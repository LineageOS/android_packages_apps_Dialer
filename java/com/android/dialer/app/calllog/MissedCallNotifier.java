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
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.provider.CallLog.Calls;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.os.UserManagerCompat;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.compat.PhoneNumberUtilsCompat;
import com.android.dialer.app.DialtactsActivity;
import com.android.dialer.app.R;
import com.android.dialer.app.calllog.CallLogNotificationsHelper.NewCall;
import com.android.dialer.app.contactinfo.ContactPhotoLoader;
import com.android.dialer.app.list.ListsFragment;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.callintent.nano.CallInitiationType;
import com.android.dialer.common.ConfigProviderBindings;
import com.android.dialer.common.LogUtil;
import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.IntentUtil;
import java.util.List;

/** Creates a notification for calls that the user missed (neither answered nor rejected). */
public class MissedCallNotifier {

  /** The tag used to identify notifications from this class. */
  private static final String NOTIFICATION_TAG = "MissedCallNotifier";
  /** The identifier of the notification of new missed calls. */
  private static final int NOTIFICATION_ID = 1;

  private static MissedCallNotifier sInstance;
  private Context mContext;
  private CallLogNotificationsHelper mCalllogNotificationsHelper;

  @VisibleForTesting
  MissedCallNotifier(Context context, CallLogNotificationsHelper callLogNotificationsHelper) {
    mContext = context;
    mCalllogNotificationsHelper = callLogNotificationsHelper;
  }

  /** Returns the singleton instance of the {@link MissedCallNotifier}. */
  public static MissedCallNotifier getInstance(Context context) {
    if (sInstance == null) {
      CallLogNotificationsHelper callLogNotificationsHelper =
          CallLogNotificationsHelper.getInstance(context);
      sInstance = new MissedCallNotifier(context, callLogNotificationsHelper);
    }
    return sInstance;
  }

  /**
   * Creates a missed call notification with a post call message if there are no existing missed
   * calls.
   */
  public void createPostCallMessageNotification(String number, String message) {
    int count = CallLogNotificationsService.UNKNOWN_MISSED_CALL_COUNT;
    if (ConfigProviderBindings.get(mContext).getBoolean("enable_call_compose", false)) {
      updateMissedCallNotification(count, number, message);
    } else {
      updateMissedCallNotification(count, number, null);
    }
  }

  /** Creates a missed call notification. */
  public void updateMissedCallNotification(int count, String number) {
    updateMissedCallNotification(count, number, null);
  }

  private void updateMissedCallNotification(
      int count, String number, @Nullable String postCallMessage) {
    final int titleResId;
    CharSequence expandedText; // The text in the notification's line 1 and 2.

    final List<NewCall> newCalls = mCalllogNotificationsHelper.getNewMissedCalls();

    if (count == CallLogNotificationsService.UNKNOWN_MISSED_CALL_COUNT) {
      if (newCalls == null) {
        // If the intent did not contain a count, and we are unable to get a count from the
        // call log, then no notification can be shown.
        return;
      }
      count = newCalls.size();
    }

    if (count == 0) {
      // No voicemails to notify about: clear the notification.
      clearMissedCalls();
      return;
    }

    // The call log has been updated, use that information preferentially.
    boolean useCallLog = newCalls != null && newCalls.size() == count;
    NewCall newestCall = useCallLog ? newCalls.get(0) : null;
    long timeMs = useCallLog ? newestCall.dateMs : System.currentTimeMillis();
    String missedNumber = useCallLog ? newestCall.number : number;

    Notification.Builder builder = new Notification.Builder(mContext);
    // Display the first line of the notification:
    // 1 missed call: <caller name || handle>
    // More than 1 missed call: <number of calls> + "missed calls"
    if (count == 1) {
      //TODO: look up caller ID that is not in contacts.
      ContactInfo contactInfo =
          mCalllogNotificationsHelper.getContactInfo(
              missedNumber,
              useCallLog ? newestCall.numberPresentation : Calls.PRESENTATION_ALLOWED,
              useCallLog ? newestCall.countryIso : null);

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

      if (!TextUtils.isEmpty(postCallMessage)) {
        // Ex. "John Doe: Hey dude"
        expandedText =
            mContext.getString(
                R.string.post_call_notification_message, expandedText, postCallMessage);
      }
      ContactPhotoLoader loader = new ContactPhotoLoader(mContext, contactInfo);
      Bitmap photoIcon = loader.loadPhotoIcon();
      if (photoIcon != null) {
        builder.setLargeIcon(photoIcon);
      }
    } else {
      titleResId = R.string.notification_missedCallsTitle;
      expandedText = mContext.getString(R.string.notification_missedCallsMsg, count);
    }

    // Create a public viewable version of the notification, suitable for display when sensitive
    // notification content is hidden.
    Notification.Builder publicBuilder = new Notification.Builder(mContext);
    publicBuilder
        .setSmallIcon(android.R.drawable.stat_notify_missed_call)
        .setColor(mContext.getResources().getColor(R.color.dialer_theme_color))
        // Show "Phone" for notification title.
        .setContentTitle(mContext.getText(R.string.userCallActivityLabel))
        // Notification details shows that there are missed call(s), but does not reveal
        // the missed caller information.
        .setContentText(mContext.getText(titleResId))
        .setContentIntent(createCallLogPendingIntent())
        .setAutoCancel(true)
        .setWhen(timeMs)
        .setShowWhen(true)
        .setDeleteIntent(createClearMissedCallsPendingIntent());

    // Create the notification suitable for display when sensitive information is showing.
    builder
        .setSmallIcon(android.R.drawable.stat_notify_missed_call)
        .setColor(mContext.getResources().getColor(R.color.dialer_theme_color))
        .setContentTitle(mContext.getText(titleResId))
        .setContentText(expandedText)
        .setContentIntent(createCallLogPendingIntent())
        .setAutoCancel(true)
        .setWhen(timeMs)
        .setShowWhen(true)
        .setDefaults(Notification.DEFAULT_VIBRATE)
        .setDeleteIntent(createClearMissedCallsPendingIntent())
        // Include a public version of the notification to be shown when the missed call
        // notification is shown on the user's lock screen and they have chosen to hide
        // sensitive notification information.
        .setPublicVersion(publicBuilder.build());

    // Add additional actions when there is only 1 missed call and the user isn't locked
    if (UserManagerCompat.isUserUnlocked(mContext) && count == 1) {
      if (!TextUtils.isEmpty(missedNumber)
          && !TextUtils.equals(missedNumber, mContext.getString(R.string.handle_restricted))) {
        builder.addAction(
            R.drawable.ic_phone_24dp,
            mContext.getString(R.string.notification_missedCall_call_back),
            createCallBackPendingIntent(missedNumber));

        if (!PhoneNumberHelper.isUriNumber(missedNumber)) {
          builder.addAction(
              R.drawable.ic_message_24dp,
              mContext.getString(R.string.notification_missedCall_message),
              createSendSmsFromNotificationPendingIntent(missedNumber));
        }
      }
    }

    Notification notification = builder.build();
    configureLedOnNotification(notification);

    LogUtil.i("MissedCallNotifier.updateMissedCallNotification", "adding missed call notification");
    getNotificationMgr().notify(NOTIFICATION_TAG, NOTIFICATION_ID, notification);
  }

