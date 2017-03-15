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
 * limitations under the License
 */

package com.android.voicemail.impl;

import android.annotation.TargetApi;
import android.os.Build.VERSION_CODES;

/**
 * Temporary stub for public APIs that should be added into telephony manager.
 *
 * <p>TODO(b/32637799) remove this.
 */
@TargetApi(VERSION_CODES.O)
public class TelephonyManagerStub {

  public static void showVoicemailNotification(int voicemailCount) {}

  /**
   * Dismisses the message waiting (voicemail) indicator.
   *
   * @param subId the subscription id we should dismiss the notification for.
   */
  public static void clearMwiIndicator(int subId) {}

  public static void setShouldCheckVisualVoicemailConfigurationForMwi(int subId, boolean enabled) {}
}
