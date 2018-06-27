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

import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.ArraySet;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

/** A class to provide the appropriate country codes related to assisted dialing. */
public final class CountryCodeProvider {

  // TODO(erfanian): Ensure the below standard is consistent between libphonenumber and the
  // platform.
  // ISO 3166-1 alpha-2 Country Codes that are eligible for assisted dialing.
  @VisibleForTesting
  static final List<String> DEFAULT_COUNTRY_CODES =
      Arrays.asList(
          "CA" /* Canada */,
          "GB" /* United Kingdom */,
          "JP" /* Japan */,
          "MX" /* Mexico */,
          "US" /* United States */);

  private final Set<String> supportedCountryCodes;

  CountryCodeProvider(ConfigProvider configProvider) {
    supportedCountryCodes =
        parseConfigProviderCountryCodes(
                configProvider.getString("assisted_dialing_csv_country_codes", ""))
            .stream()
            .map(v -> v.toUpperCase(Locale.US))
            .collect(Collectors.toCollection(ArraySet::new));
    LogUtil.i(
        "CountryCodeProvider.CountryCodeProvider", "Using country codes: " + supportedCountryCodes);
  }

  /** Checks whether a supplied country code is supported. */
  public boolean isSupportedCountryCode(String countryCode) {
    return supportedCountryCodes.contains(countryCode);
  }

  private List<String> parseConfigProviderCountryCodes(String configProviderCountryCodes) {
    if (TextUtils.isEmpty(configProviderCountryCodes)) {
      LogUtil.i(
          "Constraints.parseConfigProviderCountryCodes",
          "configProviderCountryCodes was empty, returning default");
      return DEFAULT_COUNTRY_CODES;
    }

    StringTokenizer tokenizer = new StringTokenizer(configProviderCountryCodes, ",");

    if (tokenizer.countTokens() < 1) {
      LogUtil.i(
          "Constraints.parseConfigProviderCountryCodes", "insufficient provided country codes");
      return DEFAULT_COUNTRY_CODES;
    }

    List<String> parsedCountryCodes = new ArrayList<>();
    while (tokenizer.hasMoreTokens()) {
      String foundLocale = tokenizer.nextToken();
      if (foundLocale == null) {
        LogUtil.i(
            "Constraints.parseConfigProviderCountryCodes",
            "Unexpected empty value, returning default.");
        return DEFAULT_COUNTRY_CODES;
      }

      if (foundLocale.length() != 2) {
        LogUtil.i(
            "Constraints.parseConfigProviderCountryCodes",
            "Unexpected locale %s, returning default",
            foundLocale);
        return DEFAULT_COUNTRY_CODES;
      }

      parsedCountryCodes.add(foundLocale);
    }
    return parsedCountryCodes;
  }
}
