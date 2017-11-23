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

import android.support.annotation.WorkerThread;
import com.google.internal.communications.voicemailtranscription.v1.GetTranscriptRequest;
import com.google.internal.communications.voicemailtranscription.v1.SendTranscriptionFeedbackRequest;
import com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailAsyncRequest;
import com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailRequest;
import com.google.internal.communications.voicemailtranscription.v1.VoicemailTranscriptionServiceGrpc;
import io.grpc.StatusRuntimeException;

/** Wrapper around Grpc transcription server stub */
public class TranscriptionClient {

  private final VoicemailTranscriptionServiceGrpc.VoicemailTranscriptionServiceBlockingStub stub;

  TranscriptionClient(
      VoicemailTranscriptionServiceGrpc.VoicemailTranscriptionServiceBlockingStub stub) {
    this.stub = stub;
  }

  @WorkerThread
  public TranscriptionResponseSync sendSyncRequest(TranscribeVoicemailRequest request) {
    try {
      return new TranscriptionResponseSync(stub.transcribeVoicemail(request));
    } catch (StatusRuntimeException e) {
      return new TranscriptionResponseSync(e.getStatus());
    }
  }

  @WorkerThread
  public TranscriptionResponseAsync sendUploadRequest(TranscribeVoicemailAsyncRequest request) {
    try {
      return new TranscriptionResponseAsync(stub.transcribeVoicemailAsync(request));
    } catch (StatusRuntimeException e) {
      return new TranscriptionResponseAsync(e.getStatus());
    }
  }

  @WorkerThread
  public GetTranscriptResponseAsync sendGetTranscriptRequest(GetTranscriptRequest request) {
    try {
      return new GetTranscriptResponseAsync(stub.getTranscript(request));
    } catch (StatusRuntimeException e) {
      return new GetTranscriptResponseAsync(e.getStatus());
    }
  }

  @WorkerThread
  public TranscriptionFeedbackResponseAsync sendTranscriptFeedbackRequest(
      SendTranscriptionFeedbackRequest request) {
    try {
      return new TranscriptionFeedbackResponseAsync(stub.sendTranscriptionFeedback(request));
    } catch (StatusRuntimeException e) {
      return new TranscriptionFeedbackResponseAsync(e.getStatus());
    }
  }
}
