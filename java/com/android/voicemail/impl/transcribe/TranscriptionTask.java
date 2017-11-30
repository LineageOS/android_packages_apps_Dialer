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
import android.net.Uri;
import android.support.annotation.MainThread;
import android.support.annotation.VisibleForTesting;
import android.telecom.PhoneAccountHandle;
import android.util.Pair;
import com.android.dialer.common.Assert;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.compat.android.provider.VoicemailCompat;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.transcribe.TranscriptionService.JobCallback;
import com.android.voicemail.impl.transcribe.grpc.TranscriptionClient;
import com.android.voicemail.impl.transcribe.grpc.TranscriptionClientFactory;
import com.android.voicemail.impl.transcribe.grpc.TranscriptionResponse;
import com.google.internal.communications.voicemailtranscription.v1.AudioFormat;
import com.google.internal.communications.voicemailtranscription.v1.TranscriptionStatus;
import com.google.protobuf.ByteString;

/**
 * Background task to get a voicemail transcription and update the database.
 *
 * <pre>
 * This task performs the following steps:
 *   1. Update the transcription-state in the database to 'in-progress'
 *   2. Create grpc client and transcription request
 *   3. Make synchronous or asynchronous grpc transcription request to backend server
 *     3a. On response
 *       Update the database with transcription (if successful) and new transcription-state
 *     3b. On network error
 *       If retry-count < max then increment retry-count and retry the request
 *       Otherwise update the transcription-state in the database to 'transcription-failed'
 *   4. Notify the callback that the work item is complete
 * </pre>
 */
public abstract class TranscriptionTask implements Runnable {
  private static final String TAG = "TranscriptionTask";

  private final JobCallback callback;
  private final JobWorkItem workItem;
  private final TranscriptionClientFactory clientFactory;
  protected final Context context;
  protected final Uri voicemailUri;
  protected final PhoneAccountHandle phoneAccountHandle;
  protected final TranscriptionConfigProvider configProvider;
  protected final TranscriptionDbHelper dbHelper;
  protected ByteString audioData;
  protected AudioFormat encoding;
  protected volatile boolean cancelled;

  /** Functional interface for sending requests to the transcription server */
  public interface Request {
    TranscriptionResponse getResponse(TranscriptionClient client);
  }

  TranscriptionTask(
      Context context,
      JobCallback callback,
      JobWorkItem workItem,
      TranscriptionClientFactory clientFactory,
      TranscriptionConfigProvider configProvider) {
    this.context = context;
    this.callback = callback;
    this.workItem = workItem;
    this.clientFactory = clientFactory;
    this.voicemailUri = TranscriptionService.getVoicemailUri(workItem);
    this.phoneAccountHandle = TranscriptionService.getPhoneAccountHandle(workItem);
    this.configProvider = configProvider;
    dbHelper = new TranscriptionDbHelper(context, voicemailUri);
  }

  @MainThread
  void cancel() {
    Assert.isMainThread();
    VvmLog.i(TAG, "cancel");
    cancelled = true;
  }

