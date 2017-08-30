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
import com.google.internal.communications.voicemailtranscription.v1.GetTranscriptResponse;
import com.google.internal.communications.voicemailtranscription.v1.TranscriptionStatus;
import io.grpc.Status;

/** Container for response and status objects for an asynchronous get-transcript request */
public class GetTranscriptResponseAsync extends TranscriptionResponse {
  @Nullable private final GetTranscriptResponse response;

  @VisibleForTesting
  public GetTranscriptResponseAsync(GetTranscriptResponse response) {
    Assert.checkArgument(response != null);
    this.response = response;
  }

  @VisibleForTesting
  public GetTranscriptResponseAsync(Status status) {
    super(status);
    this.response = null;
  }

  public @Nullable String getTranscript() {
    if (response != null) {
      return response.getTranscript();
    }
    return null;
  }

  public @Nullable String getErrorDescription() {
    if (!hasRecoverableError() && !hasFatalError()) {
      return null;
    }
    if (status != null) {
      return "Grpc error: " + status;
    }
    if (response != null) {
      return "Transcription error: " + response.getStatus();
    }
    Assert.fail("Impossible state");
    return null;
  }

  public TranscriptionStatus getTranscriptionStatus() {
    if (response == null) {
      return TranscriptionStatus.TRANSCRIPTION_STATUS_UNSPECIFIED;
    } else {
      return response.getStatus();
    }
  }

  public boolean isTranscribing() {
    return response != null && response.getStatus() == TranscriptionStatus.PENDING;
  }

  @Override
  public boolean hasRecoverableError() {
    if (super.hasRecoverableError()) {
      return true;
    }

    if (response != null) {
      return response.getStatus() == TranscriptionStatus.EXPIRED
          || response.getStatus() == TranscriptionStatus.FAILED_RETRY;
    }

    return false;
  }

  @Override
  public boolean hasFatalError() {
    if (super.hasFatalError()) {
      return true;
    }

    if (response != null) {
      return response.getStatus() == TranscriptionStatus.FAILED_NO_RETRY
          || response.getStatus() == TranscriptionStatus.FAILED_LANGUAGE_NOT_SUPPORTED
          || response.getStatus() == TranscriptionStatus.FAILED_NO_SPEECH_DETECTED;
    }

    return false;
  }
}
