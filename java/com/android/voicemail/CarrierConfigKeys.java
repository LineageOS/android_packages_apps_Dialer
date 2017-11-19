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
 * Keys used to lookup carrier specific configuration strings. See {@code
 * VoicemailClient.getCarrierConfigString}
 */
public interface CarrierConfigKeys {

  /**
   * Carrier config key whose value will be 'true' for carriers that allow over the top voicemail
   * transcription.
   */
  String VVM_CARRIER_ALLOWS_OTT_TRANSCRIPTION_STRING =
      "vvm_carrier_allows_ott_transcription_string";
}
