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
import java.util.concurrent.TimeUnit;

/** Provides configuration values needed to connect to the transcription server. */
public class TranscriptionConfigProvider {
  private final Context context;

  public TranscriptionConfigProvider(Context context) {
    this.context = context;
  }

  public boolean isVoicemailTranscriptionAvailable() {
    return false;
  }

  public String getServerAddress() {
    // Private voicemail transcription service
    return "";
  }

  public String getApiKey() {
    // Android API key restricted to com.google.android.dialer
    return "";
  }

  public String getAuthToken() {
    return null;
  }

  public boolean shouldUsePlaintext() {
    return false;
  }

  public boolean shouldUseSyncApi() {
    return false;
  }

  public long getMaxTranscriptionRetries() {
    return 2L;
  }

  public int getMaxGetTranscriptPolls() {
    return 20;
  }

  public long getInitialGetTranscriptPollDelayMillis() {
    return TimeUnit.SECONDS.toMillis(1);
  }

  public long getMaxGetTranscriptPollTimeMillis() {
    return TimeUnit.MINUTES.toMillis(20);
  }

  public boolean isVoicemailDonationAvailable() {
    return false;
  }

  public boolean useClientGeneratedVoicemailIds() {
    return false;
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
        getMaxGetTranscriptPollTimeMillis());
  }
}
