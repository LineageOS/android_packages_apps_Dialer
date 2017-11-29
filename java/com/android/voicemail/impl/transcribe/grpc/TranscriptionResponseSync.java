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
import com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailResponse;
import io.grpc.Status;

/** Container for response and status objects for a synchronous transcription request */
public class TranscriptionResponseSync extends TranscriptionResponse {
  @Nullable private final TranscribeVoicemailResponse response;

  @VisibleForTesting
  public TranscriptionResponseSync(Status status) {
    super(status);
    this.response = null;
  }

  @VisibleForTesting
  public TranscriptionResponseSync(TranscribeVoicemailResponse response) {
    Assert.checkArgument(response != null);
    this.response = response;
  }

  public @Nullable String getTranscript() {
    return (response != null) ? response.getTranscript() : null;
  }

  @Override
  public String toString() {
    return super.toString() + ", response: " + response;
  }
}
