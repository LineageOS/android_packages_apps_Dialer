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

/** Default implementation for logging bindings. */
public class LoggingBindingsStub implements LoggingBindings {

  @Override
  public void logImpression(DialerImpression.Type dialerImpression) {}

  @Override
  public void logImpression(int dialerImpression) {}

  @Override
  public void logCallImpression(
      DialerImpression.Type dialerImpression, String callId, long callStartTimeMillis) {}

  @Override
  public void logInteraction(InteractionEvent.Type interaction) {}

  @Override
  public void logScreenView(ScreenEvent.Type screenEvent, Activity activity) {}

  @Override
  public void logSpeedDialContactComposition(
      int counter,
      int starredContactsCount,
      int pinnedContactsCount,
      int multipleNumbersContactsCount,
      int contactsWithPhotoCount,
      int contactsWithNameCount,
      int lightbringerReachableContactsCount) {}

  @Override
  public void sendHitEventAnalytics(String category, String action, String label, long value) {}

  @Override
  public void logQuickContactOnTouch(
      QuickContactBadge quickContact,
      InteractionEvent.Type interactionEvent,
      boolean shouldPerformClick) {}
}
