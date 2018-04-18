/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.dialer.logging;

import android.app.Activity;
import android.widget.QuickContactBadge;
import com.google.auto.value.AutoValue;
import java.util.Collection;

/** Allows the container application to gather analytics. */
public interface LoggingBindings {

  /**
   * Logs an DialerImpression event that's not associated with a specific call.
   *
   * @param dialerImpression an integer representing what event occurred.
   */
  void logImpression(DialerImpression.Type dialerImpression);

  /**
   * Logs an impression for a general dialer event that's not associated with a specific call.
   *
   * @param dialerImpression an integer representing what event occurred.
   */
  @Deprecated
  void logImpression(int dialerImpression);

  /**
   * Logs an impression for a general dialer event that's associated with a specific call.
   *
   * @param dialerImpression an integer representing what event occurred.
   * @param callId unique ID of the call.
   * @param callStartTimeMillis the absolute time when the call started.
   */
  void logCallImpression(
      DialerImpression.Type dialerImpression, String callId, long callStartTimeMillis);

  /**
   * Logs an interaction that occurred.
   *
   * @param interaction an integer representing what interaction occurred.
   * @see com.android.dialer.logging.InteractionEvent
   */
  void logInteraction(InteractionEvent.Type interaction);

  /**
   * Logs an event indicating that a screen was displayed.
   *
   * @param screenEvent an integer representing the displayed screen.
   * @param activity Parent activity of the displayed screen.
   * @see com.android.dialer.logging.ScreenEvent
   */
  void logScreenView(com.android.dialer.logging.ScreenEvent.Type screenEvent, Activity activity);

  /** Logs the composition of contact tiles in the speed dial tab. */
  void logSpeedDialContactComposition(
      int counter,
      int starredContactsCount,
      int pinnedContactsCount,
      int multipleNumbersContactsCount,
      int contactsWithPhotoCount,
      int contactsWithNameCount,
      int lightbringerReachableContactsCount);

  /** Logs a hit event to the analytics server. */
  void sendHitEventAnalytics(String category, String action, String label, long value);

  /** Logs where a quick contact badge is clicked */
  void logQuickContactOnTouch(
      QuickContactBadge quickContact,
      InteractionEvent.Type interactionEvent,
      boolean shouldPerformClick);

  /** Logs People Api lookup result with error */
  void logPeopleApiLookupReportWithError(
      long latency, int httpResponseCode, PeopleApiLookupError.Type errorType);

  /** Logs successful People Api lookup result */
  void logSuccessfulPeopleApiLookupReport(long latency, int httpResponseCode);

  /** Logs a call auto-blocked in call screening. */
  void logAutoBlockedCall(String phoneNumber);

  /** Logs annotated call log metrics. */
  void logAnnotatedCallLogMetrics(int invalidNumbersInCallLog);

  /** Logs annotated call log metrics. */
  void logAnnotatedCallLogMetrics(int numberRowsThatDidPop, int numberRowsThatDidNotPop);

  /** Logs contacts provider metrics. */
  void logContactsProviderMetrics(Collection<ContactsProviderMatchInfo> matchInfos);

  /** Input type for {@link #logContactsProviderMetrics(Collection)}. */
  @AutoValue
  abstract class ContactsProviderMatchInfo {
    public abstract boolean matchedContact();

    public abstract boolean inputNumberValid();

    public abstract int inputNumberLength();

    public abstract int matchedNumberLength();

    public abstract boolean inputNumberHasPostdialDigits();

    public abstract boolean matchedNumberHasPostdialDigits();

    public static Builder builder() {
      return new AutoValue_LoggingBindings_ContactsProviderMatchInfo.Builder()
          .setMatchedContact(false)
          .setMatchedNumberLength(0)
          .setMatchedNumberHasPostdialDigits(false);
    }

    /** Builder. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setMatchedContact(boolean value);

      public abstract Builder setInputNumberValid(boolean value);

      public abstract Builder setInputNumberLength(int value);

      public abstract Builder setMatchedNumberLength(int value);

      public abstract Builder setInputNumberHasPostdialDigits(boolean value);

      public abstract Builder setMatchedNumberHasPostdialDigits(boolean value);

      public abstract ContactsProviderMatchInfo build();
    }
  }
}
