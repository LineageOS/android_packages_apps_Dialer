/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.dialer.util;

import android.content.Context;
import android.provider.Settings;

import com.android.dialer.common.LogUtil;
import com.android.dialer.storage.StorageComponent;

import java.util.concurrent.TimeUnit;

/** Utility to help with tasks related to emergency calls. */
public class EmergencyCallUtil {

  // Pref key for storing the time of end of the last emergency call in milliseconds after epoch.\
  private static final String LAST_EMERGENCY_CALL_MS_PREF_KEY = "last_emergency_call_ms";
  // Pref key for storing whether a notification has been dispatched to notify the user that call
  // blocking has been disabled because of a recent emergency call.
  protected static final String NOTIFIED_CALL_BLOCKING_DISABLED_BY_EMERGENCY_CALL_PREF_KEY =
      "notified_call_blocking_disabled_by_emergency_call";
  // Disable incoming call blocking if there was a call within the past 2 days.
  static final long RECENT_EMERGENCY_CALL_THRESHOLD_MS = TimeUnit.DAYS.toMillis(2);

  /**
   * Used for testing to specify the custom threshold value, in milliseconds for whether an
   * emergency call is "recent". The default value will be used if this custom threshold is less
   * than zero. For example, to set this threshold to 60 seconds:
   *
   * <p>adb shell settings put system dialer_emergency_call_threshold_ms 60000
   */
  private static final String RECENT_EMERGENCY_CALL_THRESHOLD_SETTINGS_KEY =
      "dialer_emergency_call_threshold_ms";

  public static long getLastEmergencyCallTimeMillis(Context context) {
    return StorageComponent.get(context)
        .unencryptedSharedPrefs()
        .getLong(LAST_EMERGENCY_CALL_MS_PREF_KEY, 0);
  }

  public static boolean hasRecentEmergencyCall(Context context) {
    if (context == null) {
      return false;
    }

    long lastEmergencyCallTime = getLastEmergencyCallTimeMillis(context);
    if (lastEmergencyCallTime == 0) {
      return false;
    }

    return (System.currentTimeMillis() - lastEmergencyCallTime)
        < getRecentEmergencyCallThresholdMs(context);
  }

  public static void recordLastEmergencyCallTime(Context context) {
    if (context == null) {
      return;
    }

    StorageComponent.get(context)
        .unencryptedSharedPrefs()
        .edit()
        .putLong(LAST_EMERGENCY_CALL_MS_PREF_KEY, System.currentTimeMillis())
        .putBoolean(NOTIFIED_CALL_BLOCKING_DISABLED_BY_EMERGENCY_CALL_PREF_KEY, false)
        .apply();
  }

  private static long getRecentEmergencyCallThresholdMs(Context context) {
    if (LogUtil.isVerboseEnabled()) {
      long thresholdMs =
          Settings.System.getLong(
              context.getContentResolver(), RECENT_EMERGENCY_CALL_THRESHOLD_SETTINGS_KEY, 0);
      return thresholdMs > 0 ? thresholdMs : RECENT_EMERGENCY_CALL_THRESHOLD_MS;
    } else {
      return RECENT_EMERGENCY_CALL_THRESHOLD_MS;
    }
  }
}
