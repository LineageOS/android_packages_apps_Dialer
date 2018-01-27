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

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.MatchType;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

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
   * @see PhoneNumberUtil#parse(CharSequence, String)
   */
  @WorkerThread
  public DialerPhoneNumber parse(@Nullable String numberToParse, @Nullable String defaultRegion) {
    Assert.isWorkerThread();

    DialerPhoneNumber.Builder dialerPhoneNumber = DialerPhoneNumber.newBuilder();
    // Numbers can be null or empty for incoming "unknown" calls.
    if (numberToParse != null) {
      dialerPhoneNumber.setNormalizedNumber(normalizeNumber(numberToParse, defaultRegion));
    }
    if (defaultRegion != null) {
      dialerPhoneNumber.setCountryIso(defaultRegion);
    }
    return dialerPhoneNumber.build();
  }

  /**
   * Parses the provided raw phone number into a Future result of {@link DialerPhoneNumber}.
   *
   * <p>Work is run on the provided {@link ListeningExecutorService}.
   *
   * @see PhoneNumberUtil#parse(CharSequence, String)
   */
  @AnyThread
  public ListenableFuture<DialerPhoneNumber> parse(
      @Nullable String numberToParse,
      @Nullable String defaultRegion,
      @NonNull ListeningExecutorService service) {
    return service.submit(() -> parse(numberToParse, defaultRegion));
  }

  /**
   * Formats the provided number to E164 format or return a normalized version of the raw number if
   * the number is not valid according to {@link PhoneNumberUtil#isValidNumber(PhoneNumber)}.
   *
   * @see #formatToValidE164(DialerPhoneNumber)
   * @see PhoneNumberUtils#normalizeNumber(String)
   */
  public String normalizeNumber(DialerPhoneNumber number) {
    // TODO(zachh): Inline this method.
    // TODO(zachh): This loses country info when number is not valid.
    return number.getNormalizedNumber();
  }

  @WorkerThread
  private String normalizeNumber(@NonNull String rawNumber, @Nullable String defaultRegion) {
    Assert.isWorkerThread();

    // If the number is a service number, just store the raw number and don't bother trying to parse
    // it. PhoneNumberUtil#parse ignores these characters which can lead to confusing behavior, such
    // as the numbers "#123" and "123" being considered the same. The "#" can appear in the middle
    // of a service number and the "*" can appear at the beginning (see a bug).
    if (isServiceNumber(rawNumber)) {
      return rawNumber;
    }

    String postDialPortion = PhoneNumberUtils.extractPostDialPortion(rawNumber);
    String networkPortion = PhoneNumberUtils.extractNetworkPortion(rawNumber);

    try {
      PhoneNumber phoneNumber = phoneNumberUtil.parse(networkPortion, defaultRegion);
      if (phoneNumberUtil.isValidNumber(phoneNumber)) {
        String validNumber = phoneNumberUtil.format(phoneNumber, PhoneNumberFormat.E164);
        if (TextUtils.isEmpty(validNumber)) {
          throw new IllegalStateException(
              "e164 number should not be empty: " + LogUtil.sanitizePii(rawNumber));
        }
        // The E164 representation doesn't contain post-dial digits, but we need to preserve them.
        if (postDialPortion != null) {
          validNumber += postDialPortion;
        }
        return validNumber;
      }
    } catch (NumberParseException e) {
      // fall through
    }
    return networkPortion + postDialPortion;
  }

  /**
   * Returns true if the two numbers:
   *
   * <ul>
   *   <li>were parseable by libphonenumber (see {@link #parse(String, String)}),
   *   <li>are a {@link MatchType#SHORT_NSN_MATCH}, {@link MatchType#NSN_MATCH}, or {@link
   *       MatchType#EXACT_MATCH}, and
   *   <li>have the same post-dial digits.
   * </ul>
   *
   * <p>If either number is not parseable, returns true if their raw inputs have the same network
   * and post-dial portions.
   *
   * <p>An empty number is never considered to match another number.
   *
   * @see PhoneNumberUtil#isNumberMatch(PhoneNumber, PhoneNumber)
   */
  @WorkerThread
  public boolean isMatch(
      @NonNull DialerPhoneNumber firstNumberIn, @NonNull DialerPhoneNumber secondNumberIn) {
    Assert.isWorkerThread();

    // An empty number should not be combined with any other number.
    if (firstNumberIn.getNormalizedNumber().isEmpty()
        || secondNumberIn.getNormalizedNumber().isEmpty()) {
      return false;
    }

    // Two numbers with different countries should not match.
    if (!firstNumberIn.getCountryIso().equals(secondNumberIn.getCountryIso())) {
      return false;
    }

    PhoneNumber phoneNumber1 = null;
    try {
      phoneNumber1 =
          phoneNumberUtil.parse(firstNumberIn.getNormalizedNumber(), firstNumberIn.getCountryIso());
    } catch (NumberParseException e) {
      // fall through
    }

    PhoneNumber phoneNumber2 = null;
    try {
      phoneNumber2 =
          phoneNumberUtil.parse(
              secondNumberIn.getNormalizedNumber(), secondNumberIn.getCountryIso());
    } catch (NumberParseException e) {
      // fall through
    }

    // If either number is a service number or either number can't be parsed by libphonenumber, just
    // fallback to basic textual matching.
    if (isServiceNumber(firstNumberIn.getNormalizedNumber())
        || isServiceNumber(secondNumberIn.getNormalizedNumber())
        || phoneNumber1 == null
        || phoneNumber2 == null) {
      return firstNumberIn.getNormalizedNumber().equals(secondNumberIn.getNormalizedNumber());
    }

    // Both numbers are parseable, use more sophisticated libphonenumber matching.
    MatchType matchType = phoneNumberUtil.isNumberMatch(phoneNumber1, phoneNumber2);

    return (matchType == MatchType.SHORT_NSN_MATCH
            || matchType == MatchType.NSN_MATCH
            || matchType == MatchType.EXACT_MATCH)
        && samePostDialPortion(firstNumberIn, secondNumberIn);
  }

  private static boolean samePostDialPortion(DialerPhoneNumber number1, DialerPhoneNumber number2) {
    return PhoneNumberUtils.extractPostDialPortion(number1.getNormalizedNumber())
        .equals(PhoneNumberUtils.extractPostDialPortion(number2.getNormalizedNumber()));
  }

  /**
   * If the provided number is "valid" (see {@link PhoneNumberUtil#isValidNumber(PhoneNumber)}),
   * formats it to E.164. Otherwise, returns {@link Optional#absent()}.
   *
   * <p>This method is analogous to {@link PhoneNumberUtils#formatNumberToE164(String, String)} (but
   * works with an already parsed {@link DialerPhoneNumber} object).
   *
   * @see PhoneNumberUtil#isValidNumber(PhoneNumber)
   * @see PhoneNumberUtil#format(PhoneNumber, PhoneNumberFormat)
   * @see PhoneNumberUtils#formatNumberToE164(String, String)
   */
  @WorkerThread
  public Optional<String> formatToValidE164(DialerPhoneNumber number) {
    // TODO(zachh): We could do something like store a "valid" bit in DialerPhoneNumber?
    Assert.isWorkerThread();
    PhoneNumber phoneNumber;
    try {
      phoneNumber = phoneNumberUtil.parse(number.getNormalizedNumber(), number.getCountryIso());
    } catch (NumberParseException e) {
      return Optional.absent();
    }
    if (phoneNumberUtil.isValidNumber(phoneNumber)) {
      return Optional.fromNullable(phoneNumberUtil.format(phoneNumber, PhoneNumberFormat.E164));
    }
    return Optional.absent();
  }

  private boolean isServiceNumber(@NonNull String rawNumber) {
    return rawNumber.contains("#") || rawNumber.startsWith("*");
  }
}
