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

package com.android.dialer.compat.telephony;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.Nullable;
import android.support.v4.os.BuildCompat;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import com.android.dialer.common.LogUtil;
import com.android.dialer.telecom.TelecomUtil;
import java.lang.reflect.InvocationTargetException;

/** Hidden APIs in {@link android.telephony.TelephonyManager}. */
public class TelephonyManagerCompat {

  // TODO(maxwelb): Use public API for these constants when available
  public static final String EVENT_HANDOVER_VIDEO_FROM_WIFI_TO_LTE =
      "android.telephony.event.EVENT_HANDOVER_VIDEO_FROM_WIFI_TO_LTE";
  public static final String EVENT_HANDOVER_VIDEO_FROM_LTE_TO_WIFI =
      "android.telephony.event.EVENT_HANDOVER_VIDEO_FROM_LTE_TO_WIFI";
  public static final String EVENT_HANDOVER_TO_WIFI_FAILED =
      "android.telephony.event.EVENT_HANDOVER_TO_WIFI_FAILED";
  public static final String EVENT_CALL_REMOTELY_HELD = "android.telecom.event.CALL_REMOTELY_HELD";
  public static final String EVENT_CALL_REMOTELY_UNHELD =
      "android.telecom.event.CALL_REMOTELY_UNHELD";
  public static final String EVENT_MERGE_START = "android.telecom.event.MERGE_START";
  public static final String EVENT_MERGE_COMPLETE = "android.telecom.event.MERGE_COMPLETE";

  public static final String EVENT_NOTIFY_INTERNATIONAL_CALL_ON_WFC =
      "android.telephony.event.EVENT_NOTIFY_INTERNATIONAL_CALL_ON_WFC";
  public static final String EVENT_CALL_FORWARDED = "android.telephony.event.EVENT_CALL_FORWARDED";

  public static final String TELEPHONY_MANAGER_CLASS = "android.telephony.TelephonyManager";

  private static final String SECRET_CODE_ACTION = "android.provider.Telephony.SECRET_CODE";

  // TODO(erfanian): a bug Replace with the platform/telecom constant when available.
  /**
   * Indicates that the call being placed originated from a known contact.
   *
   * <p>This signals to the telephony platform that an outgoing call qualifies for assisted dialing.
   */
  public static final String USE_ASSISTED_DIALING = "android.telecom.extra.USE_ASSISTED_DIALING";

  // TODO(erfanian): a bug Replace with the platform/telecom API when available.
  /** Additional information relating to the assisted dialing transformation. */
  public static final String ASSISTED_DIALING_EXTRAS =
      "android.telecom.extra.ASSISTED_DIALING_EXTRAS";

  /** Indicates the Connection/Call used assisted dialing. */
  public static final int PROPERTY_ASSISTED_DIALING_USED = 1 << 9;

  public static final String EXTRA_IS_REFRESH =
      BuildCompat.isAtLeastOMR1() ? "android.telephony.extra.IS_REFRESH" : "is_refresh";

  /**
   * Indicates the call underwent Assisted Dialing; typically set as a feature available from the
   * CallLog.
   */
  public static final Integer FEATURES_ASSISTED_DIALING = 1 << 4;

  /**
   * Flag specifying whether to show an alert dialog for video call charges. By default this value
   * is {@code false}. TODO(a bug): Replace with public API for these constants when available.
   */
  public static final String CARRIER_CONFIG_KEY_SHOW_VIDEO_CALL_CHARGES_ALERT_DIALOG_BOOL =
      "show_video_call_charges_alert_dialog_bool";

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
    return telephonyManager.getPhoneCount();
  }

  /**
   * Whether the phone supports TTY mode.
   *
   * @param telephonyManager The telephony manager instance to use for method calls.
   * @return {@code true} if the device supports TTY mode, and {@code false} otherwise.
   */
  public static boolean isTtyModeSupported(@Nullable TelephonyManager telephonyManager) {
    return telephonyManager != null && telephonyManager.isTtyModeSupported();
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
    return telephonyManager != null && telephonyManager.isHearingAidCompatibilitySupported();
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
    return telephonyManager.isVoicemailVibrationEnabled(accountHandle);
  }

  /**
   * This method uses a new system API to enable or disable visual voicemail. TODO(twyen): restrict
   * to N MR1, not needed in future SDK.
   */
  public static void setVisualVoicemailEnabled(
      TelephonyManager telephonyManager, PhoneAccountHandle handle, boolean enabled) {
    try {
      TelephonyManager.class
          .getMethod("setVisualVoicemailEnabled", PhoneAccountHandle.class, boolean.class)
          .invoke(telephonyManager, handle, enabled);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      LogUtil.e("TelephonyManagerCompat.setVisualVoicemailEnabled", "failed", e);
    }
  }

  /**
   * This method uses a new system API to check if visual voicemail is enabled TODO(twyen): restrict
   * to N MR1, not needed in future SDK.
   */
  public static boolean isVisualVoicemailEnabled(
      TelephonyManager telephonyManager, PhoneAccountHandle handle) {
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
      if (!TelecomUtil.isDefaultDialer(context)) {
        LogUtil.e(
            "TelephonyManagerCompat.handleSecretCode",
            "not default dialer, cannot send special code");
        return;
      }
      context.getSystemService(TelephonyManager.class).sendDialerSpecialCode(secretCode);
    } else {
      // System service call is not supported pre-O, so must use a broadcast for N-.
      Intent intent =
          new Intent(SECRET_CODE_ACTION, Uri.parse("android_secret_code://" + secretCode));
      context.sendBroadcast(intent);
    }
  }

  /**
   * Returns network country iso for given {@code PhoneAccountHandle} for O+ devices and country iso
   * for default sim for pre-O devices.
   */
  public static String getNetworkCountryIsoForPhoneAccountHandle(
      Context context, @Nullable PhoneAccountHandle phoneAccountHandle) {
    return getTelephonyManagerForPhoneAccountHandle(context, phoneAccountHandle)
        .getNetworkCountryIso();
  }

  /**
   * Returns TelephonyManager for given {@code PhoneAccountHandle} for O+ devices and default {@code
   * TelephonyManager} for pre-O devices.
   */
  public static TelephonyManager getTelephonyManagerForPhoneAccountHandle(
      Context context, @Nullable PhoneAccountHandle phoneAccountHandle) {
    TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
    if (phoneAccountHandle == null) {
      return telephonyManager;
    }
    if (VERSION.SDK_INT >= VERSION_CODES.O) {
      TelephonyManager telephonyManagerForPhoneAccount =
          telephonyManager.createForPhoneAccountHandle(phoneAccountHandle);
      if (telephonyManagerForPhoneAccount != null) {
        return telephonyManagerForPhoneAccount;
      }
    }
    return telephonyManager;
  }
}
