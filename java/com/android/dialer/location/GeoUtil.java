/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.dialer.location;

import android.content.Context;
import android.os.Trace;

/** Static methods related to Geo. */
public class GeoUtil {

  /**
   * Return the ISO 3166-1 two letters country code of the country the user is in.
   *
   * <p>WARNING: {@link CountryDetector} caches TelephonyManager and other system services in a
   * static. {@link CountryDetector#instance} must be reset in tests.
   */
  public static String getCurrentCountryIso(Context context) {
    // The {@link CountryDetector} should never return null so this is safe to return as-is.
    Trace.beginSection("GeoUtil.getCurrentCountryIso");
    String countryIso = CountryDetector.getInstance(context).getCurrentCountryIso();
    Trace.endSection();
    return countryIso;
  }
}
