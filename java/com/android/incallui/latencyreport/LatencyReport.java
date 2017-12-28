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
  private final boolean wasIncoming;

  // Time elapsed since boot when the call was created by the connection service.
  private final long createdTimeMillis;

  // Time elapsed since boot when telecom began processing the call.
  private final long telecomRoutingStartTimeMillis;

  // Time elapsed since boot when telecom finished processing the call. This includes things like
  // looking up contact info and call blocking but before showing any UI.
  private final long telecomRoutingEndTimeMillis;

  // Time elapsed since boot when the call was added to the InCallUi.
  private final long callAddedTimeMillis;

  // Time elapsed since boot when the call was added and call blocking evaluation was completed.
  private long callBlockingTimeMillis = INVALID_TIME;

  // Time elapsed since boot when the call notification was shown.
  private long callNotificationTimeMillis = INVALID_TIME;

  // Time elapsed since boot when the InCallUI was shown.
  private long inCallUiShownTimeMillis = INVALID_TIME;

  // Whether the call was shown to the user as a heads up notification instead of a full screen
  // UI.
  private boolean didDisplayHeadsUpNotification;

  public LatencyReport() {
    wasIncoming = false;
    createdTimeMillis = INVALID_TIME;
    telecomRoutingStartTimeMillis = INVALID_TIME;
    telecomRoutingEndTimeMillis = INVALID_TIME;
    callAddedTimeMillis = SystemClock.elapsedRealtime();
  }

  public LatencyReport(android.telecom.Call telecomCall) {
    wasIncoming = telecomCall.getState() == android.telecom.Call.STATE_RINGING;
    Bundle extras = telecomCall.getDetails().getIntentExtras();
    if (extras == null) {
      createdTimeMillis = INVALID_TIME;
      telecomRoutingStartTimeMillis = INVALID_TIME;
      telecomRoutingEndTimeMillis = INVALID_TIME;
    } else {
      createdTimeMillis = extras.getLong(EXTRA_CALL_CREATED_TIME_MILLIS, INVALID_TIME);
      telecomRoutingStartTimeMillis =
          extras.getLong(EXTRA_CALL_TELECOM_ROUTING_START_TIME_MILLIS, INVALID_TIME);
      telecomRoutingEndTimeMillis =
          extras.getLong(EXTRA_CALL_TELECOM_ROUTING_END_TIME_MILLIS, INVALID_TIME);
    }
    callAddedTimeMillis = SystemClock.elapsedRealtime();
  }

  public boolean getWasIncoming() {
    return wasIncoming;
  }

  public long getCreatedTimeMillis() {
    return createdTimeMillis;
  }

  public long getTelecomRoutingStartTimeMillis() {
    return telecomRoutingStartTimeMillis;
  }

  public long getTelecomRoutingEndTimeMillis() {
    return telecomRoutingEndTimeMillis;
  }

  public long getCallAddedTimeMillis() {
    return callAddedTimeMillis;
  }

  public long getCallBlockingTimeMillis() {
    return callBlockingTimeMillis;
  }

  public void onCallBlockingDone() {
    if (callBlockingTimeMillis == INVALID_TIME) {
      callBlockingTimeMillis = SystemClock.elapsedRealtime();
    }
  }

  public long getCallNotificationTimeMillis() {
    return callNotificationTimeMillis;
  }

  public void onNotificationShown() {
    if (callNotificationTimeMillis == INVALID_TIME) {
      callNotificationTimeMillis = SystemClock.elapsedRealtime();
    }
  }

  public long getInCallUiShownTimeMillis() {
    return inCallUiShownTimeMillis;
  }

  public void onInCallUiShown(boolean forFullScreenIntent) {
    if (inCallUiShownTimeMillis == INVALID_TIME) {
      inCallUiShownTimeMillis = SystemClock.elapsedRealtime();
      didDisplayHeadsUpNotification = wasIncoming && !forFullScreenIntent;
    }
  }

  public boolean getDidDisplayHeadsUpNotification() {
    return didDisplayHeadsUpNotification;
  }
}
