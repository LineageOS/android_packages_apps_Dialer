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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.logging.ContactLookupResult;
import com.android.dialer.logging.ContactSource;
import com.android.dialer.logging.ReportingLocation;

/** Allows the container application to mark calls as spam. */
public interface SpamBindings {

  boolean isSpamEnabled();

  boolean isSpamNotificationEnabled();

  boolean isDialogEnabledForSpamNotification();

  boolean isDialogReportSpamCheckedByDefault();

  /** @return what percentage of aftercall notifications to show to the user */
  int percentOfSpamNotificationsToShow();

  int percentOfNonSpamNotificationsToShow();

  /**
   * Checks if the given number is suspected of being a spamer.
   *
   * @param number The phone number of the call.
   * @param countryIso The country ISO of the call.
   * @param listener The callback to be invoked after {@code Info} is fetched.
   */
  void checkSpamStatus(String number, String countryIso, Listener listener);

  /**
   * @param number The number to check if the number is in the user's white list (non spam list)
   * @param countryIso The country ISO of the call.
   * @param listener The callback to be invoked after {@code Info} is fetched.
   */
  void checkUserMarkedNonSpamStatus(
      String number, @Nullable String countryIso, @NonNull Listener listener);

  /**
   * @param number The number to check if it is in user's spam list
   * @param countryIso The country ISO of the call.
   * @param listener The callback to be invoked after {@code Info} is fetched.
   */
  void checkUserMarkedSpamStatus(
      String number, @Nullable String countryIso, @NonNull Listener listener);

  /**
   * @param number The number to check if it is in the global spam list
   * @param countryIso The country ISO of the call.
   * @param listener The callback to be invoked after {@code Info} is fetched.
   */
  void checkGlobalSpamListStatus(
      String number, @Nullable String countryIso, @NonNull Listener listener);

  /**
   * Synchronously checks if the given number is suspected of being a spamer.
   *
   * @param number The phone number of the call.
   * @param countryIso The country ISO of the call.
   * @return True if the number is spam.
   */
  boolean checkSpamStatusSynchronous(String number, String countryIso);

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

  /** Callback to be invoked when data is fetched. */
  interface Listener {

    /** Called when data is fetched. */
    void onComplete(boolean isSpam);
  }
}
