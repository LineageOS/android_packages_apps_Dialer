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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.provider.CallLog;

import androidx.annotation.Nullable;

import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler;
import com.android.dialer.common.LogUtil;
import com.android.dialer.location.GeoUtil;
import com.android.dialer.logging.ContactLookupResult;
import com.android.dialer.notification.DialerNotificationManager;
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

    // Cancel notification only if we are not showing spam blocking promo. Otherwise we will show
    // spam blocking promo notification in place.
    DialerNotificationManager.cancel(this, notificationTag, notificationId);

    switch (intent.getAction()) {
      case SpamNotificationActivity.ACTION_MARK_NUMBER_AS_SPAM:
        new FilteredNumberAsyncQueryHandler(this).blockNumber(null, number);
        break;
      case SpamNotificationActivity.ACTION_MARK_NUMBER_AS_NOT_SPAM:
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
}
