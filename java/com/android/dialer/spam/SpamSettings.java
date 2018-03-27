/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.content.Context;
import android.content.Intent;

/** Allows the container application to interact with spam settings. */
public interface SpamSettings {

  /** @return if spam module is enabled */
  boolean isSpamEnabled();

  /** @return if spam after call notification is enabled */
  boolean isSpamNotificationEnabled();

  /** @return if spam blocking is enabled */
  boolean isSpamBlockingEnabled();

  /** @return if spam blocking user setting is controlled by carrier */
  boolean isSpamBlockingControlledByCarrier();

  /** @return if spam blocking module is enabled by flag */
  boolean isSpamBlockingEnabledByFlag();

  /** @return if spam blocking setting is enabled by user */
  boolean isSpamBlockingEnabledByUser();

  /** @return if dialog is used by default for spam after call notification */
  boolean isDialogEnabledForSpamNotification();

  /** @return if report spam is checked by default in block/report dialog */
  boolean isDialogReportSpamCheckedByDefault();

  /** @return percentage of after call notifications for spam numbers to show to the user */
  int percentOfSpamNotificationsToShow();

  /** @return percentage of after call notifications for nonspam numbers to show to the user */
  int percentOfNonSpamNotificationsToShow();

  /**
   * Modifies spam blocking setting.
   *
   * @param enabled Whether to enable or disable the setting.
   * @param listener The callback to be invoked after setting change is done.
   */
  void modifySpamBlockingSetting(boolean enabled, ModifySettingListener listener);

  /** @return an intent to start spam blocking setting */
  Intent getSpamBlockingSettingIntent(Context context);

  /** Callback to be invoked when setting change completes. */
  interface ModifySettingListener {

    /** Called when setting change completes. */
    void onComplete(boolean success);
  }
}
