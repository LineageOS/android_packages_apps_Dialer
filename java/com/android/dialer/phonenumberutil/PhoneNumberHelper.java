/*
 * Copyright (C) 2013 The Android Open Source Project
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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.android.dialer.i18n.LocaleUtils;
import com.android.dialer.oem.MotorolaUtils;
import com.android.dialer.oem.PhoneNumberUtilsAccessor;
import com.android.dialer.phonenumbergeoutil.PhoneNumberGeoUtilComponent;
import com.android.dialer.telecom.TelecomUtil;
import com.google.common.base.Optional;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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

  /**
   * An enhanced version of {@link PhoneNumberUtils#isLocalEmergencyNumber(Context, String)}.
   *
   * <p>This methods supports checking the number for all SIMs.
   *
   * @param context the context which the number should be checked against
   * @param number the number to tbe checked
   * @return true if the specified number is an emergency number for any SIM in the device.
   */
  @SuppressWarnings("Guava")
  public static boolean isLocalEmergencyNumber(Context context, String number) {
    List<PhoneAccountHandle> phoneAccountHandles =
        TelecomUtil.getSubscriptionPhoneAccounts(context);

    // If the number of phone accounts with a subscription is no greater than 1, only one SIM is
    // installed in the device. We hand over the job to PhoneNumberUtils#isLocalEmergencyNumber.
    if (phoneAccountHandles.size() <= 1) {
      return PhoneNumberUtils.isLocalEmergencyNumber(context, number);
    }

    for (PhoneAccountHandle phoneAccountHandle : phoneAccountHandles) {
      Optional<SubscriptionInfo> subscriptionInfo =
          TelecomUtil.getSubscriptionInfo(context, phoneAccountHandle);
      if (subscriptionInfo.isPresent()
          && PhoneNumberUtilsAccessor.isLocalEmergencyNumber(
              context, subscriptionInfo.get().getSubscriptionId(), number)) {
        return true;
      }
    }

    return false;
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

  public static boolean isUnknownNumberThatCanBeLookedUp(
      Context context, PhoneAccountHandle accountHandle, CharSequence number, int presentation) {
    if (presentation == CallLog.Calls.PRESENTATION_UNKNOWN) {
      return false;
    }
    if (presentation == CallLog.Calls.PRESENTATION_RESTRICTED) {
      return false;
    }
    if (presentation == CallLog.Calls.PRESENTATION_PAYPHONE) {
      return false;
    }
    if (TextUtils.isEmpty(number)) {
      return false;
    }
    if (isVoicemailNumber(context, accountHandle, number)) {
      return false;
    }
    if (isLegacyUnknownNumbers(number)) {
      return false;
    }
    return true;
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

  private static boolean isVerizon(Context context) {
    // Verizon MCC/MNC codes copied from com/android/voicemailomtp/res/xml/vvm_config.xml.
    // TODO(sail): Need a better way to do per carrier and per OEM configurations.
    switch (context.getSystemService(TelephonyManager.class).getSimOperator()) {
      case "310004":
      case "310010":
      case "310012":
      case "310013":
      case "310590":
      case "310890":
      case "310910":
      case "311110":
      case "311270":
      case "311271":
      case "311272":
      case "311273":
      case "311274":
      case "311275":
      case "311276":
      case "311277":
      case "311278":
      case "311279":
      case "311280":
      case "311281":
      case "311282":
      case "311283":
      case "311284":
      case "311285":
      case "311286":
      case "311287":
      case "311288":
      case "311289":
      case "311390":
      case "311480":
      case "311481":
      case "311482":
      case "311483":
      case "311484":
      case "311485":
      case "311486":
      case "311487":
      case "311488":
      case "311489":
        return true;
      default:
        return false;
    }
  }

  /**
   * Gets the label to display for a phone call where the presentation is set as
   * PRESENTATION_RESTRICTED. For Verizon we want this to be displayed as "Restricted". For all
   * other carriers we want this to be be displayed as "Private number".
   */
  public static String getDisplayNameForRestrictedNumber(Context context) {
    if (isVerizon(context)) {
      return context.getString(R.string.private_num_verizon);
    } else {
      return context.getString(R.string.private_num_non_verizon);
    }
  }
}
