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
package com.android.dialer.enrichedcall;

import android.support.annotation.NonNull;

/** Utility for comparing phone numbers. */
public class FuzzyPhoneNumberMatcher {

  private static final int REQUIRED_MATCHED_DIGITS = 7;

  /**
   * Returns {@code true} if the given numbers can be interpreted to be the same.
   *
   * <p>This method is called numerous times when rendering the call log. Using string methods is
   * too slow, so character by character matching is used instead.
   */
  public static boolean matches(@NonNull String lhs, @NonNull String rhs) {
    int aIndex = lhs.length() - 1;
    int bIndex = rhs.length() - 1;

    int matchedDigits = 0;

    while (aIndex >= 0 && bIndex >= 0) {
      if (!Character.isDigit(lhs.charAt(aIndex))) {
        --aIndex;
        continue;
      }
      if (!Character.isDigit(rhs.charAt(bIndex))) {
        --bIndex;
        continue;
      }
      if (lhs.charAt(aIndex) != rhs.charAt(bIndex)) {
        return false;
      }
      --aIndex;
      --bIndex;
      ++matchedDigits;
    }

    return matchedDigits >= REQUIRED_MATCHED_DIGITS;
  }
}
