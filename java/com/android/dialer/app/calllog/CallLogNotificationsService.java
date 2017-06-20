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
 * limitations under the License.
 */

package com.android.dialer.app.calllog;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import com.android.dialer.common.LogUtil;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.PermissionsUtil;

/**
 * Provides operations for managing call-related notifications.
 *
 * <p>It handles the following actions:
 *
 * <ul>
 *   <li>Updating voicemail notifications
 *   <li>Marking new voicemails as old
 *   <li>Updating missed call notifications
 *   <li>Marking new missed calls as old
 *   <li>Calling back from a missed call
 *   <li>Sending an SMS from a missed call
 * </ul>
 */
public class CallLogNotificationsService extends IntentService {

  private static final String ACTION_MARK_ALL_NEW_VOICEMAILS_AS_OLD =
      "com.android.dialer.calllog.ACTION_MARK_ALL_NEW_VOICEMAILS_AS_OLD";

  private static final String ACTION_MARK_SINGLE_NEW_VOICEMAIL_AS_OLD =
      "com.android.dialer.calllog.ACTION_MARK_SINGLE_NEW_VOICEMAIL_AS_OLD ";

  @VisibleForTesting
  static final String ACTION_CANCEL_ALL_MISSED_CALLS =
      "com.android.dialer.calllog.ACTION_CANCEL_ALL_MISSED_CALLS";

  private static final String ACTION_CANCEL_SINGLE_MISSED_CALL =
      "com.android.dialer.calllog.ACTION_CANCEL_SINGLE_MISSED_CALL";

  private static final String ACTION_INCOMING_POST_CALL =
      "com.android.dialer.calllog.INCOMING_POST_CALL";

  /** Action to call back a missed call. */
  public static final String ACTION_CALL_BACK_FROM_MISSED_CALL_NOTIFICATION =
      "com.android.dialer.calllog.CALL_BACK_FROM_MISSED_CALL_NOTIFICATION";

  /**
   * Extra to be included with {@link #ACTION_INCOMING_POST_CALL} to represent a post call note.
   *
   * <p>It must be a {@link String}
   */
  private static final String EXTRA_POST_CALL_NOTE = "POST_CALL_NOTE";

  /**
   * Extra to be included with {@link #ACTION_INCOMING_POST_CALL} to represent the phone number the
   * post call note came from.
   *
   * <p>It must be a {@link String}
   */
  private static final String EXTRA_POST_CALL_NUMBER = "POST_CALL_NUMBER";

  public static final int UNKNOWN_MISSED_CALL_COUNT = -1;

  public CallLogNotificationsService() {
    super("CallLogNotificationsService");
  }

  public static void insertPostCallNote(Context context, String number, String postCallNote) {
    Intent serviceIntent = new Intent(context, CallLogNotificationsService.class);
    serviceIntent.setAction(ACTION_INCOMING_POST_CALL);
    serviceIntent.putExtra(EXTRA_POST_CALL_NUMBER, number);
    serviceIntent.putExtra(EXTRA_POST_CALL_NOTE, postCallNote);
    context.startService(serviceIntent);
  }

  public static void markAllNewVoicemailsAsOld(Context context) {
    LogUtil.enterBlock("CallLogNotificationsService.markAllNewVoicemailsAsOld");
    Intent serviceIntent = new Intent(context, CallLogNotificationsService.class);
    serviceIntent.setAction(CallLogNotificationsService.ACTION_MARK_ALL_NEW_VOICEMAILS_AS_OLD);
    context.startService(serviceIntent);
  }

  public static void markSingleNewVoicemailAsOld(Context context, @Nullable Uri voicemailUri) {
    LogUtil.enterBlock("CallLogNotificationsService.markSingleNewVoicemailAsOld");
    Intent serviceIntent = new Intent(context, CallLogNotificationsService.class);
    serviceIntent.setAction(CallLogNotificationsService.ACTION_MARK_SINGLE_NEW_VOICEMAIL_AS_OLD);
    serviceIntent.setData(voicemailUri);
    context.startService(serviceIntent);
  }

  public static PendingIntent createMarkAllNewVoicemailsAsOldIntent(@NonNull Context context) {
    Intent intent = new Intent(context, CallLogNotificationsService.class);
    intent.setAction(CallLogNotificationsService.ACTION_MARK_ALL_NEW_VOICEMAILS_AS_OLD);
    return PendingIntent.getService(context, 0, intent, 0);
  }

