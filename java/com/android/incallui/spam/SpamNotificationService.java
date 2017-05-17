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

import android.app.NotificationManager;
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
import com.android.dialer.spam.Spam;
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
  private static final String EXTRA_NOTIFICATION_ID = "service_notification_id";
  private static final String EXTRA_CONTACT_LOOKUP_RESULT_TYPE =
      "service_contact_lookup_result_type";
  /** Creates an intent to start this service. */
  public static Intent createServiceIntent(
      Context context, DialerCall call, String action, int notificationId) {
    Intent intent = new Intent(context, SpamNotificationService.class);
    intent.setAction(action);
    intent.putExtra(EXTRA_PHONE_NUMBER, call.getNumber());
    intent.putExtra(EXTRA_CALL_ID, call.getUniqueCallId());
    intent.putExtra(EXTRA_CALL_START_TIME_MILLIS, call.getTimeAddedMs());
    intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
    intent.putExtra(EXTRA_CONTACT_LOOKUP_RESULT_TYPE, call.getLogState().contactLookupResult);
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
    int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 1);
    String countryIso = GeoUtil.getCurrentCountryIso(this);
    ContactLookupResult.Type contactLookupResultType =
        ContactLookupResult.Type.forNumber(intent.getIntExtra(EXTRA_CONTACT_LOOKUP_RESULT_TYPE, 0));

    ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
        .cancel(number, notificationId);

    switch (intent.getAction()) {
      case SpamNotificationActivity.ACTION_MARK_NUMBER_AS_SPAM:
        logCallImpression(
            intent, DialerImpression.Type.SPAM_NOTIFICATION_SERVICE_ACTION_MARK_NUMBER_AS_SPAM);
        Spam.get(this)
            .reportSpamFromAfterCallNotification(
                number,
                countryIso,
                CallLog.Calls.INCOMING_TYPE,
                ReportingLocation.Type.FEEDBACK_PROMPT,
                contactLookupResultType);
        new FilteredNumberAsyncQueryHandler(this).blockNumber(null, number, countryIso);
        break;
      case SpamNotificationActivity.ACTION_MARK_NUMBER_AS_NOT_SPAM:
        logCallImpression(
            intent, DialerImpression.Type.SPAM_NOTIFICATION_SERVICE_ACTION_MARK_NUMBER_AS_NOT_SPAM);
        Spam.get(this)
            .reportNotSpamFromAfterCallNotification(
                number,
                countryIso,
                CallLog.Calls.INCOMING_TYPE,
                ReportingLocation.Type.FEEDBACK_PROMPT,
                contactLookupResultType);
        break;
      default: // fall out
    }
    // TODO: call stopSelf() after async tasks complete (b/28441936)
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
}
