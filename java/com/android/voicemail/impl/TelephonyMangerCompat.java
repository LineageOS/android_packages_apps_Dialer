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

import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import java.lang.reflect.Method;

/** Handles {@link TelephonyManager} API changes in experimental SDK */
public class TelephonyMangerCompat {

  private static final String GET_VISUAL_VOICEMAIL_PACKGE_NAME = "getVisualVoicemailPackageName";

  /**
   * Changed from getVisualVoicemailPackageName(PhoneAccountHandle) to
   * getVisualVoicemailPackageName()
   */
  public static String getVisualVoicemailPackageName(TelephonyManager telephonyManager) {
    try {
      Method method = TelephonyManager.class.getMethod(GET_VISUAL_VOICEMAIL_PACKGE_NAME);
      try {
        return (String) method.invoke(telephonyManager);
      } catch (ReflectiveOperationException e) {
        throw new RuntimeException(e);
      }
    } catch (NoSuchMethodException e) {
      // Do nothing, try the next version.
    }

    try {
      Method method =
          TelephonyManager.class.getMethod(
              GET_VISUAL_VOICEMAIL_PACKGE_NAME, PhoneAccountHandle.class);
      try {
        return (String) method.invoke(telephonyManager, (Object) null);
      } catch (ReflectiveOperationException e) {
        throw new RuntimeException(e);
      }
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }
}
