/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.dialer.oem;

import android.content.Context;
import android.content.res.Resources;
import android.provider.CallLog.Calls;
import android.support.annotation.VisibleForTesting;
import android.telephony.TelephonyManager;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.PackageUtils;
import com.android.dialer.configprovider.ConfigProviderComponent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Util class for Motorola OEM devices. */
public class MotorolaUtils {

  private static final String CONFIG_HD_CODEC_BLINKING_ICON_WHEN_CONNECTING_CALL_ENABLED =
      "hd_codec_blinking_icon_when_connecting_enabled";
  private static final String CONFIG_HD_CODEC_SHOW_ICON_IN_NOTIFICATION_ENABLED =
      "hd_codec_show_icon_in_notification_enabled";
  private static final String CONFIG_WIFI_CALL_SHOW_ICON_IN_CALL_LOG_ENABLED =
      "wifi_call_show_icon_in_call_log_enabled";

  @VisibleForTesting
  static final String CONFIG_DISABLE_PHONE_NUMBER_FORMATTING = "disable_phone_number_formatting";

  // This is used to check if a Motorola device supports HD voice call feature, which comes from
  // system feature setting.
  private static final String HD_CALL_FEATRURE = "com.motorola.software.sprint.hd_call";
  // This is used to check if a Motorola device supports WiFi call feature, by checking if a certain
  // package is enabled.
  @VisibleForTesting public static final String WIFI_CALL_PACKAGE_NAME = "com.motorola.sprintwfc";
  // Thi is used to check if a Motorola device supports hidden menu feature.
  @VisibleForTesting
  static final String HIDDEN_MENU_FEATURE = "com.motorola.software.sprint.hidden_menu";

  private static Boolean disablePhoneNumberFormattingForTest = null;
  private static boolean hasCheckedSprintWifiCall;
  private static boolean supportSprintWifiCall;

  /**
   * Returns true if SPN is specified and matched the current sim operator name. This is necessary
   * since mcc310-mnc000 is not sufficient to identify Sprint network.
   */
  private static boolean isSpnMatched(Context context) {
    try {
      for (String spnResource :
          context.getResources().getStringArray(R.array.motorola_enabled_spn)) {
        if (spnResource.equalsIgnoreCase(
            context.getSystemService(TelephonyManager.class).getSimOperatorName())) {
          return true;
        }
      }
      return false;
    } catch (Resources.NotFoundException exception) {
      // If SPN is not specified we consider as not necessary to enable/disable the feature.
      return true;
    }
  }

  static boolean isSupportingHiddenMenu(Context context) {
    return context.getPackageManager().hasSystemFeature(HIDDEN_MENU_FEATURE)
        && context.getResources().getBoolean(R.bool.motorola_hidden_menu_enabled);
  }

  public static boolean shouldBlinkHdIconWhenConnectingCall(Context context) {
    return ConfigProviderComponent.get(context)
            .getConfigProvider()
            .getBoolean(CONFIG_HD_CODEC_BLINKING_ICON_WHEN_CONNECTING_CALL_ENABLED, true)
        && isSupportingSprintHdCodec(context);
  }

  public static boolean shouldShowHdIconInNotification(Context context) {
    return ConfigProviderComponent.get(context)
            .getConfigProvider()
            .getBoolean(CONFIG_HD_CODEC_SHOW_ICON_IN_NOTIFICATION_ENABLED, true)
        && isSupportingSprintHdCodec(context);
  }

  public static boolean shouldShowWifiIconInCallLog(Context context, int features) {
    return ConfigProviderComponent.get(context)
            .getConfigProvider()
            .getBoolean(CONFIG_WIFI_CALL_SHOW_ICON_IN_CALL_LOG_ENABLED, true)
        && (features & Calls.FEATURES_WIFI) == Calls.FEATURES_WIFI
        && isSupportingSprintWifiCall(context);
  }

  public static boolean shouldDisablePhoneNumberFormatting(Context context) {
    if (disablePhoneNumberFormattingForTest != null) {
      return disablePhoneNumberFormattingForTest;
    }

    return ConfigProviderComponent.get(context)
            .getConfigProvider()
            .getBoolean(CONFIG_DISABLE_PHONE_NUMBER_FORMATTING, true)
        && context.getResources().getBoolean(R.bool.motorola_disable_phone_number_formatting);
  }

  /**
   * Handle special char sequence entered in dialpad. This may launch special intent based on input.
   *
   * @param context context
   * @param input input string
   * @return true if the input is consumed and the intent is launched
   */
  public static boolean handleSpecialCharSequence(Context context, String input) {
    // TODO(a bug): Add check for Motorola devices.
    return MotorolaHiddenMenuKeySequence.handleCharSequence(context, input);
  }

  public static boolean isWifiCallingAvailable(Context context) {
    if (!isSupportingSprintWifiCall(context)) {
      return false;
    }
    TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
    try {
      Method method = TelephonyManager.class.getMethod("isWifiCallingAvailable");
      boolean isWifiCallingAvailable = (boolean) method.invoke(telephonyManager);
      LogUtil.d("MotorolaUtils.isWifiCallingAvailable", "%b", isWifiCallingAvailable);
      return isWifiCallingAvailable;
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      LogUtil.e("MotorolaUtils.isWifiCallingAvailable", "", e);
    }
    return false;
  }

  private static boolean isSupportingSprintHdCodec(Context context) {
    return isSpnMatched(context)
        && context.getResources().getBoolean(R.bool.motorola_sprint_hd_codec)
        && context.getPackageManager().hasSystemFeature(HD_CALL_FEATRURE);
  }

  private static boolean isSupportingSprintWifiCall(Context context) {
    if (!hasCheckedSprintWifiCall) {
      supportSprintWifiCall = PackageUtils.isPackageEnabled(WIFI_CALL_PACKAGE_NAME, context);
      hasCheckedSprintWifiCall = true;
    }
    return supportSprintWifiCall;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  public static void setDisablePhoneNumberFormattingForTest(boolean disablePhoneNumberFormatting) {
    disablePhoneNumberFormattingForTest = disablePhoneNumberFormatting;
  }

  @VisibleForTesting
  public static void resetForTest() {
    hasCheckedSprintWifiCall = false;
    supportSprintWifiCall = false;
  }
}
