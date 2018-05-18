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
 * limitations under the License
 */

package com.android.incallui.spam;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.provider.CallLog;
import android.support.annotation.Nullable;
import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler;
import com.android.dialer.common.LogUtil;
import com.android.dialer.location.GeoUtil;
import com.android.dialer.logging.ContactLookupResult;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ReportingLocation;
import com.android.dialer.notification.DialerNotificationManager;
import com.android.dialer.spam.SpamComponent;
import com.android.dialer.spam.SpamSettings;
import com.android.dialer.spam.promo.SpamBlockingPromoHelper;
import com.android.incallui.call.DialerCall;

/**
 * This service determines if the device is locked/unlocked and takes an action based on the state.
 * A service is used to to determine this, as opposed to an activity, because the user must unlock
 * the device before a notification can start an activity. This is not the case for a service, and
 * intents can be sent to this service even from the lock screen. This allows users to quickly
 * report a number as spam or not spam from their lock screen.
 */
public class SpamNotificationService extends Service {

  private static final String TAG = "SpamNotificationSvc";

  private static final String EXTRA_PHONE_NUMBER = "service_phone_number";
  private static final String EXTRA_CALL_ID = "service_call_id";
  private static final String EXTRA_CALL_START_TIME_MILLIS = "service_call_start_time_millis";
  private static final String EXTRA_NOTIFICATION_TAG = "service_notification_tag";
  private static final String EXTRA_NOTIFICATION_ID = "service_notification_id";
  private static final String EXTRA_CONTACT_LOOKUP_RESULT_TYPE =
      "service_contact_lookup_result_type";

  private String notificationTag;
  private int notificationId;

  /** Creates an intent to start this service. */
  public static Intent createServiceIntent(
      Context context,
      @Nullable DialerCall call,
      String action,
      String notificationTag,
      int notificationId) {
    Intent intent = new Intent(context, SpamNotificationService.class);
    intent.setAction(action);
    intent.putExtra(EXTRA_NOTIFICATION_TAG, notificationTag);
    intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);

    if (call != null) {
      intent.putExtra(EXTRA_PHONE_NUMBER, call.getNumber());
      intent.putExtra(EXTRA_CALL_ID, call.getUniqueCallId());
      intent.putExtra(EXTRA_CALL_START_TIME_MILLIS, call.getTimeAddedMs());
      intent.putExtra(
          EXTRA_CONTACT_LOOKUP_RESULT_TYPE, call.getLogState().contactLookupResult.getNumber());
    }
    return intent;
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    // Return null because clients cannot bind to this service
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    LogUtil.d(TAG, "onStartCommand");
    if (intent == null) {
      LogUtil.d(TAG, "Null intent");
      stopSelf();
      // Return {@link #START_NOT_STICKY} so service is not restarted.
      return START_NOT_STICKY;
    }
    String number = intent.getStringExtra(EXTRA_PHONE_NUMBER);
    notificationTag = intent.getStringExtra(EXTRA_NOTIFICATION_TAG);
    notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 1);
    String countryIso = GeoUtil.getCurrentCountryIso(this);
    ContactLookupResult.Type contactLookupResultType =
        ContactLookupResult.Type.forNumber(intent.getIntExtra(EXTRA_CONTACT_LOOKUP_RESULT_TYPE, 0));

    SpamSettings spamSettings = SpamComponent.get(this).spamSettings();
    SpamBlockingPromoHelper spamBlockingPromoHelper =
        new SpamBlockingPromoHelper(this, SpamComponent.get(this).spamSettings());
    boolean shouldShowSpamBlockingPromo =
        SpamNotificationActivity.ACTION_MARK_NUMBER_AS_SPAM.equals(intent.getAction())
            && spamBlockingPromoHelper.shouldShowAfterCallSpamBlockingPromo();

    // Cancel notification only if we are not showing spam blocking promo. Otherwise we will show
    // spam blocking promo notification in place.
    if (!shouldShowSpamBlockingPromo) {
      DialerNotificationManager.cancel(this, notificationTag, notificationId);
    }

    switch (intent.getAction()) {
      case SpamNotificationActivity.ACTION_MARK_NUMBER_AS_SPAM:
        logCallImpression(
            intent, DialerImpression.Type.SPAM_NOTIFICATION_SERVICE_ACTION_MARK_NUMBER_AS_SPAM);
        SpamComponent.get(this)
            .spam()
            .reportSpamFromAfterCallNotification(
                number,
                countryIso,
                CallLog.Calls.INCOMING_TYPE,
                ReportingLocation.Type.FEEDBACK_PROMPT,
                contactLookupResultType);
        new FilteredNumberAsyncQueryHandler(this).blockNumber(null, number, countryIso);
        if (shouldShowSpamBlockingPromo) {
          spamBlockingPromoHelper.showSpamBlockingPromoNotification(
              notificationTag,
              notificationId,
              createPromoActivityPendingIntent(),
              createEnableSpamBlockingPendingIntent());
        }
        break;
      case SpamNotificationActivity.ACTION_MARK_NUMBER_AS_NOT_SPAM:
        logCallImpression(
            intent, DialerImpression.Type.SPAM_NOTIFICATION_SERVICE_ACTION_MARK_NUMBER_AS_NOT_SPAM);
        SpamComponent.get(this)
            .spam()
            .reportNotSpamFromAfterCallNotification(
                number,
                countryIso,
                CallLog.Calls.INCOMING_TYPE,
                ReportingLocation.Type.FEEDBACK_PROMPT,
                contactLookupResultType);
        break;
      case SpamNotificationActivity.ACTION_ENABLE_SPAM_BLOCKING:
        Logger.get(this)
            .logImpression(
                DialerImpression.Type.SPAM_BLOCKING_ENABLED_THROUGH_AFTER_CALL_NOTIFICATION_PROMO);
        spamSettings.modifySpamBlockingSetting(
            true,
            success -> {
              if (!success) {
                Logger.get(this)
                    .logImpression(
                        DialerImpression.Type
                            .SPAM_BLOCKING_MODIFY_FAILURE_THROUGH_AFTER_CALL_NOTIFICATION_PROMO);
              }
              spamBlockingPromoHelper.showModifySettingOnCompleteToast(success);
            });
        break;
      default: // fall out
    }
    // TODO: call stopSelf() after async tasks complete (a bug)
    stopSelf();
    return START_NOT_STICKY;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    LogUtil.d(TAG, "onDestroy");
  }

  private void logCallImpression(Intent intent, DialerImpression.Type impression) {
    Logger.get(this)
        .logCallImpression(
            impression,
            intent.getStringExtra(EXTRA_CALL_ID),
            intent.getLongExtra(EXTRA_CALL_START_TIME_MILLIS, 0));
  }

  private PendingIntent createPromoActivityPendingIntent() {
    Intent intent =
        SpamNotificationActivity.createActivityIntent(
            this,
            null,
            SpamNotificationActivity.ACTION_SHOW_SPAM_BLOCKING_PROMO_DIALOG,
            notificationTag,
            notificationId);
    return PendingIntent.getActivity(
        this, (int) System.currentTimeMillis(), intent, PendingIntent.FLAG_ONE_SHOT);
  }

  private PendingIntent createEnableSpamBlockingPendingIntent() {
    Intent intent =
        SpamNotificationService.createServiceIntent(
            this,
            null,
            SpamNotificationActivity.ACTION_ENABLE_SPAM_BLOCKING,
            notificationTag,
            notificationId);
    return PendingIntent.getService(
        this, (int) System.currentTimeMillis(), intent, PendingIntent.FLAG_ONE_SHOT);
  }
}
