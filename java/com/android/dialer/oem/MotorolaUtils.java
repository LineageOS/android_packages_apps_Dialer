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
import com.android.dialer.common.ConfigProviderBindings;

/** Util class for Motorola OEM devices. */
public class MotorolaUtils {

  private static final String CONFIG_HD_CODEC_BLINKING_ICON_WHEN_CONNECTING_CALL_ENABLED =
      "hd_codec_blinking_icon_when_connecting_enabled";
  private static final String CONFIG_HD_CODEC_SHOW_ICON_IN_CALL_LOG_ENABLED =
      "hd_codec_show_icon_in_call_log_enabled";

  // This is used to check if a Motorola device supports HD voice call feature, which comes from
  // system feature setting.
  private static final String HD_CALL_FEATRURE = "com.motorola.software.sprint.hd_call";

  // Feature flag indicates it's a HD call, currently this is only used by Motorola system build.
  // TODO(b/35359461): Upstream and move it to android.provider.CallLog.
  private static final int FEATURES_HD_CALL = 0x10000000;

  public static boolean shouldBlinkHdIconWhenConnectingCall(Context context) {
    return ConfigProviderBindings.get(context)
            .getBoolean(CONFIG_HD_CODEC_BLINKING_ICON_WHEN_CONNECTING_CALL_ENABLED, true)
        && isSupportingSprintHdCodec(context);
  }

  public static boolean shouldShowHdIconInCallLog(Context context, int features) {
    return ConfigProviderBindings.get(context)
            .getBoolean(CONFIG_HD_CODEC_SHOW_ICON_IN_CALL_LOG_ENABLED, true)
        && isSupportingSprintHdCodec(context)
        && (features & FEATURES_HD_CALL) == FEATURES_HD_CALL;
  }

  /**
   * Handle special char sequence entered in dialpad. This may launch special intent based on input.
   *
   * @param context context
   * @param input input string
   * @return true if the input is consumed and the intent is launched
   */
  public static boolean handleSpecialCharSequence(Context context, String input) {
    // TODO(b/35395377): Add check for Motorola devices.
    return MotorolaHiddenMenuKeySequence.handleCharSequence(context, input);
  }

  private static boolean isSupportingSprintHdCodec(Context context) {
    return context.getPackageManager().hasSystemFeature(HD_CALL_FEATRURE)
        && context.getResources().getBoolean(R.bool.motorola_sprint_hd_codec);
  }
}
