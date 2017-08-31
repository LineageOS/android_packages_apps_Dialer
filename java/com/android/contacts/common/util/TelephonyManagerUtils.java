/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.contacts.common.util;

import android.content.Context;
import android.telephony.TelephonyManager;

/** This class provides several TelephonyManager util functions. */
public class TelephonyManagerUtils {

  /**
   * Gets the voicemail tag from Telephony Manager.
   *
   * @param context Current application context
   * @return Voicemail tag, the alphabetic identifier associated with the voice mail number.
   */
  public static String getVoiceMailAlphaTag(Context context) {
    final TelephonyManager telephonyManager =
        (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    final String voiceMailLabel = telephonyManager.getVoiceMailAlphaTag();
    return voiceMailLabel;
  }
}
