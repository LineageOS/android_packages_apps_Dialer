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

  public TranscriptionConfigProvider(Context context) {
  }

  public String getServerAddress() {
    // Private voicemail transcription service
    return "voicemailtranscription-pa.googleapis.com";
  }

  public String getApiKey() {
    // Android API key restricted to com.google.android.dialer
    return "AIzaSyAXdDnif6B7sBYxU8hzw9qAp3pRPVHs060";
  }

  public String getAuthToken() {
    return null;
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

  @Override
  public String toString() {
    return String.format(
        "{ address: %s, api key: %s, auth token: %s, retries: %d, polls:"
            + " %d, poll ms: %d }",
        getServerAddress(),
        getApiKey(),
        getAuthToken(),
        getMaxTranscriptionRetries(),
        getMaxGetTranscriptPolls(),
        getMaxGetTranscriptPollTimeMillis());
  }
}
