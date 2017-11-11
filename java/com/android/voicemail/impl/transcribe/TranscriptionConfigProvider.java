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
import android.os.Build;
import com.android.dialer.configprovider.ConfigProviderBindings;

/** Provides configuration values needed to connect to the transcription server. */
public class TranscriptionConfigProvider {
  private final Context context;

  public TranscriptionConfigProvider(Context context) {
    this.context = context;
  }

  public boolean isVoicemailTranscriptionEnabled() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        && ConfigProviderBindings.get(context).getBoolean("voicemail_transcription_enabled", false);
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

  public boolean shouldUseSyncApi() {
    return ConfigProviderBindings.get(context)
        .getBoolean("voicemail_transcription_server_use_sync_api", false);
  }

  public long getMaxTranscriptionRetries() {
    return ConfigProviderBindings.get(context)
        .getLong("voicemail_transcription_max_transcription_retries", 2L);
  }

  public long getMaxGetTranscriptPolls() {
    return ConfigProviderBindings.get(context)
        .getLong("voicemail_transcription_max_get_transcript_polls", 20L);
  }

  public long getGetTranscriptPollIntervalMillis() {
    return ConfigProviderBindings.get(context)
        .getLong("voicemail_transcription_get_transcript_poll_interval_millis", 1000L);
  }

  public boolean isVoicemailDonationAvailable() {
    return ConfigProviderBindings.get(context)
        .getBoolean("voicemail_transcription_donation_available", false);
  }

  @Override
  public String toString() {
    return String.format(
        "{ address: %s, api key: %s, auth token: %s, plaintext: %b, sync: %b, retries: %d, polls:"
            + " %d, poll ms: %d }",
        getServerAddress(),
        getApiKey(),
        getAuthToken(),
        shouldUsePlaintext(),
        shouldUseSyncApi(),
        getMaxTranscriptionRetries(),
        getMaxGetTranscriptPolls(),
        getGetTranscriptPollIntervalMillis());
  }
}
