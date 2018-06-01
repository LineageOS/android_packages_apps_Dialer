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

package com.android.dialer.precall.impl;

import android.content.Context;
import android.telephony.PhoneNumberUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProviderComponent;
import com.android.dialer.precall.impl.MalformedNumberRectifier.MalformedNumberHandler;
import com.google.common.base.Optional;

/**
 * It is customary in UK to present numbers as "+44 (0) xx xxxx xxxx". This is actually a amalgam of
 * international (+44 xx xxxx xxxx) and regional (0xx xxxx xxxx) format, and is in fact invalid. It
 * might be rejected depending on the carrier.
 *
 * <p>This class removes the "0" region code prefix if the first dialable digits are "+440". UK
 * short codes and region codes in international format will never start with a 0.
 */
class UkRegionPrefixInInternationalFormatHandler implements MalformedNumberHandler {

  private static final String MALFORMED_PREFIX = "+440";

  @Override
  public Optional<String> handle(Context context, String number) {
    if (!ConfigProviderComponent.get(context)
        .getConfigProvider()
        .getBoolean("uk_region_prefix_in_international_format_fix_enabled", true)) {
      return Optional.absent();
    }
    if (!PhoneNumberUtils.normalizeNumber(number).startsWith(MALFORMED_PREFIX)) {
      return Optional.absent();
    }
    LogUtil.i("UkRegionPrefixInInternationalFormatHandler.handle", "removing (0) in UK numbers");

    // libPhoneNumber is not used because we want to keep post dial digits, and this is on the main
    // thread.
    String convertedNumber = PhoneNumberUtils.convertKeypadLettersToDigits(number);
    StringBuilder result = new StringBuilder();
    int prefixPosition = 0;
    for (int i = 0; i < convertedNumber.length(); i++) {
      char c = convertedNumber.charAt(i);
      if (c != MALFORMED_PREFIX.charAt(prefixPosition)) {
        result.append(c);
        continue;
      }
      prefixPosition++;
      if (prefixPosition == MALFORMED_PREFIX.length()) {
        result.append(convertedNumber.substring(i + 1));
        break;
      }
      result.append(c);
    }
    return Optional.of(result.toString());
  }
}