  private void clearMissedCalls() {
    AsyncTask.execute(
        new Runnable() {
          @Override
          public void run() {
            // Call log is only accessible when unlocked. If that's the case, clear the list of
            // new missed calls from the call log.
            if (UserManagerCompat.isUserUnlocked(mContext)) {
              ContentValues values = new ContentValues();
              values.put(Calls.NEW, 0);
              values.put(Calls.IS_READ, 1);
              StringBuilder where = new StringBuilder();
              where.append(Calls.NEW);
              where.append(" = 1 AND ");
              where.append(Calls.TYPE);
              where.append(" = ?");
              try {
                mContext
                    .getContentResolver()
                    .update(
                        Calls.CONTENT_URI,
                        values,
                        where.toString(),
                        new String[] {Integer.toString(Calls.MISSED_TYPE)});
              } catch (IllegalArgumentException e) {
                LogUtil.e(
                    "MissedCallNotifier.clearMissedCalls",
                    "contacts provider update command failed",
                    e);
              }
            }
            getNotificationMgr().cancel(NOTIFICATION_TAG, NOTIFICATION_ID);
          }
        });
  }

  /** Trigger an intent to make a call from a missed call number. */
  public void callBackFromMissedCall(String number) {
    closeSystemDialogs(mContext);
    CallLogNotificationsHelper.removeMissedCallNotifications(mContext);
    DialerUtils.startActivityWithErrorToast(
        mContext,
        new CallIntentBuilder(number, CallInitiationType.Type.MISSED_CALL_NOTIFICATION)
            .build()
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }

  /** Trigger an intent to send an sms from a missed call number. */
  public void sendSmsFromMissedCall(String number) {
    closeSystemDialogs(mContext);
    CallLogNotificationsHelper.removeMissedCallNotifications(mContext);
    DialerUtils.startActivityWithErrorToast(
        mContext, IntentUtil.getSendSmsIntent(number).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }

  /**
   * Creates a new pending intent that sends the user to the call log.
   *
   * @return The pending intent.
   */
  private PendingIntent createCallLogPendingIntent() {
    Intent contentIntent =
        DialtactsActivity.getShowTabIntent(mContext, ListsFragment.TAB_INDEX_HISTORY);
    return PendingIntent.getActivity(mContext, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  /** Creates a pending intent that marks all new missed calls as old. */
  private PendingIntent createClearMissedCallsPendingIntent() {
    Intent intent = new Intent(mContext, CallLogNotificationsService.class);
    intent.setAction(CallLogNotificationsService.ACTION_MARK_NEW_MISSED_CALLS_AS_OLD);
    return PendingIntent.getService(mContext, 0, intent, 0);
  }

  private PendingIntent createCallBackPendingIntent(String number) {
    Intent intent = new Intent(mContext, CallLogNotificationsService.class);
    intent.setAction(CallLogNotificationsService.ACTION_CALL_BACK_FROM_MISSED_CALL_NOTIFICATION);
    intent.putExtra(CallLogNotificationsService.EXTRA_MISSED_CALL_NUMBER, number);
    // Use FLAG_UPDATE_CURRENT to make sure any previous pending intent is updated with the new
    // extra.
    return PendingIntent.getService(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  private PendingIntent createSendSmsFromNotificationPendingIntent(String number) {
    Intent intent = new Intent(mContext, CallLogNotificationsService.class);
    intent.setAction(CallLogNotificationsService.ACTION_SEND_SMS_FROM_MISSED_CALL_NOTIFICATION);
    intent.putExtra(CallLogNotificationsService.EXTRA_MISSED_CALL_NUMBER, number);
    // Use FLAG_UPDATE_CURRENT to make sure any previous pending intent is updated with the new
    // extra.
    return PendingIntent.getService(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
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

  private NotificationManager getNotificationMgr() {
    return (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
  }
}
