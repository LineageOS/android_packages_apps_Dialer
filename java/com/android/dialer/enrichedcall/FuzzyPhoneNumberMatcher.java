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
import com.android.dialer.common.Assert;

/** Utility for comparing phone numbers. */
public class FuzzyPhoneNumberMatcher {

  /** Returns {@code true} if the given numbers can be interpreted to be the same. */
  public static boolean matches(@NonNull String a, @NonNull String b) {
    String aNormalized = Assert.isNotNull(a).replaceAll("[^0-9]", "");
    String bNormalized = Assert.isNotNull(b).replaceAll("[^0-9]", "");
    if (aNormalized.length() < 7 || bNormalized.length() < 7) {
      return false;
    }
    String aMatchable = aNormalized.substring(aNormalized.length() - 7);
    String bMatchable = bNormalized.substring(bNormalized.length() - 7);
    return aMatchable.equals(bMatchable);
  }
}