  public static PendingIntent createMarkSingleNewVoicemailAsOldIntent(
      @NonNull Context context, @Nullable Uri voicemailUri) {
    Intent intent = new Intent(context, CallLogNotificationsService.class);
    intent.setAction(CallLogNotificationsService.ACTION_MARK_SINGLE_NEW_VOICEMAIL_AS_OLD);
    intent.setData(voicemailUri);
    return PendingIntent.getService(context, 0, intent, 0);
  }

  public static void cancelAllMissedCalls(@NonNull Context context) {
    LogUtil.enterBlock("CallLogNotificationsService.cancelAllMissedCalls");
    Intent serviceIntent = new Intent(context, CallLogNotificationsService.class);
    serviceIntent.setAction(ACTION_CANCEL_ALL_MISSED_CALLS);
    context.startService(serviceIntent);
  }

  public static PendingIntent createCancelAllMissedCallsPendingIntent(@NonNull Context context) {
    Intent intent = new Intent(context, CallLogNotificationsService.class);
    intent.setAction(ACTION_CANCEL_ALL_MISSED_CALLS);
    return PendingIntent.getService(context, 0, intent, 0);
  }

  public static PendingIntent createCancelSingleMissedCallPendingIntent(
      @NonNull Context context, @Nullable Uri callUri) {
    Intent intent = new Intent(context, CallLogNotificationsService.class);
    intent.setAction(ACTION_CANCEL_SINGLE_MISSED_CALL);
    intent.setData(callUri);
    return PendingIntent.getService(context, 0, intent, 0);
  }

  @Override
  protected void onHandleIntent(Intent intent) {
    if (intent == null) {
      LogUtil.e("CallLogNotificationsService.onHandleIntent", "could not handle null intent");
      return;
    }

    if (!PermissionsUtil.hasPermission(this, android.Manifest.permission.READ_CALL_LOG)
        || !PermissionsUtil.hasPermission(this, android.Manifest.permission.WRITE_CALL_LOG)) {
      LogUtil.e("CallLogNotificationsService.onHandleIntent", "no READ_CALL_LOG permission");
      return;
    }

    String action = intent.getAction();
    LogUtil.i("CallLogNotificationsService.onHandleIntent", "action: " + action);
    switch (action) {
      case ACTION_MARK_ALL_NEW_VOICEMAILS_AS_OLD:
        VoicemailQueryHandler.markAllNewVoicemailsAsRead(this);
        VisualVoicemailNotifier.cancelAllVoicemailNotifications(this);
        break;
      case ACTION_MARK_SINGLE_NEW_VOICEMAIL_AS_OLD:
        Uri voicemailUri = intent.getData();
        VoicemailQueryHandler.markSingleNewVoicemailAsRead(this, voicemailUri);
        VisualVoicemailNotifier.cancelSingleVoicemailNotification(this, voicemailUri);
        break;
      case ACTION_INCOMING_POST_CALL:
        String note = intent.getStringExtra(EXTRA_POST_CALL_NOTE);
        String phoneNumber = intent.getStringExtra(EXTRA_POST_CALL_NUMBER);
        MissedCallNotifier.getIstance(this).insertPostCallNotification(phoneNumber, note);
        break;
      case ACTION_CANCEL_ALL_MISSED_CALLS:
        CallLogNotificationsQueryHelper.markAllMissedCallsInCallLogAsRead(this);
        MissedCallNotifier.cancelAllMissedCallNotifications(this);
        TelecomUtil.cancelMissedCallsNotification(this);
        break;
      case ACTION_CANCEL_SINGLE_MISSED_CALL:
        Uri callUri = intent.getData();
        CallLogNotificationsQueryHelper.markSingleMissedCallInCallLogAsRead(this, callUri);
        MissedCallNotifier.cancelSingleMissedCallNotification(this, callUri);
        TelecomUtil.cancelMissedCallsNotification(this);
        break;
      case ACTION_CALL_BACK_FROM_MISSED_CALL_NOTIFICATION:
        MissedCallNotifier.getIstance(this)
            .callBackFromMissedCall(
                intent.getStringExtra(
                    MissedCallNotificationReceiver.EXTRA_NOTIFICATION_PHONE_NUMBER),
                intent.getData());
        break;
      default:
        LogUtil.e("CallLogNotificationsService.onHandleIntent", "no handler for action: " + action);
        break;
    }
  }
}
