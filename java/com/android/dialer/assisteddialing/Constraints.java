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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber.CountryCodeSource;
import java.util.Locale;
import java.util.Optional;

/** Ensures that a number is eligible for Assisted Dialing */
@TargetApi(VERSION_CODES.N)
@SuppressWarnings("AndroidApiChecker") // Use of optional
final class Constraints {
  private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
  private final Context context;
  private final CountryCodeProvider countryCodeProvider;

  /**
   * Create a new instance of Constraints.
   *
   * @param context The context used to determine whether or not a number is an emergency number.
   * @param configProviderCountryCodes A csv of supported country codes, e.g. "US,CA"
   */
  public Constraints(@NonNull Context context, @NonNull CountryCodeProvider countryCodeProvider) {
    if (context == null) {
      throw new NullPointerException("Provided context cannot be null");
    }
    this.context = context;

    if (countryCodeProvider == null) {
      throw new NullPointerException("Provided configProviderCountryCodes cannot be null");
    }

    // We allow dynamic country support only in Dialer; this should be removed in the framework
    // implementation.
    // TODO(erfanian): Remove in the framework implementation.
    this.countryCodeProvider = countryCodeProvider;
  }

  /**
   * Determines whether or not we think Assisted Dialing is possible given the provided parameters.
   *
   * @param numberToCheck A string containing the phone number.
   * @param userHomeCountryCode A string containing an ISO 3166-1 alpha-2 country code representing
   *     the user's home country.
   * @param userRoamingCountryCode A string containing an ISO 3166-1 alpha-2 country code
   *     representing the user's roaming country.
   * @return A boolean indicating whether or not the provided values are eligible for assisted
   *     dialing.
   */
  public boolean meetsPreconditions(
      @NonNull String numberToCheck,
      @NonNull String userHomeCountryCode,
      @NonNull String userRoamingCountryCode) {

    if (TextUtils.isEmpty(numberToCheck)) {
      LogUtil.i("Constraints.meetsPreconditions", "numberToCheck was empty");
      return false;
    }

    if (TextUtils.isEmpty(userHomeCountryCode)) {
      LogUtil.i("Constraints.meetsPreconditions", "userHomeCountryCode was empty");
      return false;
    }

    if (TextUtils.isEmpty(userRoamingCountryCode)) {
      LogUtil.i("Constraints.meetsPreconditions", "userRoamingCountryCode was empty");
      return false;
    }

    userHomeCountryCode = userHomeCountryCode.toUpperCase(Locale.US);
    userRoamingCountryCode = userRoamingCountryCode.toUpperCase(Locale.US);

    Optional<PhoneNumber> parsedPhoneNumber = parsePhoneNumber(numberToCheck, userHomeCountryCode);

    if (!parsedPhoneNumber.isPresent()) {
      LogUtil.i("Constraints.meetsPreconditions", "parsedPhoneNumber was empty");
      return false;
    }

    return areSupportedCountryCodes(userHomeCountryCode, userRoamingCountryCode)
        && isUserRoaming(userHomeCountryCode, userRoamingCountryCode)
        && isNotInternationalNumber(parsedPhoneNumber)
        && isNotEmergencyNumber(numberToCheck, context)
        && isValidNumber(parsedPhoneNumber)
        && doesNotHaveExtension(parsedPhoneNumber);
  }

  /** Returns a boolean indicating the value equivalence of the provided country codes. */
  private boolean isUserRoaming(
      @NonNull String userHomeCountryCode, @NonNull String userRoamingCountryCode) {
    boolean result = !userHomeCountryCode.equals(userRoamingCountryCode);
    LogUtil.i("Constraints.isUserRoaming", String.valueOf(result));
    return result;
  }

