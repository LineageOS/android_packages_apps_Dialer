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

package com.android.incallui.latencyreport;

import android.os.Bundle;
import android.os.SystemClock;

/** Tracks latency information for a call. */
public class LatencyReport {

  public static final long INVALID_TIME = -1;
  // The following are hidden constants from android.telecom.TelecomManager.
  private static final String EXTRA_CALL_CREATED_TIME_MILLIS =
      "android.telecom.extra.CALL_CREATED_TIME_MILLIS";
  private static final String EXTRA_CALL_TELECOM_ROUTING_START_TIME_MILLIS =
      "android.telecom.extra.CALL_TELECOM_ROUTING_START_TIME_MILLIS";
  private static final String EXTRA_CALL_TELECOM_ROUTING_END_TIME_MILLIS =
      "android.telecom.extra.CALL_TELECOM_ROUTING_END_TIME_MILLIS";
  private final boolean mWasIncoming;

  // Time elapsed since boot when the call was created by the connection service.
  private final long mCreatedTimeMillis;

  // Time elapsed since boot when telecom began processing the call.
  private final long mTelecomRoutingStartTimeMillis;

  // Time elapsed since boot when telecom finished processing the call. This includes things like
  // looking up contact info and call blocking but before showing any UI.
  private final long mTelecomRoutingEndTimeMillis;

  // Time elapsed since boot when the call was added to the InCallUi.
  private final long mCallAddedTimeMillis;

  // Time elapsed since boot when the call was added and call blocking evaluation was completed.
  private long mCallBlockingTimeMillis = INVALID_TIME;

  // Time elapsed since boot when the call notification was shown.
  private long mCallNotificationTimeMillis = INVALID_TIME;

  // Time elapsed since boot when the InCallUI was shown.
  private long mInCallUiShownTimeMillis = INVALID_TIME;

  // Whether the call was shown to the user as a heads up notification instead of a full screen
  // UI.
  private boolean mDidDisplayHeadsUpNotification;

  public LatencyReport() {
    mWasIncoming = false;
    mCreatedTimeMillis = INVALID_TIME;
    mTelecomRoutingStartTimeMillis = INVALID_TIME;
    mTelecomRoutingEndTimeMillis = INVALID_TIME;
    mCallAddedTimeMillis = SystemClock.elapsedRealtime();
  }

  public LatencyReport(android.telecom.Call telecomCall) {
    mWasIncoming = telecomCall.getState() == android.telecom.Call.STATE_RINGING;
    Bundle extras = telecomCall.getDetails().getIntentExtras();
    if (extras == null) {
      mCreatedTimeMillis = INVALID_TIME;
      mTelecomRoutingStartTimeMillis = INVALID_TIME;
      mTelecomRoutingEndTimeMillis = INVALID_TIME;
    } else {
      mCreatedTimeMillis = extras.getLong(EXTRA_CALL_CREATED_TIME_MILLIS, INVALID_TIME);
      mTelecomRoutingStartTimeMillis =
          extras.getLong(EXTRA_CALL_TELECOM_ROUTING_START_TIME_MILLIS, INVALID_TIME);
      mTelecomRoutingEndTimeMillis =
          extras.getLong(EXTRA_CALL_TELECOM_ROUTING_END_TIME_MILLIS, INVALID_TIME);
    }
    mCallAddedTimeMillis = SystemClock.elapsedRealtime();
  }

  public boolean getWasIncoming() {
    return mWasIncoming;
  }

  public long getCreatedTimeMillis() {
    return mCreatedTimeMillis;
  }

  public long getTelecomRoutingStartTimeMillis() {
    return mTelecomRoutingStartTimeMillis;
  }

  public long getTelecomRoutingEndTimeMillis() {
    return mTelecomRoutingEndTimeMillis;
  }

  public long getCallAddedTimeMillis() {
    return mCallAddedTimeMillis;
  }

  public long getCallBlockingTimeMillis() {
    return mCallBlockingTimeMillis;
  }

  public void onCallBlockingDone() {
    if (mCallBlockingTimeMillis == INVALID_TIME) {
      mCallBlockingTimeMillis = SystemClock.elapsedRealtime();
    }
  }

  public long getCallNotificationTimeMillis() {
    return mCallNotificationTimeMillis;
  }

  public void onNotificationShown() {
    if (mCallNotificationTimeMillis == INVALID_TIME) {
      mCallNotificationTimeMillis = SystemClock.elapsedRealtime();
    }
  }

  public long getInCallUiShownTimeMillis() {
    return mInCallUiShownTimeMillis;
  }

  public void onInCallUiShown(boolean forFullScreenIntent) {
    if (mInCallUiShownTimeMillis == INVALID_TIME) {
      mInCallUiShownTimeMillis = SystemClock.elapsedRealtime();
      mDidDisplayHeadsUpNotification = mWasIncoming && !forFullScreenIntent;
    }
  }

  public boolean getDidDisplayHeadsUpNotification() {
    return mDidDisplayHeadsUpNotification;
  }
}
