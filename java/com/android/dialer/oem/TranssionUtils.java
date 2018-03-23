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
 * limitations under the License
 */

package com.android.dialer.oem;

import android.content.Context;
import android.os.Build;
import android.support.annotation.VisibleForTesting;
import com.android.dialer.common.Assert;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.google.common.collect.ImmutableSet;

/** Utilities for Transsion devices. */
public final class TranssionUtils {

  @VisibleForTesting
  public static final ImmutableSet<String> TRANSSION_DEVICE_MANUFACTURERS =
      ImmutableSet.of("INFINIX MOBILITY LIMITED", "itel", "TECNO");

  @VisibleForTesting
  public static final ImmutableSet<String> TRANSSION_SECRET_CODES =
      ImmutableSet.of("*#07#", "*#87#", "*#43#", "*#2727#", "*#88#");

  private TranssionUtils() {}

  /**
   * Returns true if
   *
   * <ul>
   *   <li>the device is a Transsion device, AND
   *   <li>the input is a secret code for Transsion devices.
   * </ul>
   */
  public static boolean isTranssionSecretCode(String input) {
    return TRANSSION_DEVICE_MANUFACTURERS.contains(Build.MANUFACTURER)
        && TRANSSION_SECRET_CODES.contains(input);
  }

  /**
   * Handle a Transsion secret code by passing it to {@link
   * TelephonyManagerCompat#handleSecretCode(Context, String)}.
   *
   * <p>Before calling this method, we must use {@link #isTranssionSecretCode(String)} to ensure the
   * device is a Transsion device and the input is a valid Transsion secret code.
   *
   * <p>An exception will be thrown if either of the conditions above is not met.
   */
  public static void handleTranssionSecretCode(Context context, String input) {
    Assert.checkState(isTranssionSecretCode(input));

    TelephonyManagerCompat.handleSecretCode(context, getDigitsFromSecretCode(input));
  }

  private static String getDigitsFromSecretCode(String input) {
    // We assume a valid secret code is of format "*#{[0-9]+}#".
    Assert.checkArgument(input.length() > 3 && input.startsWith("*#") && input.endsWith("#"));

    return input.substring(2, input.length() - 1);
  }
}
