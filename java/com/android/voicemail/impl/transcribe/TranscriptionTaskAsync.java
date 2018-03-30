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
import android.support.annotation.VisibleForTesting;
import android.util.Pair;
import com.android.dialer.logging.DialerImpression;
import com.android.voicemail.VoicemailComponent;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.transcribe.TranscriptionService.JobCallback;
import com.android.voicemail.impl.transcribe.grpc.TranscriptionClientFactory;
import com.android.voicemail.impl.transcribe.grpc.TranscriptionResponseAsync;
import com.google.internal.communications.voicemailtranscription.v1.DonationPreference;
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

    if (GetTranscriptReceiver.hasPendingAlarm(context)) {
      // Don't start a transcription while another is still active
      VvmLog.i(
          TAG,
          "getTranscription, pending transcription, postponing transcription of: " + voicemailUri);
      return new Pair<>(null, null);
    }

    TranscribeVoicemailAsyncRequest uploadRequest = getUploadRequest();
    VvmLog.i(
        TAG,
        "getTranscription, uploading voicemail: "
            + voicemailUri
            + ", id: "
            + uploadRequest.getTranscriptionId());
    TranscriptionResponseAsync uploadResponse =
        (TranscriptionResponseAsync)
            sendRequest((client) -> client.sendUploadRequest(uploadRequest));

    if (cancelled) {
      VvmLog.i(TAG, "getTranscription, cancelled.");
      return new Pair<>(null, TranscriptionStatus.FAILED_NO_RETRY);
    } else if (uploadResponse == null) {
      VvmLog.i(TAG, "getTranscription, failed to upload voicemail.");
      return new Pair<>(null, TranscriptionStatus.FAILED_NO_RETRY);
    } else if (uploadResponse.isStatusAlreadyExists()) {
      VvmLog.i(TAG, "getTranscription, transcription already exists.");
      GetTranscriptReceiver.beginPolling(
          context,
          voicemailUri,
          uploadRequest.getTranscriptionId(),
          0,
          configProvider,
          phoneAccountHandle);
      return new Pair<>(null, null);
    } else if (uploadResponse.getTranscriptionId() == null) {
      VvmLog.i(TAG, "getTranscription, upload error: " + uploadResponse.status);
      return new Pair<>(null, TranscriptionStatus.FAILED_NO_RETRY);
    } else {
      VvmLog.i(TAG, "getTranscription, begin polling for: " + uploadResponse.getTranscriptionId());
      GetTranscriptReceiver.beginPolling(
          context,
          voicemailUri,
          uploadResponse.getTranscriptionId(),
          uploadResponse.getEstimatedWaitMillis(),
          configProvider,
          phoneAccountHandle);
      // This indicates that the result is not available yet
      return new Pair<>(null, null);
    }
  }

  @Override
  protected DialerImpression.Type getRequestSentImpression() {
    return DialerImpression.Type.VVM_TRANSCRIPTION_REQUEST_SENT_ASYNC;
  }

  @VisibleForTesting
  TranscribeVoicemailAsyncRequest getUploadRequest() {
    TranscribeVoicemailAsyncRequest.Builder builder =
        TranscribeVoicemailAsyncRequest.newBuilder()
            .setVoicemailData(audioData)
            .setAudioFormat(encoding)
            .setDonationPreference(
                isDonationEnabled() ? DonationPreference.DONATE : DonationPreference.DO_NOT_DONATE);
    // Generate the transcript id locally if configured to do so, or if voicemail donation is
    // available (because rating donating voicemails requires locally generated voicemail ids).
    if (configProvider.useClientGeneratedVoicemailIds()
        || VoicemailComponent.get(context)
            .getVoicemailClient()
            .isVoicemailDonationAvailable(context, phoneAccountHandle)) {
      // The server currently can't handle repeated transcription id's so if we add the Uri to the
      // fingerprint (which contains the voicemail id) which is different each time a voicemail is
      // downloaded.  If this becomes a problem then it should be possible to change the server
      // behavior to allow id's to be re-used, a bug
      String salt = voicemailUri.toString();
      builder.setTranscriptionId(TranscriptionUtils.getFingerprintFor(audioData, salt));
    }
    return builder.build();
  }

  private boolean isDonationEnabled() {
    return phoneAccountHandle != null
        && VoicemailComponent.get(context)
            .getVoicemailClient()
            .isVoicemailDonationEnabled(context, phoneAccountHandle);
  }
}
