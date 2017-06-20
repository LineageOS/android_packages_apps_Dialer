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
package com.android.dialer.phonenumberproto;

import com.android.dialer.DialerInternalPhoneNumber;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

/**
 * Methods for converting from {@link PhoneNumber} POJOs to {@link DialerInternalPhoneNumber} protos
 * and back.
 */
class Converter {

  static DialerInternalPhoneNumber pojoToProto(PhoneNumber pojo) {
    DialerInternalPhoneNumber.Builder proto = DialerInternalPhoneNumber.newBuilder();
    if (pojo.hasCountryCode()) {
      proto.setCountryCode(pojo.getCountryCode());
    }
    if (pojo.hasCountryCodeSource()) {
      switch (pojo.getCountryCodeSource()) {
        case FROM_NUMBER_WITH_PLUS_SIGN:
          proto.setCountryCodeSource(
              DialerInternalPhoneNumber.CountryCodeSource.FROM_NUMBER_WITH_PLUS_SIGN);
          break;
        case FROM_NUMBER_WITH_IDD:
          proto.setCountryCodeSource(
              DialerInternalPhoneNumber.CountryCodeSource.FROM_NUMBER_WITH_IDD);
          break;
        case FROM_NUMBER_WITHOUT_PLUS_SIGN:
          proto.setCountryCodeSource(
              DialerInternalPhoneNumber.CountryCodeSource.FROM_NUMBER_WITHOUT_PLUS_SIGN);
          break;
        case FROM_DEFAULT_COUNTRY:
          proto.setCountryCodeSource(
              DialerInternalPhoneNumber.CountryCodeSource.FROM_DEFAULT_COUNTRY);
          break;
        default:
          throw new IllegalArgumentException(
              "unsupported country code source: " + pojo.getCountryCodeSource());
      }
    }
    if (pojo.hasExtension()) {
      proto.setExtension(pojo.getExtension());
    }
    if (pojo.hasItalianLeadingZero()) {
      proto.setItalianLeadingZero(pojo.isItalianLeadingZero());
    }
    if (pojo.hasNationalNumber()) {
      proto.setNationalNumber(pojo.getNationalNumber());
    }
    if (pojo.hasNumberOfLeadingZeros()) {
      proto.setNumberOfLeadingZeros(pojo.getNumberOfLeadingZeros());
    }
    if (pojo.hasPreferredDomesticCarrierCode()) {
      proto.setPreferredDomesticCarrierCode(pojo.getPreferredDomesticCarrierCode());
    }
    if (pojo.hasRawInput()) {
      proto.setRawInput(pojo.getRawInput());
    }
    return proto.build();
  }

  static PhoneNumber protoToPojo(DialerInternalPhoneNumber proto) {
    PhoneNumber pojo = new PhoneNumber();
    if (proto.hasCountryCode()) {
      pojo.setCountryCode(proto.getCountryCode());
    }
    if (proto.hasCountryCodeSource()) {
      switch (proto.getCountryCodeSource()) {
        case FROM_NUMBER_WITH_PLUS_SIGN:
          pojo.setCountryCodeSource(PhoneNumber.CountryCodeSource.FROM_NUMBER_WITH_PLUS_SIGN);
          break;
        case FROM_NUMBER_WITH_IDD:
          pojo.setCountryCodeSource(PhoneNumber.CountryCodeSource.FROM_NUMBER_WITH_IDD);
          break;
        case FROM_NUMBER_WITHOUT_PLUS_SIGN:
          pojo.setCountryCodeSource(PhoneNumber.CountryCodeSource.FROM_NUMBER_WITHOUT_PLUS_SIGN);
          break;
        case FROM_DEFAULT_COUNTRY:
          pojo.setCountryCodeSource(PhoneNumber.CountryCodeSource.FROM_DEFAULT_COUNTRY);
          break;
        default:
          throw new IllegalArgumentException(
              "unsupported country code source: " + proto.getCountryCodeSource());
      }
    }
    if (proto.hasExtension()) {
      pojo.setExtension(proto.getExtension());
    }
    if (proto.hasItalianLeadingZero()) {
      pojo.setItalianLeadingZero(proto.getItalianLeadingZero());
    }
    if (proto.hasNationalNumber()) {
      pojo.setNationalNumber(proto.getNationalNumber());
    }
    if (proto.hasNumberOfLeadingZeros()) {
      pojo.setNumberOfLeadingZeros(proto.getNumberOfLeadingZeros());
    }
    if (proto.hasPreferredDomesticCarrierCode()) {
      pojo.setPreferredDomesticCarrierCode(proto.getPreferredDomesticCarrierCode());
    }
    if (proto.hasRawInput()) {
      pojo.setRawInput(proto.getRawInput());
    }
    return pojo;
  }
}
