/*
 * Copyright (C) 2011 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.dialer.app.calllog;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.support.v4.os.BuildCompat;
import android.support.v4.util.Pair;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import com.android.contacts.common.compat.TelephonyManagerCompat;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.dialer.app.DialtactsActivity;
import com.android.dialer.app.R;
import com.android.dialer.app.calllog.CallLogNotificationsQueryHelper.NewCall;
import com.android.dialer.app.contactinfo.ContactPhotoLoader;
import com.android.dialer.app.list.DialtactsPagerAdapter;
import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler;
import com.android.dialer.blocking.FilteredNumbersUtil;
import com.android.dialer.calllogutils.PhoneAccountUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutors;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.notification.NotificationChannelManager;
import com.android.dialer.notification.NotificationChannelManager.Channel;
import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.telecom.TelecomUtil;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Shows a voicemail notification in the status bar. */
public class DefaultVoicemailNotifier implements Worker<Void, Void> {

  public static final String TAG = "VoicemailNotifier";

  /** The tag used to identify notifications from this class. */
  static final String VISUAL_VOICEMAIL_NOTIFICATION_TAG = "DefaultVoicemailNotifier";
  /** The identifier of the notification of new voicemails. */
  private static final int VISUAL_VOICEMAIL_NOTIFICATION_ID = R.id.notification_visual_voicemail;

  private static final int LEGACY_VOICEMAIL_NOTIFICATION_ID = R.id.notification_legacy_voicemail;
  private static final String LEGACY_VOICEMAIL_NOTIFICATION_TAG = "legacy_voicemail";

  private final Context context;
  private final CallLogNotificationsQueryHelper queryHelper;
  private final FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler;

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  DefaultVoicemailNotifier(
      Context context,
      CallLogNotificationsQueryHelper queryHelper,
      FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler) {
    this.context = context;
    this.queryHelper = queryHelper;
    this.filteredNumberAsyncQueryHandler = filteredNumberAsyncQueryHandler;
  }

  public DefaultVoicemailNotifier(Context context) {
    this(
        context,
        CallLogNotificationsQueryHelper.getInstance(context),
        new FilteredNumberAsyncQueryHandler(context));
  }

  @Nullable
  @Override
  public Void doInBackground(@Nullable Void input) throws Throwable {
    updateNotification();
    return null;
  }

  /**
   * Updates the notification and notifies of the call with the given URI.
   *
   * <p>Clears the notification if there are no new voicemails, and notifies if the given URI
   * corresponds to a new voicemail.
   *
   * <p>It is not safe to call this method from the main thread.
   */
  @VisibleForTesting
  @WorkerThread
  void updateNotification() {
    Assert.isWorkerThread();
    // Lookup the list of new voicemails to include in the notification.
    final List<NewCall> newCalls = queryHelper.getNewVoicemails();

    if (newCalls == null) {
      // Query failed, just return.
      return;
    }

    Resources resources = context.getResources();

    // This represents a list of names to include in the notification.
    String callers = null;

    // Maps each number into a name: if a number is in the map, it has already left a more
    // recent voicemail.
    final Map<String, ContactInfo> contactInfos = new ArrayMap<>();

    // Iterate over the new voicemails to determine all the information above.
    Iterator<NewCall> itr = newCalls.iterator();
    while (itr.hasNext()) {
      NewCall newCall = itr.next();

      // Skip notifying for numbers which are blocked.
      if (!FilteredNumbersUtil.hasRecentEmergencyCall(context)
          && filteredNumberAsyncQueryHandler.getBlockedIdSynchronous(
                  newCall.number, newCall.countryIso)
              != null) {
        itr.remove();

        if (newCall.voicemailUri != null) {
          // Delete the voicemail.
          CallLogAsyncTaskUtil.deleteVoicemailSynchronous(context, newCall.voicemailUri);
        }
        continue;
      }

      // Check if we already know the name associated with this number.
      ContactInfo contactInfo = contactInfos.get(newCall.number);
      if (contactInfo == null) {
        contactInfo =
            queryHelper.getContactInfo(
                newCall.number, newCall.numberPresentation, newCall.countryIso);
        contactInfos.put(newCall.number, contactInfo);
        // This is a new caller. Add it to the back of the list of callers.
        if (TextUtils.isEmpty(callers)) {
          callers = contactInfo.name;
        } else {
          callers =
              resources.getString(
                  R.string.notification_voicemail_callers_list, callers, contactInfo.name);
        }
      }
    }

    if (newCalls.isEmpty()) {
      // No voicemails to notify about
      return;
    }

    Notification.Builder groupSummary =
        createNotificationBuilder()
            .setContentTitle(
                resources.getQuantityString(
                    R.plurals.notification_voicemail_title, newCalls.size(), newCalls.size()))
            .setContentText(callers)
            .setDeleteIntent(createMarkNewVoicemailsAsOldIntent(null))
            .setGroupSummary(true)
            .setContentIntent(newVoicemailIntent(null));

    if (BuildCompat.isAtLeastO()) {
      groupSummary.setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN);
    }

