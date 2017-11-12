/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.dialer.app.calllog;

import android.app.Notification;
import android.app.Notification.Builder;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.service.notification.StatusBarNotification;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.support.v4.os.BuildCompat;
import android.support.v4.os.UserManagerCompat;
import android.support.v4.util.Pair;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.util.ArraySet;
import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.compat.PhoneNumberUtilsCompat;
import com.android.dialer.app.DialtactsActivity;
import com.android.dialer.app.R;
import com.android.dialer.app.calllog.CallLogNotificationsQueryHelper.NewCall;
import com.android.dialer.app.contactinfo.ContactPhotoLoader;
import com.android.dialer.app.list.DialtactsPagerAdapter;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.compat.android.provider.VoicemailCompat;
import com.android.dialer.duo.DuoConstants;
import com.android.dialer.enrichedcall.FuzzyPhoneNumberMatcher;
import com.android.dialer.notification.DialerNotificationManager;
import com.android.dialer.notification.NotificationChannelId;
import com.android.dialer.notification.NotificationManagerUtils;
import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.precall.PreCall;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.IntentUtil;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Creates a notification for calls that the user missed (neither answered nor rejected). */
public class MissedCallNotifier implements Worker<Pair<Integer, String>, Void> {

  /** Prefix used to generate a unique tag for each missed call notification. */
  private static final String NOTIFICATION_TAG_PREFIX = "MissedCall_";
  /** Common ID for all missed call notifications. */
  private static final int NOTIFICATION_ID = 1;
  /** Tag for the group summary notification. */
  private static final String GROUP_SUMMARY_NOTIFICATION_TAG = "GroupSummary_MissedCall";
  /**
   * Key used to associate all missed call notifications and the summary as belonging to a single
   * group.
   */
  private static final String GROUP_KEY = "MissedCallGroup";

  private final Context context;
  private final CallLogNotificationsQueryHelper callLogNotificationsQueryHelper;

  @VisibleForTesting
  MissedCallNotifier(
      Context context, CallLogNotificationsQueryHelper callLogNotificationsQueryHelper) {
    this.context = context;
    this.callLogNotificationsQueryHelper = callLogNotificationsQueryHelper;
  }

  public static MissedCallNotifier getInstance(Context context) {
    return new MissedCallNotifier(context, CallLogNotificationsQueryHelper.getInstance(context));
  }

  @Nullable
  @Override
  public Void doInBackground(@Nullable Pair<Integer, String> input) throws Throwable {
    updateMissedCallNotification(input.first, input.second);
    return null;
  }

