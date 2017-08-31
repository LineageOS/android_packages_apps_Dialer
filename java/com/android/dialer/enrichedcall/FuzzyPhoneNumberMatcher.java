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
    return lastSevenDigitsCharacterByCharacterMatches(lhs, rhs);
  }

  /**
   * This strategy examines the numbers character by character starting from the end. If the last
   * {@link #REQUIRED_MATCHED_DIGITS} match, it returns {@code true}.
   */
  private static boolean lastSevenDigitsCharacterByCharacterMatches(
      @NonNull String lhs, @NonNull String rhs) {
    int lhsIndex = lhs.length() - 1;
    int rhsIndex = rhs.length() - 1;

    int matchedDigits = 0;

    while (lhsIndex >= 0 && rhsIndex >= 0) {
      if (!Character.isDigit(lhs.charAt(lhsIndex))) {
        --lhsIndex;
        continue;
      }
      if (!Character.isDigit(rhs.charAt(rhsIndex))) {
        --rhsIndex;
        continue;
      }
      if (lhs.charAt(lhsIndex) != rhs.charAt(rhsIndex)) {
        break;
      }
      --lhsIndex;
      --rhsIndex;
      ++matchedDigits;
    }

    return matchedDigits >= REQUIRED_MATCHED_DIGITS;
  }
}
