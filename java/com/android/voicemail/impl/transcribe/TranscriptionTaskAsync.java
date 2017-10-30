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
import com.android.dialer.common.Assert;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.voicemail.VoicemailComponent;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.transcribe.TranscriptionService.JobCallback;
import com.android.voicemail.impl.transcribe.grpc.GetTranscriptResponseAsync;
import com.android.voicemail.impl.transcribe.grpc.TranscriptionClientFactory;
import com.android.voicemail.impl.transcribe.grpc.TranscriptionResponseAsync;
import com.google.internal.communications.voicemailtranscription.v1.DonationPreference;
import com.google.internal.communications.voicemailtranscription.v1.GetTranscriptRequest;
import com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailAsyncRequest;
import com.google.internal.communications.voicemailtranscription.v1.TranscriptionStatus;

/**
 * Background task to get a voicemail transcription using the asynchronous API. The async API works
 * as follows:
 *
 * <ol>
 *   <li>client uploads voicemail data to the server
 *   <li>server responds with a transcription-id and an estimated transcription wait time
 *   <li>client waits appropriate amount of time then begins polling for the result
 * </ol>
 *
 * This implementation blocks until the response or an error is received, even though it is using
 * the asynchronous server API.
 */
public class TranscriptionTaskAsync extends TranscriptionTask {
  private static final String TAG = "TranscriptionTaskAsync";

  public TranscriptionTaskAsync(
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

    TranscriptionResponseAsync uploadResponse =
        (TranscriptionResponseAsync)
            sendRequest((client) -> client.sendUploadRequest(getUploadRequest()));

    if (cancelled) {
      VvmLog.i(TAG, "getTranscription, cancelled.");
      return new Pair<>(null, TranscriptionStatus.FAILED_NO_RETRY);
    } else if (uploadResponse == null) {
      VvmLog.i(TAG, "getTranscription, failed to upload voicemail.");
      return new Pair<>(null, TranscriptionStatus.FAILED_NO_RETRY);
    } else {
      waitForTranscription(uploadResponse);
      return pollForTranscription(uploadResponse);
    }
  }

  @Override
  protected DialerImpression.Type getRequestSentImpression() {
    return DialerImpression.Type.VVM_TRANSCRIPTION_REQUEST_SENT_ASYNC;
  }

  private static void waitForTranscription(TranscriptionResponseAsync uploadResponse) {
    long millis = uploadResponse.getEstimatedWaitMillis();
    VvmLog.i(TAG, "waitForTranscription, " + millis + " millis");
    sleep(millis);
  }

  private Pair<String, TranscriptionStatus> pollForTranscription(
      TranscriptionResponseAsync uploadResponse) {
    VvmLog.i(TAG, "pollForTranscription");
    GetTranscriptRequest request = getGetTranscriptRequest(uploadResponse);
    for (int i = 0; i < configProvider.getMaxGetTranscriptPolls(); i++) {
      if (cancelled) {
        VvmLog.i(TAG, "pollForTranscription, cancelled.");
        return new Pair<>(null, TranscriptionStatus.FAILED_NO_RETRY);
      }
      Logger.get(context).logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_POLL_REQUEST);
      GetTranscriptResponseAsync response =
          (GetTranscriptResponseAsync)
              sendRequest((client) -> client.sendGetTranscriptRequest(request));
      if (cancelled) {
        VvmLog.i(TAG, "pollForTranscription, cancelled.");
        return new Pair<>(null, TranscriptionStatus.FAILED_NO_RETRY);
      } else if (response == null) {
        VvmLog.i(TAG, "pollForTranscription, no transcription result.");
      } else if (response.isTranscribing()) {
        VvmLog.i(TAG, "pollForTranscription, poll count: " + (i + 1));
      } else if (response.hasFatalError()) {
        VvmLog.i(TAG, "pollForTranscription, fail. " + response.getErrorDescription());
        return new Pair<>(null, response.getTranscriptionStatus());
      } else {
        VvmLog.i(TAG, "pollForTranscription, got transcription");
        return new Pair<>(response.getTranscript(), TranscriptionStatus.SUCCESS);
      }
      sleep(configProvider.getGetTranscriptPollIntervalMillis());
    }
    VvmLog.i(TAG, "pollForTranscription, timed out.");
    return new Pair<>(null, TranscriptionStatus.FAILED_NO_RETRY);
  }

  TranscribeVoicemailAsyncRequest getUploadRequest() {
    return TranscribeVoicemailAsyncRequest.newBuilder()
        .setVoicemailData(audioData)
        .setAudioFormat(encoding)
        .setDonationPreference(
            isDonationEnabled() ? DonationPreference.DONATE : DonationPreference.DO_NOT_DONATE)
        .build();
  }

  private boolean isDonationEnabled() {
    return phoneAccountHandle != null
        && VoicemailComponent.get(context)
            .getVoicemailClient()
            .isVoicemailDonationEnabled(context, phoneAccountHandle);
  }

  private GetTranscriptRequest getGetTranscriptRequest(TranscriptionResponseAsync uploadResponse) {
    Assert.checkArgument(uploadResponse.getTranscriptionId() != null);
    return GetTranscriptRequest.newBuilder()
        .setTranscriptionId(uploadResponse.getTranscriptionId())
        .build();
  }
}
