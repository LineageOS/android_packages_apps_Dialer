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

package com.android.dialer.assisteddialing;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.telephony.TelephonyManager;
import com.android.dialer.common.LogUtil;

/**
 * A Creator for AssistedDialingMediators.
 *
 * <p>This helps keep the dependencies required by AssistedDialingMediator for assisted dialing
 * explicit.
 */
@TargetApi(VERSION_CODES.N)
public final class ConcreteCreator {

  /**
   * Creates a new AssistedDialingMediator
   *
   * @param telephonyManager The telephony manager used to determine user location.
   * @param context The context used to determine whether or not a provided number is an emergency
   *     number.
   * @return An AssistedDialingMediator
   */
  public static AssistedDialingMediator createNewAssistedDialingMediator(
      @NonNull TelephonyManager telephonyManager, @NonNull Context context) {
    if (telephonyManager == null) {
      LogUtil.i(
          "ConcreteCreator.createNewAssistedDialingMediator", "provided TelephonyManager was null");
      throw new NullPointerException("Provided TelephonyManager was null");
    }
    if (context == null) {
      LogUtil.i("ConcreteCreator.createNewAssistedDialingMediator", "provided context was null");
      throw new NullPointerException("Provided context was null");
    }
    Constraints constraints = new Constraints(context);
    return new AssistedDialingMediator(
        new LocationDetector(telephonyManager), new NumberTransformer(constraints));
  }
}
