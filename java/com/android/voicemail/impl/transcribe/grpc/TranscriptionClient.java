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
import android.support.annotation.WorkerThread;
import com.android.dialer.common.Assert;
import com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailRequest;
import com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailResponse;
import com.google.internal.communications.voicemailtranscription.v1.VoicemailTranscriptionServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/** Wrapper around Grpc transcription server stub */
public class TranscriptionClient {

  private final VoicemailTranscriptionServiceGrpc.VoicemailTranscriptionServiceBlockingStub stub;

  /** Wraps the server response and status objects, either of which may be null. */
  public static class TranscriptionResponseWrapper {
    public final TranscribeVoicemailResponse response;
    public final Status status;

    public TranscriptionResponseWrapper(
        @Nullable TranscribeVoicemailResponse response, @Nullable Status status) {
      Assert.checkArgument(!(response == null && status == null));
      this.response = response;
      this.status = status;
    }
  }

  TranscriptionClient(
      VoicemailTranscriptionServiceGrpc.VoicemailTranscriptionServiceBlockingStub stub) {
    this.stub = stub;
  }

  @WorkerThread
  public TranscriptionResponseWrapper transcribeVoicemail(TranscribeVoicemailRequest request) {
    TranscribeVoicemailResponse response = null;
    Status status = null;
    try {
      response = stub.transcribeVoicemail(request);
    } catch (StatusRuntimeException e) {
      status = e.getStatus();
    }
    return new TranscriptionClient.TranscriptionResponseWrapper(response, status);
  }
}
