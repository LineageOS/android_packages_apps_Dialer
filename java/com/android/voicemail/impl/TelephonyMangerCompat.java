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
 * limitations under the License
 */

package com.android.voicemail.impl;

import android.app.PendingIntent;
import android.content.Context;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import android.telephony.VisualVoicemailService;
import android.telephony.VisualVoicemailSmsFilterSettings;
import com.android.dialer.common.LogUtil;
import java.lang.reflect.Method;

/** Handles {@link TelephonyManager} API changes in experimental SDK */
public class TelephonyMangerCompat {
  /** Moved from VisualVoicemailService to TelephonyManager */
  public static String sendVisualVoicemailSms(
      Context context,
      PhoneAccountHandle phoneAccountHandle,
      String number,
      int port,
      String text,
      PendingIntent sentIntent) {
    try {
      Method method =
          TelephonyManager.class.getMethod(
              "sendVisualVoicemailSms", String.class, int.class, String.class, PendingIntent.class);
      try {
        LogUtil.i("TelephonyMangerCompat.sendVisualVoicemailSms", "using TelephonyManager");
        TelephonyManager telephonyManager =
            context
                .getSystemService(TelephonyManager.class)
                .createForPhoneAccountHandle(phoneAccountHandle);
        return (String) method.invoke(telephonyManager, number, port, text, sentIntent);
      } catch (ReflectiveOperationException e) {
        throw new RuntimeException(e);
      }
    } catch (NoSuchMethodException e) {
      // Do nothing, try the next version.
    }

    try {
      LogUtil.i("TelephonyMangerCompat.sendVisualVoicemailSms", "using VisualVoicemailService");
      Method method =
          VisualVoicemailService.class.getMethod(
              "sendVisualVoicemailSms",
              Context.class,
              PhoneAccountHandle.class,
              String.class,
              short.class,
              String.class,
              PendingIntent.class);
      return (String)
          method.invoke(null, context, phoneAccountHandle, number, (short) port, text, sentIntent);

    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  /** Moved from VisualVoicemailService to TelephonyManager */
  public static String setVisualVoicemailSmsFilterSettings(
      Context context,
      PhoneAccountHandle phoneAccountHandle,
      VisualVoicemailSmsFilterSettings settings) {
    try {
      Method method =
          TelephonyManager.class.getMethod(
              "setVisualVoicemailSmsFilterSettings", VisualVoicemailSmsFilterSettings.class);
      try {
        LogUtil.i(
            "TelephonyMangerCompat.setVisualVoicemailSmsFilterSettings", "using TelephonyManager");
        TelephonyManager telephonyManager =
            context
                .getSystemService(TelephonyManager.class)
                .createForPhoneAccountHandle(phoneAccountHandle);
        return (String) method.invoke(telephonyManager, settings);
      } catch (ReflectiveOperationException e) {
        throw new RuntimeException(e);
      }
    } catch (NoSuchMethodException e) {
      // Do nothing, try the next version.
    }

    try {
      LogUtil.i(
          "TelephonyMangerCompat.setVisualVoicemailSmsFilterSettings",
          "using VisualVoicemailService");
      Method method =
          VisualVoicemailService.class.getMethod(
              "setSmsFilterSettings",
              Context.class,
              PhoneAccountHandle.class,
              VisualVoicemailSmsFilterSettings.class);
      return (String) method.invoke(null, context, phoneAccountHandle, settings);

    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
