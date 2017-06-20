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
package com.android.voicemail.impl.transcribe;

import android.content.Context;
import com.android.dialer.configprovider.ConfigProviderBindings;

/** Provides configuration values needed to connect to the transcription server. */
public class TranscriptionConfigProvider {
  private final Context context;

  public TranscriptionConfigProvider(Context context) {
    this.context = context;
  }

  public boolean isVoicemailTranscriptionEnabled() {
    return ConfigProviderBindings.get(context).getBoolean("voicemail_transcription_enabled", false);
  }

  public String getServerAddress() {
    // Private voicemail transcription service
    return ConfigProviderBindings.get(context)
        .getString(
            "voicemail_transcription_server_address", "voicemailtranscription-pa.googleapis.com");
  }

  public String getApiKey() {
    // Android API key restricted to com.google.android.dialer
    return ConfigProviderBindings.get(context)
        .getString(
            "voicemail_transcription_client_api_key", "AIzaSyAXdDnif6B7sBYxU8hzw9qAp3pRPVHs060");
  }

  public String getAuthToken() {
    return null;
  }

  public boolean shouldUsePlaintext() {
    return ConfigProviderBindings.get(context)
        .getBoolean("voicemail_transcription_server_use_plaintext", false);
  }

  @Override
  public String toString() {
    return String.format(
        "{ address: %s, api key: %s, auth token: %s, plaintext: %b }",
        getServerAddress(), getApiKey(), getAuthToken(), shouldUsePlaintext());
  }
}