  @Override
  public void run() {
    VvmLog.i(TAG, "run");
    if (readAndValidateAudioFile()) {
      updateTranscriptionState(VoicemailCompat.TRANSCRIPTION_IN_PROGRESS);
      transcribeVoicemail();
    } else {
      if (AudioFormat.AUDIO_FORMAT_UNSPECIFIED.equals(encoding)) {
        Logger.get(context)
            .logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_VOICEMAIL_FORMAT_NOT_SUPPORTED);
      } else {
        Logger.get(context)
            .logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_VOICEMAIL_INVALID_DATA);
      }
      updateTranscriptionState(VoicemailCompat.TRANSCRIPTION_FAILED);
    }
    ThreadUtil.postOnUiThread(
        () -> {
          callback.onWorkCompleted(workItem);
        });
  }

  protected abstract Pair<String, TranscriptionStatus> getTranscription();

  protected abstract DialerImpression.Type getRequestSentImpression();

  private void transcribeVoicemail() {
    VvmLog.i(TAG, "transcribeVoicemail");
    recordResult(context, getTranscription(), dbHelper, cancelled);
  }

  protected TranscriptionResponse sendRequest(Request request) {
    VvmLog.i(TAG, "sendRequest");
    TranscriptionClient client = clientFactory.getClient();
    for (int i = 0; i < configProvider.getMaxTranscriptionRetries(); i++) {
      if (cancelled) {
        VvmLog.i(TAG, "sendRequest, cancelled");
        return null;
      }

      VvmLog.i(TAG, "sendRequest, try: " + (i + 1));
      if (i == 0) {
        Logger.get(context).logImpression(getRequestSentImpression());
      } else {
        Logger.get(context).logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_REQUEST_RETRY);
      }

      TranscriptionResponse response = request.getResponse(client);
      if (cancelled) {
        VvmLog.i(TAG, "sendRequest, cancelled");
        return null;
      } else if (response.hasRecoverableError()) {
        Logger.get(context)
            .logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_RESPONSE_RECOVERABLE_ERROR);
        backoff(i);
      } else {
        return response;
      }
    }

    Logger.get(context)
        .logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_RESPONSE_TOO_MANY_ERRORS);
    return null;
  }

  private static void backoff(int retryCount) {
    VvmLog.i(TAG, "backoff, count: " + retryCount);
    long millis = (1L << retryCount) * 1000;
    sleep(millis);
  }

  protected static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      VvmLog.e(TAG, "interrupted", e);
      Thread.currentThread().interrupt();
    }
  }

  protected void updateTranscriptionState(int newState) {
    dbHelper.setTranscriptionState(newState);
  }

  protected void updateTranscriptionAndState(String transcript, int newState) {
    dbHelper.setTranscriptionAndState(transcript, newState);
  }

  static void recordResult(
      Context context, Pair<String, TranscriptionStatus> result, TranscriptionDbHelper dbHelper) {
    recordResult(context, result, dbHelper, false);
  }

  static void recordResult(
      Context context,
      Pair<String, TranscriptionStatus> result,
      TranscriptionDbHelper dbHelper,
      boolean cancelled) {
    if (result.first != null) {
      VvmLog.i(TAG, "recordResult, got transcription");
      dbHelper.setTranscriptionAndState(result.first, VoicemailCompat.TRANSCRIPTION_AVAILABLE);
      Logger.get(context).logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_RESPONSE_SUCCESS);
    } else if (result.second != null) {
      VvmLog.i(TAG, "recordResult, failed to transcribe, reason: " + result.second);
      switch (result.second) {
        case FAILED_NO_SPEECH_DETECTED:
          dbHelper.setTranscriptionState(VoicemailCompat.TRANSCRIPTION_FAILED_NO_SPEECH_DETECTED);
          Logger.get(context)
              .logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_RESPONSE_NO_SPEECH_DETECTED);
          break;
        case FAILED_LANGUAGE_NOT_SUPPORTED:
          dbHelper.setTranscriptionState(
              VoicemailCompat.TRANSCRIPTION_FAILED_LANGUAGE_NOT_SUPPORTED);
          Logger.get(context)
              .logImpression(
                  DialerImpression.Type.VVM_TRANSCRIPTION_RESPONSE_LANGUAGE_NOT_SUPPORTED);
          break;
        case EXPIRED:
          dbHelper.setTranscriptionState(VoicemailCompat.TRANSCRIPTION_FAILED);
          Logger.get(context)
              .logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_RESPONSE_EXPIRED);
          break;
        default:
          dbHelper.setTranscriptionState(
              cancelled
                  ? VoicemailCompat.TRANSCRIPTION_NOT_STARTED
                  : VoicemailCompat.TRANSCRIPTION_FAILED);
          Logger.get(context).logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_RESPONSE_EMPTY);
          break;
      }
    }
  }

  private boolean readAndValidateAudioFile() {
    if (voicemailUri == null) {
      VvmLog.i(TAG, "Transcriber.readAndValidateAudioFile, file not found.");
      return false;
    } else {
      VvmLog.i(TAG, "Transcriber.readAndValidateAudioFile, reading: " + voicemailUri);
    }

    audioData = TranscriptionUtils.getAudioData(context, voicemailUri);
    if (audioData != null) {
      VvmLog.i(TAG, "readAndValidateAudioFile, read " + audioData.size() + " bytes");
    } else {
      VvmLog.i(TAG, "readAndValidateAudioFile, unable to read audio data for " + voicemailUri);
      return false;
    }

    encoding = TranscriptionUtils.getAudioFormat(audioData);
    if (encoding == AudioFormat.AUDIO_FORMAT_UNSPECIFIED) {
      VvmLog.i(TAG, "Transcriber.readAndValidateAudioFile, unknown encoding");
      return false;
    }

    return true;
  }

  @VisibleForTesting
  void setAudioDataForTesting(ByteString audioData) {
    this.audioData = audioData;
    encoding = TranscriptionUtils.getAudioFormat(audioData);
  }
}