  /**
   * Update missed call notifications from the call log. Accepts default information in case call
   * log cannot be accessed.
   *
   * @param count the number of missed calls to display if call log cannot be accessed. May be
   *     {@link CallLogNotificationsService#UNKNOWN_MISSED_CALL_COUNT} if unknown.
   * @param number the phone number of the most recent call to display if the call log cannot be
   *     accessed. May be null if unknown.
   */
  @VisibleForTesting
  @WorkerThread
  void updateMissedCallNotification(int count, @Nullable String number) {
    final int titleResId;
    CharSequence expandedText; // The text in the notification's line 1 and 2.

    List<NewCall> newCalls = callLogNotificationsQueryHelper.getNewMissedCalls();

    removeSelfManagedCalls(newCalls);

    if ((newCalls != null && newCalls.isEmpty()) || count == 0) {
      // No calls to notify about: clear the notification.
      CallLogNotificationsQueryHelper.markAllMissedCallsInCallLogAsRead(context);
      cancelAllMissedCallNotifications(context);
      return;
    }

    if (newCalls != null) {
      if (count != CallLogNotificationsService.UNKNOWN_MISSED_CALL_COUNT
          && count != newCalls.size()) {
        LogUtil.w(
            "MissedCallNotifier.updateMissedCallNotification",
            "Call count does not match call log count."
                + " count: "
                + count
                + " newCalls.size(): "
                + newCalls.size());
      }
      count = newCalls.size();
    }

    if (count == CallLogNotificationsService.UNKNOWN_MISSED_CALL_COUNT) {
      // If the intent did not contain a count, and we are unable to get a count from the
      // call log, then no notification can be shown.
      return;
    }

    Notification.Builder groupSummary = createNotificationBuilder();
    boolean useCallList = newCalls != null;

    if (count == 1) {
      NewCall call =
          useCallList
              ? newCalls.get(0)
              : new NewCall(
                  null,
                  null,
                  number,
                  Calls.PRESENTATION_ALLOWED,
                  null,
                  null,
                  null,
                  null,
                  System.currentTimeMillis(),
                  VoicemailCompat.TRANSCRIPTION_NOT_STARTED);

      // TODO: look up caller ID that is not in contacts.
      ContactInfo contactInfo =
          callLogNotificationsQueryHelper.getContactInfo(
              call.number, call.numberPresentation, call.countryIso);
      titleResId =
          contactInfo.userType == ContactsUtils.USER_TYPE_WORK
              ? R.string.notification_missedWorkCallTitle
              : R.string.notification_missedCallTitle;

      if (TextUtils.equals(contactInfo.name, contactInfo.formattedNumber)
          || TextUtils.equals(contactInfo.name, contactInfo.number)) {
        expandedText =
            PhoneNumberUtilsCompat.createTtsSpannable(
                BidiFormatter.getInstance()
                    .unicodeWrap(contactInfo.name, TextDirectionHeuristics.LTR));
      } else {
        expandedText = contactInfo.name;
      }

      ContactPhotoLoader loader = new ContactPhotoLoader(context, contactInfo);
      Bitmap photoIcon = loader.loadPhotoIcon();
      if (photoIcon != null) {
        groupSummary.setLargeIcon(photoIcon);
      }
    } else {
      titleResId = R.string.notification_missedCallsTitle;
      expandedText = context.getString(R.string.notification_missedCallsMsg, count);
    }

    // Create a public viewable version of the notification, suitable for display when sensitive
    // notification content is hidden.
    Notification.Builder publicSummaryBuilder = createNotificationBuilder();
    publicSummaryBuilder
        .setContentTitle(context.getText(titleResId))
        .setContentIntent(createCallLogPendingIntent())
        .setDeleteIntent(
            CallLogNotificationsService.createCancelAllMissedCallsPendingIntent(context));

    // Create the notification summary suitable for display when sensitive information is showing.
    groupSummary
        .setContentTitle(context.getText(titleResId))
        .setContentText(expandedText)
        .setContentIntent(createCallLogPendingIntent())
        .setDeleteIntent(
            CallLogNotificationsService.createCancelAllMissedCallsPendingIntent(context))
        .setGroupSummary(useCallList)
        .setOnlyAlertOnce(useCallList)
        .setPublicVersion(publicSummaryBuilder.build());
    if (BuildCompat.isAtLeastO()) {
      groupSummary.setChannelId(NotificationChannelId.MISSED_CALL);
    }

    Notification notification = groupSummary.build();
    configureLedOnNotification(notification);

    LogUtil.i("MissedCallNotifier.updateMissedCallNotification", "adding missed call notification");
    DialerNotificationManager.notify(
        context, GROUP_SUMMARY_NOTIFICATION_TAG, NOTIFICATION_ID, notification);

    if (useCallList) {
      // Do not repost active notifications to prevent erasing post call notes.
      Set<String> activeTags = new ArraySet<>();
      for (StatusBarNotification activeNotification :
          DialerNotificationManager.getActiveNotifications(context)) {
        activeTags.add(activeNotification.getTag());
      }

      for (NewCall call : newCalls) {
        String callTag = getNotificationTagForCall(call);
        if (!activeTags.contains(callTag)) {
          DialerNotificationManager.notify(
              context, callTag, NOTIFICATION_ID, getNotificationForCall(call, null));
        }
      }
    }
  }