    NotificationChannelManager.applyChannel(
        groupSummary,
        context,
        Channel.VOICEMAIL,
        PhoneAccountHandles.getAccount(context, newCalls.get(0)));

    LogUtil.i(TAG, "Creating visual voicemail notification");
    getNotificationManager()
        .notify(
            VISUAL_VOICEMAIL_NOTIFICATION_TAG,
            VISUAL_VOICEMAIL_NOTIFICATION_ID,
            groupSummary.build());

    for (NewCall voicemail : newCalls) {
      getNotificationManager()
          .notify(
              voicemail.callsUri.toString(),
              VISUAL_VOICEMAIL_NOTIFICATION_ID,
              createNotificationForVoicemail(voicemail, contactInfos));
    }
  }

  /**
   * Replicates how packages/services/Telephony/NotificationMgr.java handles legacy voicemail
   * notification. The notification will not be stackable because no information is available for
   * individual voicemails.
   */
  @TargetApi(VERSION_CODES.O)
  public void notifyLegacyVoicemail(
      @NonNull PhoneAccountHandle phoneAccountHandle,
      int count,
      String voicemailNumber,
      PendingIntent callVoicemailIntent,
      PendingIntent voicemailSettingIntent) {
    Assert.isNotNull(phoneAccountHandle);
    Assert.checkArgument(BuildCompat.isAtLeastO());
    TelephonyManager telephonyManager =
        context
            .getSystemService(TelephonyManager.class)
            .createForPhoneAccountHandle(phoneAccountHandle);
    Assert.isNotNull(telephonyManager);
    LogUtil.i(TAG, "Creating legacy voicemail notification");

    PersistableBundle carrierConfig = telephonyManager.getCarrierConfig();

    String notificationTitle =
        context
            .getResources()
            .getQuantityString(R.plurals.notification_voicemail_title, count, count);

    TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
    PhoneAccount phoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle);

    String notificationText;
    PendingIntent pendingIntent;

    if (voicemailSettingIntent != null) {
      // If the voicemail number if unknown, instead of calling voicemail, take the user
      // to the voicemail settings.
      notificationText = context.getString(R.string.notification_voicemail_no_vm_number);
      pendingIntent = voicemailSettingIntent;
    } else {
      if (PhoneAccountUtils.getSubscriptionPhoneAccounts(context).size() > 1) {
        notificationText = phoneAccount.getShortDescription().toString();
      } else {
        notificationText =
            String.format(
                context.getString(R.string.notification_voicemail_text_format),
                PhoneNumberUtils.formatNumber(voicemailNumber));
      }
      pendingIntent = callVoicemailIntent;
    }
    Notification.Builder builder = new Notification.Builder(context);
    builder
        .setSmallIcon(android.R.drawable.stat_notify_voicemail)
        .setColor(context.getColor(R.color.dialer_theme_color))
        .setWhen(System.currentTimeMillis())
        .setContentTitle(notificationTitle)
        .setContentText(notificationText)
        .setContentIntent(pendingIntent)
        .setSound(telephonyManager.getVoicemailRingtoneUri(phoneAccountHandle))
        .setOngoing(
            carrierConfig.getBoolean(
                CarrierConfigManager.KEY_VOICEMAIL_NOTIFICATION_PERSISTENT_BOOL));

    if (telephonyManager.isVoicemailVibrationEnabled(phoneAccountHandle)) {
      builder.setDefaults(Notification.DEFAULT_VIBRATE);
    }

    NotificationChannelManager.applyChannel(
        builder, context, Channel.VOICEMAIL, phoneAccountHandle);
    Notification notification = builder.build();
    getNotificationManager()
        .notify(LEGACY_VOICEMAIL_NOTIFICATION_TAG, LEGACY_VOICEMAIL_NOTIFICATION_ID, notification);
  }

  public void cancelLegacyNotification() {
    LogUtil.i(TAG, "Clearing legacy voicemail notification");
    getNotificationManager()
        .cancel(LEGACY_VOICEMAIL_NOTIFICATION_TAG, LEGACY_VOICEMAIL_NOTIFICATION_ID);
  }

  /**
   * Determines which ringtone Uri and Notification defaults to use when updating the notification
   * for the given call.
   */
  private Pair<Uri, Integer> getNotificationInfo(@Nullable NewCall callToNotify) {
    LogUtil.v(TAG, "getNotificationInfo");
    if (callToNotify == null) {
      LogUtil.i(TAG, "callToNotify == null");
      return new Pair<>(null, 0);
    }
    PhoneAccountHandle accountHandle = PhoneAccountHandles.getAccount(context, callToNotify);
    if (accountHandle == null) {
      LogUtil.i(TAG, "No default phone account found, using default notification ringtone");
      return new Pair<>(null, Notification.DEFAULT_ALL);
    }
    return new Pair<>(
        TelephonyManagerCompat.getVoicemailRingtoneUri(getTelephonyManager(), accountHandle),
        getNotificationDefaults(accountHandle));
  }

  private int getNotificationDefaults(PhoneAccountHandle accountHandle) {
    if (VERSION.SDK_INT >= VERSION_CODES.N) {
      return TelephonyManagerCompat.isVoicemailVibrationEnabled(
              getTelephonyManager(), accountHandle)
          ? Notification.DEFAULT_VIBRATE
          : 0;
    }
    return Notification.DEFAULT_ALL;
  }

  /** Creates a pending intent that marks all new voicemails as old. */
  private PendingIntent createMarkNewVoicemailsAsOldIntent(@Nullable Uri voicemailUri) {
    Intent intent = new Intent(context, CallLogNotificationsService.class);
    intent.setAction(CallLogNotificationsService.ACTION_MARK_NEW_VOICEMAILS_AS_OLD);
    intent.setData(voicemailUri);
    return PendingIntent.getService(context, 0, intent, 0);
  }

  private NotificationManager getNotificationManager() {
    return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
  }

  private TelephonyManager getTelephonyManager() {
    return (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
  }

  private Notification createNotificationForVoicemail(
      @NonNull NewCall voicemail, @NonNull Map<String, ContactInfo> contactInfos) {
    Pair<Uri, Integer> notificationInfo = getNotificationInfo(voicemail);
    ContactInfo contactInfo = contactInfos.get(voicemail.number);

    Notification.Builder notificationBuilder =
        createNotificationBuilder()
            .setContentTitle(
                context
                    .getResources()
                    .getQuantityString(R.plurals.notification_voicemail_title, 1, 1))
            .setContentText(
                ContactDisplayUtils.getTtsSpannedPhoneNumber(
                    context.getResources(),
                    R.string.notification_new_voicemail_ticker,
                    contactInfo.name))
            .setWhen(voicemail.dateMs)
            .setSound(notificationInfo.first)
            .setDefaults(notificationInfo.second);

    if (voicemail.voicemailUri != null) {
      notificationBuilder.setDeleteIntent(
          createMarkNewVoicemailsAsOldIntent(voicemail.voicemailUri));
    }

    NotificationChannelManager.applyChannel(
        notificationBuilder,
        context,
        Channel.VOICEMAIL,
        PhoneAccountHandles.getAccount(context, voicemail));

    ContactPhotoLoader loader = new ContactPhotoLoader(context, contactInfo);
    Bitmap photoIcon = loader.loadPhotoIcon();
    if (photoIcon != null) {
      notificationBuilder.setLargeIcon(photoIcon);
    }
    if (!TextUtils.isEmpty(voicemail.transcription)) {
      Logger.get(context)
          .logImpression(DialerImpression.Type.VVM_NOTIFICATION_CREATED_WITH_TRANSCRIPTION);
      notificationBuilder.setStyle(
          new Notification.BigTextStyle().bigText(voicemail.transcription));
    }
    notificationBuilder.setContentIntent(newVoicemailIntent(voicemail));
    Logger.get(context).logImpression(DialerImpression.Type.VVM_NOTIFICATION_CREATED);
    return notificationBuilder.build();
  }

  private Notification.Builder createNotificationBuilder() {
    return new Notification.Builder(context)
        .setSmallIcon(android.R.drawable.stat_notify_voicemail)
        .setColor(context.getColor(R.color.dialer_theme_color))
        .setGroup(VISUAL_VOICEMAIL_NOTIFICATION_TAG)
        .setOnlyAlertOnce(true)
        .setAutoCancel(true);
  }

  private PendingIntent newVoicemailIntent(@Nullable NewCall voicemail) {
    Intent intent =
        DialtactsActivity.getShowTabIntent(context, DialtactsPagerAdapter.TAB_INDEX_VOICEMAIL);
    // TODO (b/35486204): scroll to this voicemail
    if (voicemail != null) {
      intent.setData(voicemail.voicemailUri);
    }
    intent.putExtra(DialtactsActivity.EXTRA_CLEAR_NEW_VOICEMAILS, true);
    return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  /**
   * Updates the voicemail notifications displayed.
   *
   * @param runnable Called when the async update task completes no matter if it succeeds or fails.
   *     May be null.
   */
  static void updateVoicemailNotifications(Context context, Runnable runnable) {
    if (!TelecomUtil.isDefaultDialer(context)) {
      LogUtil.i(
          "DefaultVoicemailNotifier.updateVoicemailNotifications",
          "not default dialer, not scheduling update to voicemail notifications");
      return;
    }

    DialerExecutors.createNonUiTaskBuilder(new DefaultVoicemailNotifier(context))
        .onSuccess(
            output -> {
              LogUtil.i(
                  "DefaultVoicemailNotifier.updateVoicemailNotifications",
                  "update voicemail notifications successful");
              if (runnable != null) {
                runnable.run();
              }
            })
        .onFailure(
            throwable -> {
              LogUtil.i(
                  "DefaultVoicemailNotifier.updateVoicemailNotifications",
                  "update voicemail notifications failed");
              if (runnable != null) {
                runnable.run();
              }
            })
        .build()
        .executeParallel(null);
  }
}
