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
package com.android.voicemail.impl.transcribe.grpc;

import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import com.android.dialer.common.Assert;
import com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailAsyncResponse;
import io.grpc.Status;

/** Container for response and status objects for an asynchronous transcription upload request */
public class TranscriptionResponseAsync extends TranscriptionResponse {
  @Nullable private final TranscribeVoicemailAsyncResponse response;

  @VisibleForTesting
  public TranscriptionResponseAsync(TranscribeVoicemailAsyncResponse response) {
    Assert.checkArgument(response != null);
    this.response = response;
  }

  @VisibleForTesting
  public TranscriptionResponseAsync(Status status) {
    super(status);
    this.response = null;
  }

  public @Nullable String getTranscriptionId() {
    if (response != null) {
      return response.getTranscriptionId();
    }
    return null;
  }

  public long getEstimatedWaitMillis() {
    if (response != null) {
      return response.getEstimatedWaitSecs() * 1_000L;
    }
    return 0;
  }

  @Override
  public String toString() {
    return super.toString() + ", response: " + response;
  }
}
