/*
 * Copyright (C) 2013 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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

package com.android.dialer.phonenumberutil;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Trace;
import android.provider.CallLog;
import android.telecom.PhoneAccountHandle;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.dialer.R;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.android.dialer.i18n.LocaleUtils;
import com.android.dialer.oem.MotorolaUtils;
import com.android.dialer.phonenumbergeoutil.PhoneNumberGeoUtilComponent;
import com.android.dialer.telecom.TelecomUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class PhoneNumberHelper {

  private static final Set<String> LEGACY_UNKNOWN_NUMBERS =
      new HashSet<>(Arrays.asList("-1", "-2", "-3"));

  /** Returns true if it is possible to place a call to the given number. */
  public static boolean canPlaceCallsTo(CharSequence number, int presentation) {
    return presentation == CallLog.Calls.PRESENTATION_ALLOWED
        && !TextUtils.isEmpty(number)
        && !isLegacyUnknownNumbers(number);
  }

  /**
   * Move the given cursor to a position where the number it points to matches the number in a
   * contact lookup URI.
   *
   * <p>We assume the cursor is one returned by the Contacts Provider when the URI asks for a
   * specific number. This method's behavior is undefined when the cursor doesn't meet the
   * assumption.
   *
   * <p>When determining whether two phone numbers are identical enough for caller ID purposes, the
   * Contacts Provider ignores special characters such as '#'. This makes it possible for the cursor
   * returned by the Contacts Provider to have multiple rows even when the URI asks for a specific
   * number.
   *
   * <p>For example, suppose the user has two contacts whose numbers are "#123" and "123",
   * respectively. When the URI asks for number "123", both numbers will be returned. Therefore, the
   * following strategy is employed to find a match.
   *
   * <p>In the following description, we use E to denote a number the cursor points to (an existing
   * contact number), and L to denote the number in the contact lookup URI.
   *
   * <p>If neither E nor L contains special characters, return true to indicate a match is found.
   *
   * <p>If either E or L contains special characters, return true when the raw numbers of E and L
   * are the same. Otherwise, move the cursor to its next position and start over.
   *
   * <p>Return false in all other circumstances to indicate that no match can be found.
   *
   * <p>When no match can be found, the cursor is after the last result when the method returns.
   *
   * @param cursor A cursor returned by the Contacts Provider.
   * @param columnIndexForNumber The index of the column where phone numbers are stored. It is the
   *     caller's responsibility to pass the correct column index.
   * @param contactLookupUri A URI used to retrieve a contact via the Contacts Provider. It is the
   *     caller's responsibility to ensure the URI is one that asks for a specific phone number.
   * @return true if a match can be found.
   */
  public static boolean updateCursorToMatchContactLookupUri(
          @Nullable Cursor cursor, int columnIndexForNumber, @Nullable Uri contactLookupUri) {
    if (cursor == null || contactLookupUri == null) {
      return false;
    }

    if (!cursor.moveToFirst()) {
      return false;
    }

    Assert.checkArgument(
        0 <= columnIndexForNumber && columnIndexForNumber < cursor.getColumnCount());

    String lookupNumber = contactLookupUri.getLastPathSegment();
    if (TextUtils.isEmpty(lookupNumber)) {
      return false;
    }

    boolean lookupNumberHasSpecialChars = numberHasSpecialChars(lookupNumber);

    do {
      String existingContactNumber = cursor.getString(columnIndexForNumber);
      boolean existingContactNumberHasSpecialChars = numberHasSpecialChars(existingContactNumber);

      if ((!lookupNumberHasSpecialChars && !existingContactNumberHasSpecialChars)
          || sameRawNumbers(existingContactNumber, lookupNumber)) {
        return true;
      }

    } while (cursor.moveToNext());

    return false;
  }

  /** Returns true if the input phone number contains special characters. */
  public static boolean numberHasSpecialChars(String number) {
    return !TextUtils.isEmpty(number) && number.contains("#");
  }

  /** Returns true if the raw numbers of the two input phone numbers are the same. */
  public static boolean sameRawNumbers(String number1, String number2) {
    String rawNumber1 =
        PhoneNumberUtils.stripSeparators(PhoneNumberUtils.convertKeypadLettersToDigits(number1));
    String rawNumber2 =
        PhoneNumberUtils.stripSeparators(PhoneNumberUtils.convertKeypadLettersToDigits(number2));

    return rawNumber1.equals(rawNumber2);
  }

  public static boolean isEmergencyNumber(Context context, String number) {
    TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
    return telephonyManager.isEmergencyNumber(number);
  }

  public static boolean isEmergencyNumber(Context context, String number, String countryIso) {
    return isEmergencyNumber(context, PhoneNumberUtils.formatNumberToE164(number, countryIso));
  }

  /**
   * Returns true if the given number is the number of the configured voicemail. To be able to
   * mock-out this, it is not a static method.
   */
  public static boolean isVoicemailNumber(
      Context context, PhoneAccountHandle accountHandle, CharSequence number) {
    if (TextUtils.isEmpty(number)) {
      return false;
    }
    return TelecomUtil.isVoicemailNumber(context, accountHandle, number.toString());
  }

  /**
   * Returns true if the given number is a SIP address. To be able to mock-out this, it is not a
   * static method.
   */
  public static boolean isSipNumber(CharSequence number) {
    return number != null && isUriNumber(number.toString());
  }

  public static boolean isLegacyUnknownNumbers(CharSequence number) {
    return number != null && LEGACY_UNKNOWN_NUMBERS.contains(number.toString());
  }

  /**
   * @param countryIso Country ISO used if there is no country code in the number, may be null
   *     otherwise.
   * @return a geographical description string for the specified number.
   */
  public static String getGeoDescription(
      Context context, String number, @Nullable String countryIso) {
    return PhoneNumberGeoUtilComponent.get(context)
        .getPhoneNumberGeoUtil()
        .getGeoDescription(context, number, countryIso);
  }

  /**
   * @param phoneAccountHandle {@code PhonAccountHandle} used to get current network country ISO.
   *     May be null if no account is in use or selected, in which case default account will be
   *     used.
   * @return The ISO 3166-1 two letters country code of the country the user is in based on the
   *     network location. If the network location does not exist, fall back to the locale setting.
   */
  public static String getCurrentCountryIso(
      Context context, @Nullable PhoneAccountHandle phoneAccountHandle) {
    Trace.beginSection("PhoneNumberHelper.getCurrentCountryIso");
    // Without framework function calls, this seems to be the most accurate location service
    // we can rely on.
    String countryIso =
        TelephonyManagerCompat.getNetworkCountryIsoForPhoneAccountHandle(
            context, phoneAccountHandle);
    if (TextUtils.isEmpty(countryIso)) {
      countryIso = LocaleUtils.getLocale(context).getCountry();
      LogUtil.i(
          "PhoneNumberHelper.getCurrentCountryIso",
          "No CountryDetector; falling back to countryIso based on locale: " + countryIso);
    }
    countryIso = countryIso.toUpperCase();
    Trace.endSection();

    return countryIso;
  }

  /**
   * An enhanced version of {@link PhoneNumberUtils#formatNumber(String, String, String)}.
   *
   * <p>The {@link Context} parameter allows us to tweak formatting according to device properties.
   *
   * <p>Returns the formatted phone number (e.g, 1-123-456-7890) or the original number if
   * formatting fails or is intentionally ignored.
   */
  public static String formatNumber(
      Context context, @Nullable String number, @Nullable String numberE164, String countryIso) {
    // The number can be null e.g. schema is voicemail and uri content is empty.
    if (number == null) {
      return null;
    }

    if (MotorolaUtils.shouldDisablePhoneNumberFormatting(context)) {
      return number;
    }

    String formattedNumber = PhoneNumberUtils.formatNumber(number, numberE164, countryIso);
    return formattedNumber != null ? formattedNumber : number;
  }

  /** @see #formatNumber(Context, String, String, String). */
  public static String formatNumber(Context context, @Nullable String number, String countryIso) {
    return formatNumber(context, number, /* numberE164 = */ null, countryIso);
  }

  @Nullable
  public static CharSequence formatNumberForDisplay(
      Context context, @Nullable String number, @NonNull String countryIso) {
    if (number == null) {
      return null;
    }

    return PhoneNumberUtils.createTtsSpannable(
        BidiFormatter.getInstance()
            .unicodeWrap(formatNumber(context, number, countryIso), TextDirectionHeuristics.LTR));
  }

  /**
   * Determines if the specified number is actually a URI (i.e. a SIP address) rather than a regular
   * PSTN phone number, based on whether or not the number contains an "@" character.
   *
   * @param number Phone number
   * @return true if number contains @
   *     <p>TODO: Remove if PhoneNumberUtils.isUriNumber(String number) is made public.
   */
  public static boolean isUriNumber(String number) {
    // Note we allow either "@" or "%40" to indicate a URI, in case
    // the passed-in string is URI-escaped.  (Neither "@" nor "%40"
    // will ever be found in a legal PSTN number.)
    return number != null && (number.contains("@") || number.contains("%40"));
  }

  /**
   * @param number SIP address of the form "username@domainname" (or the URI-escaped equivalent
   *     "username%40domainname")
   *     <p>TODO: Remove if PhoneNumberUtils.getUsernameFromUriNumber(String number) is made public.
   * @return the "username" part of the specified SIP address, i.e. the part before the "@"
   *     character (or "%40").
   */
  public static String getUsernameFromUriNumber(String number) {
    // The delimiter between username and domain name can be
    // either "@" or "%40" (the URI-escaped equivalent.)
    int delimiterIndex = number.indexOf('@');
    if (delimiterIndex < 0) {
      delimiterIndex = number.indexOf("%40");
    }
    if (delimiterIndex < 0) {
      LogUtil.i(
          "PhoneNumberHelper.getUsernameFromUriNumber",
          "getUsernameFromUriNumber: no delimiter found in SIP address: "
              + LogUtil.sanitizePii(number));
      return number;
    }
    return number.substring(0, delimiterIndex);
  }

  public static boolean compareSipAddresses(@Nullable String number1, @Nullable String number2) {
    if (number1 == null || number2 == null) {
      return Objects.equals(number1, number2);
    }

    int index1 = number1.indexOf('@');
    final String userinfo1;
    final String rest1;
    if (index1 != -1) {
      userinfo1 = number1.substring(0, index1);
      rest1 = number1.substring(index1);
    } else {
      userinfo1 = number1;
      rest1 = "";
    }

    int index2 = number2.indexOf('@');
    final String userinfo2;
    final String rest2;
    if (index2 != -1) {
      userinfo2 = number2.substring(0, index2);
      rest2 = number2.substring(index2);
    } else {
      userinfo2 = number2;
      rest2 = "";
    }

    return userinfo1.equals(userinfo2) && rest1.equalsIgnoreCase(rest2);
  }
}
