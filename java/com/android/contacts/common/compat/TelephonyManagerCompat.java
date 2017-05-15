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

package com.android.contacts.common.compat;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.Nullable;
import android.support.v4.os.BuildCompat;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.CompatUtils;
import java.lang.reflect.InvocationTargetException;

public class TelephonyManagerCompat {

  // TODO: Use public API for these constants when available
  public static final String EVENT_HANDOVER_VIDEO_FROM_WIFI_TO_LTE =
      "android.telephony.event.EVENT_HANDOVER_VIDEO_FROM_WIFI_TO_LTE";
  public static final String EVENT_HANDOVER_TO_WIFI_FAILED =
      "android.telephony.event.EVENT_HANDOVER_TO_WIFI_FAILED";
  public static final String EVENT_CALL_REMOTELY_HELD = "android.telecom.event.CALL_REMOTELY_HELD";
  public static final String EVENT_CALL_REMOTELY_UNHELD =
      "android.telecom.event.CALL_REMOTELY_UNHELD";

  public static final String EVENT_NOTIFY_INTERNATIONAL_CALL_ON_WFC =
      "android.telephony.event.EVENT_NOTIFY_INTERNATIONAL_CALL_ON_WFC";

  public static final String TELEPHONY_MANAGER_CLASS = "android.telephony.TelephonyManager";

  private static final String SECRET_CODE_ACTION = "android.provider.Telephony.SECRET_CODE";

  /**
   * @param telephonyManager The telephony manager instance to use for method calls.
   * @return true if the current device is "voice capable".
   *     <p>"Voice capable" means that this device supports circuit-switched (i.e. voice) phone
   *     calls over the telephony network, and is allowed to display the in-call UI while a cellular
   *     voice call is active. This will be false on "data only" devices which can't make voice
   *     calls and don't support any in-call UI.
   *     <p>Note: the meaning of this flag is subtly different from the
   *     PackageManager.FEATURE_TELEPHONY system feature, which is available on any device with a
   *     telephony radio, even if the device is data-only.
   */
  public static boolean isVoiceCapable(@Nullable TelephonyManager telephonyManager) {
    if (telephonyManager == null) {
      return false;
    }
    if (CompatUtils.isLollipopMr1Compatible()
        || CompatUtils.isMethodAvailable(TELEPHONY_MANAGER_CLASS, "isVoiceCapable")) {
      // isVoiceCapable was unhidden in L-MR1
      return telephonyManager.isVoiceCapable();
    }
    final int phoneType = telephonyManager.getPhoneType();
    return phoneType == TelephonyManager.PHONE_TYPE_CDMA
        || phoneType == TelephonyManager.PHONE_TYPE_GSM;
  }

  /**
   * Returns the number of phones available. Returns 1 for Single standby mode (Single SIM
   * functionality) Returns 2 for Dual standby mode.(Dual SIM functionality)
   *
   * <p>Returns 1 if the method or telephonyManager is not available.
   *
   * @param telephonyManager The telephony manager instance to use for method calls.
   */
  public static int getPhoneCount(@Nullable TelephonyManager telephonyManager) {
    if (telephonyManager == null) {
      return 1;
    }
    if (CompatUtils.isMarshmallowCompatible()
        || CompatUtils.isMethodAvailable(TELEPHONY_MANAGER_CLASS, "getPhoneCount")) {
      return telephonyManager.getPhoneCount();
    }
    return 1;
  }

  /**
   * Returns the unique device ID of a subscription, for example, the IMEI for GSM and the MEID for
   * CDMA phones. Return null if device ID is not available.
   *
   * <p>Requires Permission: {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
   *
   * @param telephonyManager The telephony manager instance to use for method calls.
   * @param slotId of which deviceID is returned
   */
  public static String getDeviceId(@Nullable TelephonyManager telephonyManager, int slotId) {
    if (telephonyManager == null) {
      return null;
    }
    if (CompatUtils.isMarshmallowCompatible()
        || CompatUtils.isMethodAvailable(TELEPHONY_MANAGER_CLASS, "getDeviceId", Integer.class)) {
      return telephonyManager.getDeviceId(slotId);
    }
    return null;
  }

  /**
   * Whether the phone supports TTY mode.
   *
   * @param telephonyManager The telephony manager instance to use for method calls.
   * @return {@code true} if the device supports TTY mode, and {@code false} otherwise.
   */
  public static boolean isTtyModeSupported(@Nullable TelephonyManager telephonyManager) {
    if (telephonyManager == null) {
      return false;
    }
    if (CompatUtils.isMarshmallowCompatible()
        || CompatUtils.isMethodAvailable(TELEPHONY_MANAGER_CLASS, "isTtyModeSupported")) {
      return telephonyManager.isTtyModeSupported();
    }
    return false;
  }

