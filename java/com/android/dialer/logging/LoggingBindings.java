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
}