  /**
   * Remove self-managed calls from {@code newCalls}. If a {@link PhoneAccount} declared it is
   * {@link PhoneAccount#CAPABILITY_SELF_MANAGED}, it should handle the in call UI and notifications
   * itself, but might still write to call log with {@link
   * PhoneAccount#EXTRA_LOG_SELF_MANAGED_CALLS}.
   */
  private void removeSelfManagedCalls(@Nullable List<NewCall> newCalls) {
    if (newCalls == null) {
      return;
    }

    TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
    Iterator<NewCall> iterator = newCalls.iterator();
    while (iterator.hasNext()) {
      NewCall call = iterator.next();
      if (call.accountComponentName == null || call.accountId == null) {
        continue;
      }
      ComponentName componentName = ComponentName.unflattenFromString(call.accountComponentName);
      if (componentName == null) {
        continue;
      }
      PhoneAccountHandle phoneAccountHandle = new PhoneAccountHandle(componentName, call.accountId);
      PhoneAccount phoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle);
      if (phoneAccount == null) {
        continue;
      }
      if (DuoConstants.PHONE_ACCOUNT_HANDLE.equals(phoneAccountHandle)) {
        iterator.remove();
        continue;
      }
      if (phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)) {
        LogUtil.i(
            "MissedCallNotifier.removeSelfManagedCalls",
            "ignoring self-managed call " + call.callsUri);
        iterator.remove();
      }
    }
  }

  public static void cancelAllMissedCallNotifications(@NonNull Context context) {
    NotificationManagerUtils.cancelAllInGroup(context, GROUP_KEY);
  }

  public static void cancelSingleMissedCallNotification(
      @NonNull Context context, @Nullable Uri callUri) {
    if (callUri == null) {
      LogUtil.e(
          "MissedCallNotifier.cancelSingleMissedCallNotification",
          "unable to cancel notification, uri is null");
      return;
    }
    // This will also dismiss the group summary if there are no more missed call notifications.
    DialerNotificationManager.cancel(
        context, getNotificationTagForCallUri(callUri), NOTIFICATION_ID);
  }

  private static String getNotificationTagForCall(@NonNull NewCall call) {
    return getNotificationTagForCallUri(call.callsUri);
  }

  private static String getNotificationTagForCallUri(@NonNull Uri callUri) {
    return NOTIFICATION_TAG_PREFIX + callUri;
  }

  @WorkerThread
  public void insertPostCallNotification(@NonNull String number, @NonNull String note) {
    Assert.isWorkerThread();
    LogUtil.enterBlock("MissedCallNotifier.insertPostCallNotification");
    List<NewCall> newCalls = callLogNotificationsQueryHelper.getNewMissedCalls();
    if (newCalls != null && !newCalls.isEmpty()) {
      for (NewCall call : newCalls) {
        if (FuzzyPhoneNumberMatcher.matches(call.number, number.replace("tel:", ""))) {
          LogUtil.i("MissedCallNotifier.insertPostCallNotification", "Notification updated");
          // Update the first notification that matches our post call note sender.
          DialerNotificationManager.notify(
              context,
              getNotificationTagForCall(call),
              NOTIFICATION_ID,
              getNotificationForCall(call, note));
          return;
        }
      }
    }
    LogUtil.i("MissedCallNotifier.insertPostCallNotification", "notification not found");
  }

  private Notification getNotificationForCall(
      @NonNull NewCall call, @Nullable String postCallMessage) {
    ContactInfo contactInfo =
        callLogNotificationsQueryHelper.getContactInfo(
            call.number, call.numberPresentation, call.countryIso);

    // Create a public viewable version of the notification, suitable for display when sensitive
    // notification content is hidden.
    int titleResId =
        contactInfo.userType == ContactsUtils.USER_TYPE_WORK
            ? R.string.notification_missedWorkCallTitle
            : R.string.notification_missedCallTitle;
    Notification.Builder publicBuilder =
        createNotificationBuilder(call).setContentTitle(context.getText(titleResId));

    Notification.Builder builder = createNotificationBuilder(call);
    CharSequence expandedText;
    if (TextUtils.equals(contactInfo.name, contactInfo.formattedNumber)
        || TextUtils.equals(contactInfo.name, contactInfo.number)) {
      expandedText =
          PhoneNumberUtilsCompat.createTtsSpannable(
              BidiFormatter.getInstance()
                  .unicodeWrap(contactInfo.name, TextDirectionHeuristics.LTR));
    } else {
      expandedText = contactInfo.name;
    }

    if (postCallMessage != null) {
      expandedText =
          context.getString(R.string.post_call_notification_message, expandedText, postCallMessage);
    }

    ContactPhotoLoader loader = new ContactPhotoLoader(context, contactInfo);
    Bitmap photoIcon = loader.loadPhotoIcon();
    if (photoIcon != null) {
      builder.setLargeIcon(photoIcon);
    }
    // Create the notification suitable for display when sensitive information is showing.
    builder
        .setContentTitle(context.getText(titleResId))
        .setContentText(expandedText)
        // Include a public version of the notification to be shown when the missed call
        // notification is shown on the user's lock screen and they have chosen to hide
        // sensitive notification information.
        .setPublicVersion(publicBuilder.build());

    // Add additional actions when the user isn't locked
    if (UserManagerCompat.isUserUnlocked(context)) {
      if (!TextUtils.isEmpty(call.number)
          && !TextUtils.equals(call.number, context.getString(R.string.handle_restricted))) {
        builder.addAction(
            new Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_phone_24dp),
                    context.getString(R.string.notification_missedCall_call_back),
                    createCallBackPendingIntent(call.number, call.callsUri))
                .build());

        if (!PhoneNumberHelper.isUriNumber(call.number)) {
          builder.addAction(
              new Notification.Action.Builder(
                      Icon.createWithResource(context, R.drawable.quantum_ic_message_white_24),
                      context.getString(R.string.notification_missedCall_message),
                      createSendSmsFromNotificationPendingIntent(call.number, call.callsUri))
                  .build());
        }
      }
    }

    Notification notification = builder.build();
    configureLedOnNotification(notification);
    return notification;
  }

  private Notification.Builder createNotificationBuilder() {
    return new Notification.Builder(context)
        .setGroup(GROUP_KEY)
        .setSmallIcon(android.R.drawable.stat_notify_missed_call)
        .setColor(context.getResources().getColor(R.color.dialer_theme_color, null))
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .setShowWhen(true)
        .setDefaults(Notification.DEFAULT_VIBRATE);
  }

  private Notification.Builder createNotificationBuilder(@NonNull NewCall call) {
    Builder builder =
        createNotificationBuilder()
            .setWhen(call.dateMs)
            .setDeleteIntent(
                CallLogNotificationsService.createCancelSingleMissedCallPendingIntent(
                    context, call.callsUri))
            .setContentIntent(createCallLogPendingIntent(call.callsUri));
    if (BuildCompat.isAtLeastO()) {
      builder.setChannelId(NotificationChannelId.MISSED_CALL);
    }

    return builder;
  }

  /** Trigger an intent to make a call from a missed call number. */
  @WorkerThread
  public void callBackFromMissedCall(String number, Uri callUri) {
    closeSystemDialogs(context);
    CallLogNotificationsQueryHelper.markSingleMissedCallInCallLogAsRead(context, callUri);
    cancelSingleMissedCallNotification(context, callUri);
    DialerUtils.startActivityWithErrorToast(
        context,
        PreCall.getIntent(
                context,
                new CallIntentBuilder(number, CallInitiationType.Type.MISSED_CALL_NOTIFICATION))
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }

  /** Trigger an intent to send an sms from a missed call number. */
  public void sendSmsFromMissedCall(String number, Uri callUri) {
    closeSystemDialogs(context);
    CallLogNotificationsQueryHelper.markSingleMissedCallInCallLogAsRead(context, callUri);
    cancelSingleMissedCallNotification(context, callUri);
    DialerUtils.startActivityWithErrorToast(
        context, IntentUtil.getSendSmsIntent(number).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }

  /**
   * Creates a new pending intent that sends the user to the call log.
   *
   * @return The pending intent.
   */
  private PendingIntent createCallLogPendingIntent() {
    return createCallLogPendingIntent(null);
  }

  /**
   * Creates a new pending intent that sends the user to the call log.
   *
   * @return The pending intent.
   * @param callUri Uri of the call to jump to. May be null
   */
  private PendingIntent createCallLogPendingIntent(@Nullable Uri callUri) {
    Intent contentIntent =
        DialtactsActivity.getShowTabIntent(context, DialtactsPagerAdapter.TAB_INDEX_HISTORY);
    // TODO (a bug): scroll to call
    contentIntent.setData(callUri);
    return PendingIntent.getActivity(context, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  private PendingIntent createCallBackPendingIntent(String number, @NonNull Uri callUri) {
    Intent intent = new Intent(context, CallLogNotificationsService.class);
    intent.setAction(CallLogNotificationsService.ACTION_CALL_BACK_FROM_MISSED_CALL_NOTIFICATION);
    intent.putExtra(MissedCallNotificationReceiver.EXTRA_NOTIFICATION_PHONE_NUMBER, number);
    intent.setData(callUri);
    // Use FLAG_UPDATE_CURRENT to make sure any previous pending intent is updated with the new
    // extra.
    return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  private PendingIntent createSendSmsFromNotificationPendingIntent(
      String number, @NonNull Uri callUri) {
    Intent intent = new Intent(context, CallLogNotificationsActivity.class);
    intent.setAction(CallLogNotificationsActivity.ACTION_SEND_SMS_FROM_MISSED_CALL_NOTIFICATION);
    intent.putExtra(CallLogNotificationsActivity.EXTRA_MISSED_CALL_NUMBER, number);
    intent.setData(callUri);
    // Use FLAG_UPDATE_CURRENT to make sure any previous pending intent is updated with the new
    // extra.
    return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  /** Configures a notification to emit the blinky notification light. */
  private void configureLedOnNotification(Notification notification) {
    notification.flags |= Notification.FLAG_SHOW_LIGHTS;
    notification.defaults |= Notification.DEFAULT_LIGHTS;
  }

  /** Closes open system dialogs and the notification shade. */
  private void closeSystemDialogs(Context context) {
    context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
  }
}