  /**
   * Whether the phone supports hearing aid compatibility.
   *
   * @param telephonyManager The telephony manager instance to use for method calls.
   * @return {@code true} if the device supports hearing aid compatibility, and {@code false}
   *     otherwise.
   */
  public static boolean isHearingAidCompatibilitySupported(
      @Nullable TelephonyManager telephonyManager) {
    if (telephonyManager == null) {
      return false;
    }
    if (CompatUtils.isMarshmallowCompatible()
        || CompatUtils.isMethodAvailable(
            TELEPHONY_MANAGER_CLASS, "isHearingAidCompatibilitySupported")) {
      return telephonyManager.isHearingAidCompatibilitySupported();
    }
    return false;
  }

  /**
   * Returns the URI for the per-account voicemail ringtone set in Phone settings.
   *
   * @param telephonyManager The telephony manager instance to use for method calls.
   * @param accountHandle The handle for the {@link android.telecom.PhoneAccount} for which to
   *     retrieve the voicemail ringtone.
   * @return The URI for the ringtone to play when receiving a voicemail from a specific
   *     PhoneAccount.
   */
  @Nullable
  public static Uri getVoicemailRingtoneUri(
      TelephonyManager telephonyManager, PhoneAccountHandle accountHandle) {
    if (VERSION.SDK_INT < VERSION_CODES.N) {
      return null;
    }
    return telephonyManager.getVoicemailRingtoneUri(accountHandle);
  }

  /**
   * Returns whether vibration is set for voicemail notification in Phone settings.
   *
   * @param telephonyManager The telephony manager instance to use for method calls.
   * @param accountHandle The handle for the {@link android.telecom.PhoneAccount} for which to
   *     retrieve the voicemail vibration setting.
   * @return {@code true} if the vibration is set for this PhoneAccount, {@code false} otherwise.
   */
  public static boolean isVoicemailVibrationEnabled(
      TelephonyManager telephonyManager, PhoneAccountHandle accountHandle) {
    return VERSION.SDK_INT < VERSION_CODES.N
        || telephonyManager.isVoicemailVibrationEnabled(accountHandle);
  }

  /**
   * This method uses a new system API to enable or disable visual voicemail. TODO: restrict
   * to N MR1, not needed in future SDK.
   */
  public static void setVisualVoicemailEnabled(
      TelephonyManager telephonyManager, PhoneAccountHandle handle, boolean enabled) {
    if (VERSION.SDK_INT < VERSION_CODES.N_MR1) {
      Assert.fail("setVisualVoicemailEnabled called on pre-NMR1");
    }
    try {
      TelephonyManager.class
          .getMethod("setVisualVoicemailEnabled", PhoneAccountHandle.class, boolean.class)
          .invoke(telephonyManager, handle, enabled);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      LogUtil.e("TelephonyManagerCompat.setVisualVoicemailEnabled", "failed", e);
    }
  }

  /**
   * This method uses a new system API to check if visual voicemail is enabled TODO: restrict
   * to N MR1, not needed in future SDK.
   */
  public static boolean isVisualVoicemailEnabled(
      TelephonyManager telephonyManager, PhoneAccountHandle handle) {
    if (VERSION.SDK_INT < VERSION_CODES.N_MR1) {
      Assert.fail("isVisualVoicemailEnabled called on pre-NMR1");
    }
    try {
      return (boolean)
          TelephonyManager.class
              .getMethod("isVisualVoicemailEnabled", PhoneAccountHandle.class)
              .invoke(telephonyManager, handle);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      LogUtil.e("TelephonyManagerCompat.setVisualVoicemailEnabled", "failed", e);
    }
    return false;
  }

  /**
   * Handles secret codes to launch arbitrary activities.
   *
   * @param context the context to use
   * @param secretCode the secret code without the "*#*#" prefix and "#*#*" suffix
   */
  public static void handleSecretCode(Context context, String secretCode) {
    // Must use system service on O+ to avoid using broadcasts, which are not allowed on O+.
    if (BuildCompat.isAtLeastO()) {
      context.getSystemService(TelephonyManager.class).sendDialerSpecialCode(secretCode);
    } else {
      // System service call is not supported pre-O, so must use a broadcast for N-.
      Intent intent =
          new Intent(SECRET_CODE_ACTION, Uri.parse("android_secret_code://" + secretCode));
      context.sendBroadcast(intent);
    }
  }
}
