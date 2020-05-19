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
package com.android.contacts.common.compat.telecom;

import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import java.lang.reflect.Field;

/** Compatibility class for {@link android.telecom.TelecomManager}. */
public class TelecomManagerCompat {

  // Constants from http://cs/android/frameworks/base/telecomm/java/android/telecom/Call.java.
  public static final String EVENT_REQUEST_HANDOVER = "android.telecom.event.REQUEST_HANDOVER";
  public static final String EXTRA_HANDOVER_PHONE_ACCOUNT_HANDLE =
      "android.telecom.extra.HANDOVER_PHONE_ACCOUNT_HANDLE";
  public static final String EXTRA_HANDOVER_VIDEO_STATE =
      "android.telecom.extra.HANDOVER_VIDEO_STATE";

  // This is a hidden constant in android.telecom.DisconnectCause. Telecom sets this as a disconnect
  // reason if it wants us to prompt the user that the video call is not available.
  // TODO(wangqi): Reference it to constant in android.telecom.DisconnectCause.
  public static final String REASON_IMS_ACCESS_BLOCKED = "REASON_IMS_ACCESS_BLOCKED";

  /**
   * Returns the current SIM call manager. Apps must be prepared for this method to return null,
   * indicating that there currently exists no registered SIM call manager.
   *
   * @param telecomManager the {@link TelecomManager} to use to fetch the SIM call manager.
   * @return The phone account handle of the current sim call manager.
   */
  @Nullable
  public static PhoneAccountHandle getSimCallManager(TelecomManager telecomManager) {
    if (telecomManager != null) {
      return telecomManager.getSimCallManager();
    }
    return null;
  }

  /** Returns true if the Android version supports Handover. */
  public static boolean supportsHandover() {
    // Starting with Android P, handover is supported via a public API.
    if (VERSION.SDK_INT >= VERSION_CODES.P) {
      return true;
    }
    // Handovers are supported from Android O-DR onward. Since there is no API
    // bump from O to O-DR, we need to use reflection to check the existence
    // of TelecomManager.EXTRA_IS_HANDOVER in
    // http://cs/android/frameworks/base/telecomm/java/android/telecom/TelecomManager.java.
    try {
      Field field = TelecomManager.class.getDeclaredField("EXTRA_IS_HANDOVER");
      return "android.telecom.extra.IS_HANDOVER".equals(field.get(null /* obj (static field) */));
    } catch (Exception e) {
      // Do nothing
    }
    return false;
  }
}
