/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.spam;

import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.provider.CallLog.Calls;
import android.support.annotation.Nullable;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.logging.ContactLookupResult;
import com.android.dialer.logging.ContactSource;
import com.android.dialer.logging.ReportingLocation;
import com.android.dialer.spam.status.SpamStatus;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;

/** Allows the container application to mark calls as spam. */
public interface Spam {

  /**
   * Checks if each of numbers in the given list is suspected of being a spam.
   *
   * @param dialerPhoneNumbers A set of {@link DialerPhoneNumber}.
   * @return A {@link ListenableFuture} of a map that maps each number to its {@link SpamStatus}.
   */
  ListenableFuture<ImmutableMap<DialerPhoneNumber, SpamStatus>> batchCheckSpamStatus(
      ImmutableSet<DialerPhoneNumber> dialerPhoneNumbers);

  /**
   * Checks if the given number is suspected of being spam.
   *
   * @param dialerPhoneNumber the phone number.
   * @return the {@link SpamStatus} for the given number.
   */
  ListenableFuture<SpamStatus> checkSpamStatus(DialerPhoneNumber dialerPhoneNumber);

  /**
   * Checks if the given number is suspected of being spam.
   *
   * <p>See {@link #checkSpamStatus(DialerPhoneNumber)}.
   *
   * @param number the phone number.
   * @param defaultCountryIso the default country to use if it's not part of the number.
   * @return the {@link SpamStatus} for the given number.
   */
  ListenableFuture<SpamStatus> checkSpamStatus(String number, @Nullable String defaultCountryIso);

  /**
   * Called as an indication that the Spam implementation should check whether downloading a spam
   * list needs to occur or not.
   *
   * @param isEnabledByUser true if spam is enabled by the user. Generally, this value should be
   *     passed as {@link SpamSettings#isSpamEnabled()}. In the scenario where the user toggles the
   *     spam setting isSpamEnabled returns stale data: the SharedPreferences will not have updated
   *     prior to executing {@link OnPreferenceChangeListener#onPreferenceChange(Preference,
   *     Object)}. For that case, use the new value provided in the onPreferenceChange callback.
   * @return a future containing no value. It is only an indication of success or failure of the
   *     operation.
   */
  ListenableFuture<Void> updateSpamListDownload(boolean isEnabledByUser);

  /**
   * Synchronously checks if the given number is suspected of being a spamer.
   *
   * @param number The phone number of the call.
   * @param countryIso The country ISO of the call.
   * @return True if the number is spam.
   */
  boolean checkSpamStatusSynchronous(String number, String countryIso);

  /**
   * Returns a {@link ListenableFuture} indicating whether the spam data have been updated since
   * {@code timestampMillis}.
   *
   * <p>It is the caller's responsibility to ensure the timestamp is in milliseconds. Failure to do
   * so will result in undefined behavior.
   */
  ListenableFuture<Boolean> dataUpdatedSince(long timestampMillis);

  /**
   * Reports number as spam.
   *
   * @param number The number to be reported.
   * @param countryIso The country ISO of the number.
   * @param callType Whether the type of call is missed, voicemail, etc. Example of this is {@link
   *     android.provider.CallLog.Calls#VOICEMAIL_TYPE}.
   * @param from Where in the dialer this was reported from. Must be one of {@link
   *     com.android.dialer.logging.ReportingLocation}.
   * @param contactLookupResultType The result of the contact lookup for this phone number. Must be
   *     one of {@link com.android.dialer.logging.ContactLookupResult}.
   */
  void reportSpamFromAfterCallNotification(
      String number,
      String countryIso,
      int callType,
      ReportingLocation.Type from,
      ContactLookupResult.Type contactLookupResultType);

  /**
   * Reports number as spam.
   *
   * @param number The number to be reported.
   * @param countryIso The country ISO of the number.
   * @param callType Whether the type of call is missed, voicemail, etc. Example of this is {@link
   *     android.provider.CallLog.Calls#VOICEMAIL_TYPE}.
   * @param from Where in the dialer this was reported from. Must be one of {@link
   *     com.android.dialer.logging.ReportingLocation}.
   * @param contactSourceType If we have cached contact information for the phone number, this
   *     indicates its source. Must be one of {@link com.android.dialer.logging.ContactSource}.
   */
  void reportSpamFromCallHistory(
      String number,
      String countryIso,
      int callType,
      ReportingLocation.Type from,
      ContactSource.Type contactSourceType);

  /**
   * Reports number as not spam.
   *
   * @param number The number to be reported.
   * @param countryIso The country ISO of the number.
   * @param callType Whether the type of call is missed, voicemail, etc. Example of this is {@link
   *     android.provider.CallLog.Calls#VOICEMAIL_TYPE}.
   * @param from Where in the dialer this was reported from. Must be one of {@link
   *     com.android.dialer.logging.ReportingLocation}.
   * @param contactLookupResultType The result of the contact lookup for this phone number. Must be
   *     one of {@link com.android.dialer.logging.ContactLookupResult}.
   */
  void reportNotSpamFromAfterCallNotification(
      String number,
      String countryIso,
      int callType,
      ReportingLocation.Type from,
      ContactLookupResult.Type contactLookupResultType);

  /**
   * Reports number as not spam.
   *
   * @param number The number to be reported.
   * @param countryIso The country ISO of the number.
   * @param callType Whether the type of call is missed, voicemail, etc. Example of this is {@link
   *     android.provider.CallLog.Calls#VOICEMAIL_TYPE}.
   * @param from Where in the dialer this was reported from. Must be one of {@link
   *     com.android.dialer.logging.ReportingLocation}.
   * @param contactSourceType If we have cached contact information for the phone number, this
   *     indicates its source. Must be one of {@link com.android.dialer.logging.ContactSource}.
   */
  void reportNotSpamFromCallHistory(
      String number,
      String countryIso,
      int callType,
      ReportingLocation.Type from,
      ContactSource.Type contactSourceType);

  /**
   * Given a number's spam status and a call type, determine if the call should be shown as spam.
   *
   * <p>We show a call as spam if
   *
   * <ul>
   *   <li>the number is marked as spam, and
   *   <li>the call is not an outgoing call.
   * </ul>
   *
   * <p>This is because spammers can hide behind a legit number (e.g., a customer service number).
   * We don't want to show a spam icon when users call it.
   *
   * @param isNumberSpam Whether the number is spam.
   * @param callType One of the types in {@link android.provider.CallLog.Calls#TYPE}.
   * @return true if the number is spam *and* the call is not an outgoing call.
   */
  static boolean shouldShowAsSpam(boolean isNumberSpam, int callType) {
    return isNumberSpam && (callType != Calls.OUTGOING_TYPE);
  }
}
