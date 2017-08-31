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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import com.android.dialer.DialerInternalPhoneNumber;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.DialerPhoneNumber.RawInput;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.MatchType;

/**
 * Wrapper for selected methods in {@link PhoneNumberUtil} which uses the {@link DialerPhoneNumber}
 * lite proto instead of the {@link com.google.i18n.phonenumbers.Phonenumber.PhoneNumber} POJO.
 *
 * <p>All methods should be called on a worker thread.
 */
public class DialerPhoneNumberUtil {
  private final PhoneNumberUtil phoneNumberUtil;

  @WorkerThread
  public DialerPhoneNumberUtil(@NonNull PhoneNumberUtil phoneNumberUtil) {
    Assert.isWorkerThread();
    this.phoneNumberUtil = Assert.isNotNull(phoneNumberUtil);
  }

  /**
   * Parses the provided raw phone number into a {@link DialerPhoneNumber}.
   *
   * @see PhoneNumberUtil#parse(String, String)
   */
  @WorkerThread
  public DialerPhoneNumber parse(@Nullable String numberToParse, @Nullable String defaultRegion) {
    Assert.isWorkerThread();

    DialerPhoneNumber.Builder dialerPhoneNumber = DialerPhoneNumber.newBuilder();
    RawInput.Builder rawInput = RawInput.newBuilder();
    // Numbers can be null or empty for incoming "unknown" calls.
    if (numberToParse != null) {
      rawInput.setNumber(numberToParse);
    }
    if (defaultRegion != null) {
      rawInput.setCountryIso(defaultRegion);
    }
    dialerPhoneNumber.setRawInput(rawInput.build());

    try {
      dialerPhoneNumber.setDialerInternalPhoneNumber(
          Converter.pojoToProto(phoneNumberUtil.parse(numberToParse, defaultRegion)));
    } catch (NumberParseException e) {
      LogUtil.w("DialerPhoneNumberUtil.parse", "couldn't parse phone number", e);
    }
    return dialerPhoneNumber.build();
  }

  /**
   * Returns true if the two numbers were parseable by libphonenumber and are an {@link
   * MatchType#EXACT_MATCH} or if they have the same raw input.
   */
  @WorkerThread
  public boolean isExactMatch(
      @NonNull DialerPhoneNumber firstNumberIn, @NonNull DialerPhoneNumber secondNumberIn) {
    Assert.isWorkerThread();
    if (!Assert.isNotNull(firstNumberIn).hasDialerInternalPhoneNumber()
        || !Assert.isNotNull(secondNumberIn).hasDialerInternalPhoneNumber()) {
      return firstNumberIn.getRawInput().equals(secondNumberIn.getRawInput());
    }
    return isNumberMatch(
            firstNumberIn.getDialerInternalPhoneNumber(),
            secondNumberIn.getDialerInternalPhoneNumber())
        == MatchType.EXACT_MATCH;
  }

  /**
   * Compares the provided phone numbers.
   *
   * @see PhoneNumberUtil#isNumberMatch(com.google.i18n.phonenumbers.Phonenumber.PhoneNumber,
   *     com.google.i18n.phonenumbers.Phonenumber.PhoneNumber)
   */
  @WorkerThread
  private MatchType isNumberMatch(
      @NonNull DialerInternalPhoneNumber firstNumberIn,
      @NonNull DialerInternalPhoneNumber secondNumberIn) {
    Assert.isWorkerThread();
    return phoneNumberUtil.isNumberMatch(
        Converter.protoToPojo(Assert.isNotNull(firstNumberIn)),
        Converter.protoToPojo(Assert.isNotNull(secondNumberIn)));
  }
}
