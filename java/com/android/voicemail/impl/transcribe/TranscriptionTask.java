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

import android.annotation.TargetApi;
import android.app.job.JobWorkItem;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.transcribe.TranscriptionService.JobCallback;
import com.android.voicemail.impl.transcribe.grpc.TranscriptionClient;
import com.android.voicemail.impl.transcribe.grpc.TranscriptionClientFactory;
import com.google.internal.communications.voicemailtranscription.v1.AudioFormat;
import com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailRequest;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import java.io.IOException;
import java.io.InputStream;

/**
 * Background task to get a voicemail transcription and update the database.
 *
 * <pre>
 * This task performs the following steps:
 *   1. Update the transcription-state in the database to 'in-progress'
 *   2. Create grpc client and transcription request
 *   3. Make synchronous grpc transcription request to backend server
 *     3a. On response
 *       Update the database with transcription (if successful) and new transcription-state
 *     3b. On network error
 *       If retry-count < max then increment retry-count and retry the request
 *       Otherwise update the transcription-state in the database to 'transcription-failed'
 *   4. Notify the callback that the work item is complete
 * </pre>
 */
public class TranscriptionTask implements Runnable {
  private static final String TAG = "TranscriptionTask";

  private final Context context;
  private final JobCallback callback;
  private final JobWorkItem workItem;
  private final TranscriptionClientFactory clientFactory;
  private final Uri voicemailUri;
  private final TranscriptionDbHelper databaseHelper;
  private ByteString audioData;
  private AudioFormat encoding;

  private static final int MAX_RETRIES = 2;
  static final String AMR_PREFIX = "#!AMR\n";

  public TranscriptionTask(
      Context context,
      JobCallback callback,
      JobWorkItem workItem,
      TranscriptionClientFactory clientFactory) {
    this.context = context;
    this.callback = callback;
    this.workItem = workItem;
    this.clientFactory = clientFactory;
    this.voicemailUri = getVoicemailUri(workItem);
    databaseHelper = new TranscriptionDbHelper(context, voicemailUri);
  }

  @Override
  public void run() {
    VvmLog.i(TAG, "run");
    if (readAndValidateAudioFile()) {
      updateTranscriptionState(VoicemailCompat.TRANSCRIPTION_IN_PROGRESS);
      transcribeVoicemail();
    } else {
      updateTranscriptionState(VoicemailCompat.TRANSCRIPTION_FAILED);
    }
    ThreadUtil.postOnUiThread(
        () -> {
          callback.onWorkCompleted(workItem);
        });
  }

  private void transcribeVoicemail() {
    VvmLog.i(TAG, "transcribeVoicemail");
    TranscribeVoicemailRequest request = makeRequest();
    TranscriptionClient client = clientFactory.getClient();
    String transcript = null;
    for (int i = 0; transcript == null && i < MAX_RETRIES; i++) {
      VvmLog.i(TAG, "transcribeVoicemail, try: " + (i + 1));
      if (i == 0) {
        Logger.get(context).logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_REQUEST_SENT);
      } else {
        Logger.get(context).logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_REQUEST_RETRY);
      }
      TranscriptionClient.TranscriptionResponseWrapper responseWrapper =
          client.transcribeVoicemail(request);
      if (responseWrapper.status != null) {
        VvmLog.i(TAG, "transcribeVoicemail, status: " + responseWrapper.status.getCode());
        if (shouldRetryRequest(responseWrapper.status)) {
          Logger.get(context)
              .logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_RESPONSE_RECOVERABLE_ERROR);
          backoff(i);
        } else {
          Logger.get(context)
              .logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_RESPONSE_FATAL_ERROR);
          break;
        }
      } else if (responseWrapper.response != null) {
        if (!TextUtils.isEmpty(responseWrapper.response.getTranscript())) {
          VvmLog.i(TAG, "transcribeVoicemail, got response");
          transcript = responseWrapper.response.getTranscript();
          Logger.get(context)
              .logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_RESPONSE_SUCCESS);
        } else {
          VvmLog.i(TAG, "transcribeVoicemail, empty transcription");
          Logger.get(context).logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_RESPONSE_EMPTY);
        }
      } else {
        VvmLog.w(TAG, "transcribeVoicemail, no response");
        Logger.get(context).logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_RESPONSE_INVALID);
      }
    }

    int newState =
        (transcript == null)
            ? VoicemailCompat.TRANSCRIPTION_FAILED
            : VoicemailCompat.TRANSCRIPTION_AVAILABLE;
    updateTranscriptionAndState(transcript, newState);
  }

  private static boolean shouldRetryRequest(Status status) {
    return status.getCode() == Status.Code.UNAVAILABLE;
  }

  private static void backoff(int retryCount) {
    VvmLog.i(TAG, "backoff, count: " + retryCount);
    try {
      long millis = (1 << retryCount) * 1000;
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      VvmLog.w(TAG, "interrupted");
      Thread.currentThread().interrupt();
    }
  }

  private void updateTranscriptionAndState(String transcript, int newState) {
    databaseHelper.setTranscriptionAndState(transcript, newState);
  }

  private void updateTranscriptionState(int newState) {
    databaseHelper.setTranscriptionState(newState);
  }

  private TranscribeVoicemailRequest makeRequest() {
    return TranscribeVoicemailRequest.newBuilder()
        .setVoicemailData(audioData)
        .setAudioFormat(encoding)
        .build();
  }

  // Uses try-with-resource
  @TargetApi(android.os.Build.VERSION_CODES.M)
  private boolean readAndValidateAudioFile() {
    if (voicemailUri == null) {
      VvmLog.i(TAG, "Transcriber.readAndValidateAudioFile, file not found.");
      return false;
    } else {
      VvmLog.i(TAG, "Transcriber.readAndValidateAudioFile, reading: " + voicemailUri);
    }

    try (InputStream in = context.getContentResolver().openInputStream(voicemailUri)) {
      audioData = ByteString.readFrom(in);
      VvmLog.i(TAG, "Transcriber.readAndValidateAudioFile, read " + audioData.size() + " bytes");
    } catch (IOException e) {
      VvmLog.e(TAG, "Transcriber.readAndValidateAudioFile", e);
      return false;
    }

    if (audioData.startsWith(ByteString.copyFromUtf8(AMR_PREFIX))) {
      encoding = AudioFormat.AMR_NB_8KHZ;
    } else {
      VvmLog.i(TAG, "Transcriber.readAndValidateAudioFile, unknown encoding");
      encoding = AudioFormat.AUDIO_FORMAT_UNSPECIFIED;
      return false;
    }

    return true;
  }

  private static Uri getVoicemailUri(JobWorkItem workItem) {
    return workItem.getIntent().getParcelableExtra(TranscriptionService.EXTRA_VOICEMAIL_URI);
  }
}
