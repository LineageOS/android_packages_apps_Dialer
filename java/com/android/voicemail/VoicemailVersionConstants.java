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

package com.android.voicemail;

/**
 * Shared preference keys and values relating to the voicemail version that the user has accepted.
 * Note: these can be carrier dependent.
 */
public interface VoicemailVersionConstants {
  // Preference key to check which version of the Verizon ToS that the user has accepted.
  String PREF_VVM3_TOS_VERSION_ACCEPTED_KEY = "vvm3_tos_version_accepted";

  // Preference key to check which version of the Google Dialer ToS that the user has accepted.
  String PREF_DIALER_TOS_VERSION_ACCEPTED_KEY = "dialer_tos_version_accepted";

  // Preference key to check which feature version the user has acknowledged
  String PREF_DIALER_FEATURE_VERSION_ACKNOWLEDGED_KEY = "dialer_feature_version_acknowledged";

  int CURRENT_VVM3_TOS_VERSION = 2;
  int CURRENT_DIALER_TOS_VERSION = 1;
  int LEGACY_VOICEMAIL_FEATURE_VERSION = 1; // original visual voicemail
  int TRANSCRIPTION_VOICEMAIL_FEATURE_VERSION = 2;
  int CURRENT_VOICEMAIL_FEATURE_VERSION = TRANSCRIPTION_VOICEMAIL_FEATURE_VERSION;
}