  /**
   * Returns a boolean indicating the support of both provided country codes for assisted dialing.
   * Both country codes must be allowed for the return value to be true.
   */
  private boolean areSupportedCountryCodes(
      @NonNull String userHomeCountryCode, @NonNull String userRoamingCountryCode) {
    if (TextUtils.isEmpty(userHomeCountryCode)) {
      LogUtil.i("Constraints.areSupportedCountryCodes", "userHomeCountryCode was empty");
      return false;
    }

    if (TextUtils.isEmpty(userRoamingCountryCode)) {
      LogUtil.i("Constraints.areSupportedCountryCodes", "userRoamingCountryCode was empty");
      return false;
    }

    boolean result =
        countryCodeProvider.isSupportedCountryCode(userHomeCountryCode)
            && countryCodeProvider.isSupportedCountryCode(userRoamingCountryCode);
    LogUtil.i("Constraints.areSupportedCountryCodes", String.valueOf(result));
    return result;
  }

  /**
   * A convenience method to take a number as a String and a specified country code, and return a
   * PhoneNumber object.
   */
  private Optional<PhoneNumber> parsePhoneNumber(
      @NonNull String numberToParse, @NonNull String userHomeCountryCode) {
    try {
      // TODO(erfanian): confirm behavior of blocking the foreground thread when moving to the
      // framework
      return Optional.of(phoneNumberUtil.parseAndKeepRawInput(numberToParse, userHomeCountryCode));
    } catch (NumberParseException e) {
      Logger.get(context)
          .logImpression(DialerImpression.Type.ASSISTED_DIALING_CONSTRAINT_PARSING_FAILURE);
      LogUtil.i("Constraints.parsePhoneNumber", "could not parse the number");
      return Optional.empty();
    }
  }

  /** Returns a boolean indicating if the provided number is already internationally formatted. */
  private boolean isNotInternationalNumber(@NonNull Optional<PhoneNumber> parsedPhoneNumber) {

    if (parsedPhoneNumber.get().hasCountryCode()
        && parsedPhoneNumber.get().getCountryCodeSource()
            != CountryCodeSource.FROM_DEFAULT_COUNTRY) {
      Logger.get(context)
          .logImpression(DialerImpression.Type.ASSISTED_DIALING_CONSTRAINT_NUMBER_HAS_COUNTRY_CODE);
      LogUtil.i(
          "Constraints.isNotInternationalNumber", "phone number already provided the country code");
      return false;
    }
    return true;
  }

  /**
   * Returns a boolean indicating if the provided number has an extension.
   *
   * <p>Extensions are currently stripped when formatting a number for mobile dialing, so we don't
   * want to purposefully truncate a number.
   */
  private boolean doesNotHaveExtension(@NonNull Optional<PhoneNumber> parsedPhoneNumber) {

    if (parsedPhoneNumber.get().hasExtension()
        && !TextUtils.isEmpty(parsedPhoneNumber.get().getExtension())) {
      Logger.get(context)
          .logImpression(DialerImpression.Type.ASSISTED_DIALING_CONSTRAINT_NUMBER_HAS_EXTENSION);
      LogUtil.i("Constraints.doesNotHaveExtension", "phone number has an extension");
      return false;
    }
    return true;
  }

  /** Returns a boolean indicating if the provided number is considered to be a valid number. */
  private boolean isValidNumber(@NonNull Optional<PhoneNumber> parsedPhoneNumber) {
    boolean result = PhoneNumberUtil.getInstance().isValidNumber(parsedPhoneNumber.get());
    LogUtil.i("Constraints.isValidNumber", String.valueOf(result));

    return result;
  }

  /** Returns a boolean indicating if the provided number is an emergency number. */
  private boolean isNotEmergencyNumber(@NonNull String numberToCheck, @NonNull Context context) {
    // isEmergencyNumber may depend on network state, so also use isLocalEmergencyNumber when
    // roaming and out of service.
    boolean result =
        !PhoneNumberUtils.isEmergencyNumber(numberToCheck)
            && !PhoneNumberUtils.isLocalEmergencyNumber(context, numberToCheck);
    LogUtil.i("Constraints.isNotEmergencyNumber", String.valueOf(result));
    return result;
  }
}
