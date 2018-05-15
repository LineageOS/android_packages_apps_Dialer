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

package com.android.dialer.spam.stub;

import android.content.Context;
import android.content.Intent;
import com.android.dialer.spam.SpamSettings;
import javax.inject.Inject;

/** Default implementation of SpamSettings. */
public class SpamSettingsStub implements SpamSettings {

  @Inject
  public SpamSettingsStub() {}

  @Override
  public boolean isSpamEnabled() {
    return false;
  }

  @Override
  public boolean isSpamNotificationEnabled() {
    return false;
  }

  @Override
  public boolean isSpamBlockingEnabledByFlag() {
    return false;
  }

  @Override
  public boolean isSpamBlockingControlledByCarrier() {
    return false;
  }

  @Override
  public boolean isSpamBlockingEnabled() {
    return false;
  }

  @Override
  public boolean isSpamBlockingEnabledByUser() {
    return false;
  }

  @Override
  public boolean isDialogEnabledForSpamNotification() {
    return false;
  }

  @Override
  public boolean isDialogReportSpamCheckedByDefault() {
    return false;
  }

  @Override
  public int percentOfSpamNotificationsToShow() {
    return 0;
  }

  @Override
  public int percentOfNonSpamNotificationsToShow() {
    return 0;
  }

  @Override
  public void modifySpamBlockingSetting(boolean enabled, ModifySettingListener listener) {
    listener.onComplete(false);
  }

  @Override
  public Intent getSpamBlockingSettingIntent(Context context) {
    return new Intent();
  }
}
