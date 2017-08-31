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

import android.app.job.JobWorkItem;
import android.content.Context;
import android.util.Pair;
import com.android.dialer.logging.DialerImpression;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.transcribe.TranscriptionService.JobCallback;
import com.android.voicemail.impl.transcribe.grpc.TranscriptionClientFactory;
import com.android.voicemail.impl.transcribe.grpc.TranscriptionResponseSync;
import com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailRequest;
import com.google.internal.communications.voicemailtranscription.v1.TranscriptionStatus;

/** Background task to get a voicemail transcription using the synchronous API */
public class TranscriptionTaskSync extends TranscriptionTask {
  private static final String TAG = "TranscriptionTaskSync";

  public TranscriptionTaskSync(
      Context context,
      JobCallback callback,
      JobWorkItem workItem,
      TranscriptionClientFactory clientFactory,
      TranscriptionConfigProvider configProvider) {
    super(context, callback, workItem, clientFactory, configProvider);
  }

  @Override
  protected Pair<String, TranscriptionStatus> getTranscription() {
    VvmLog.i(TAG, "getTranscription");

    TranscriptionResponseSync response =
        (TranscriptionResponseSync)
            sendRequest((client) -> client.sendSyncRequest(getSyncRequest()));
    if (response == null) {
      VvmLog.i(TAG, "getTranscription, failed to transcribe voicemail.");
      return new Pair<>(null, TranscriptionStatus.FAILED_NO_RETRY);
    } else {
      VvmLog.i(TAG, "getTranscription, got transcription");
      return new Pair<>(response.getTranscript(), TranscriptionStatus.SUCCESS);
    }
  }

  @Override
  protected DialerImpression.Type getRequestSentImpression() {
    return DialerImpression.Type.VVM_TRANSCRIPTION_REQUEST_SENT;
  }

  private TranscribeVoicemailRequest getSyncRequest() {
    return TranscribeVoicemailRequest.newBuilder()
        .setVoicemailData(audioData)
        .setAudioFormat(encoding)
        .build();
  }
}
