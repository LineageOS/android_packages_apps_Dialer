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

import android.text.TextUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.strictmode.StrictModeUtils;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import java.util.Optional;

/** Responsible for transforming numbers to make them dialable and valid when roaming. */
final class NumberTransformer {

  private final PhoneNumberUtil phoneNumberUtil;
  private final Constraints constraints;

  NumberTransformer(Constraints constraints) {
    this.constraints = constraints;
    this.phoneNumberUtil = StrictModeUtils.bypass(PhoneNumberUtil::getInstance);
  }

  /**
   * A method to do assisted dialing transformations.
   *
   * <p>The library will do its best to attempt a transformation, but, if for any reason the
   * transformation fails, we return an empty optional. The operation can be considered a success
   * when the Optional we return has a value set.
   */
  Optional<TransformationInfo> doAssistedDialingTransformation(
      String numbertoTransform, String userHomeCountryCode, String userRoamingCountryCode) {

    if (!constraints.meetsPreconditions(
        numbertoTransform, userHomeCountryCode, userRoamingCountryCode)) {
      LogUtil.i(
          "NumberTransformer.doAssistedDialingTransformation",
          "assisted dialing failed preconditions");
      return Optional.empty();
    }

    PhoneNumber phoneNumber =
        StrictModeUtils.bypass(
            () -> {
              try {
                return phoneNumberUtil.parse(numbertoTransform, userHomeCountryCode);
              } catch (NumberParseException e) {
                LogUtil.i(
                    "NumberTransformer.doAssistedDialingTransformation", "number failed to parse");
                return null;
              }
            });

    if (phoneNumber == null) {
      return Optional.empty();
    }

    String transformedNumber =
        StrictModeUtils.bypass(
            () ->
                phoneNumberUtil.formatNumberForMobileDialing(
                    phoneNumber, userRoamingCountryCode, true));

    // formatNumberForMobileDialing may return an empty String.
    if (TextUtils.isEmpty(transformedNumber)) {
      LogUtil.i(
          "NumberTransformer.doAssistedDialingTransformation",
          "formatNumberForMobileDialing returned an empty string");
      return Optional.empty();
    }

    // TODO Verify the transformed number is still valid?
    return Optional.of(
        TransformationInfo.builder()
            .setOriginalNumber(numbertoTransform)
            .setTransformedNumber(transformedNumber)
            .setUserHomeCountryCode(userHomeCountryCode)
            .setUserRoamingCountryCode(userRoamingCountryCode)
            .setTransformedNumberCountryCallingCode(phoneNumber.getCountryCode())
            .build());
  }
}
