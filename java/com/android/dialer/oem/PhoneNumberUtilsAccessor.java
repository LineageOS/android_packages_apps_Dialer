/*
 * Copyright (C) 2018 The Android Open Source Project
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
import android.telephony.PhoneNumberUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Provides access to hidden APIs in {@link android.telephony.PhoneNumberUtils}. */
public final class PhoneNumberUtilsAccessor {

  /**
   * Checks if a given number is an emergency number for the country that the user is in.
   *
   * @param subId the subscription ID of the SIM
   * @param number the number to check
   * @param context the specific context which the number should be checked against
   * @return true if the specified number is an emergency number for the country the user is
   *     currently in.
   */
  public static boolean isLocalEmergencyNumber(Context context, int subId, String number) {
    try {
      Method method =
          PhoneNumberUtils.class.getMethod(
              "isLocalEmergencyNumber", Context.class, int.class, String.class);
      return (boolean) method.invoke(null, context, subId, number);

    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }
}
