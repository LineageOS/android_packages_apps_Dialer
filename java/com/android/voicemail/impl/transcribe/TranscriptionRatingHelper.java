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

import android.content.Context;
import android.net.Uri;
import com.android.dialer.common.concurrent.DialerExecutor;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.compat.android.provider.VoicemailCompat;
import com.google.internal.communications.voicemailtranscription.v1.SendTranscriptionFeedbackRequest;
import com.google.internal.communications.voicemailtranscription.v1.TranscriptionRating;
import com.google.internal.communications.voicemailtranscription.v1.TranscriptionRatingValue;
import com.google.protobuf.ByteString;

/**
 * Send voicemail transcription rating feedback to the server and record the fact that feedback was
 * provided in the local database.
 */
public class TranscriptionRatingHelper {

  /** Callback invoked after the feedback has been recorded locally */
  public interface SuccessListener {
    void onRatingSuccess(Uri voicemailUri);
  }

  /** Callback invoked if there was an error recording the feedback */
  public interface FailureListener {
    void onRatingFailure(Throwable t);
  }

  /**
   * Method for sending a user voicemail transcription feedback rating to the server and recording
   * the fact that the voicemail was rated in the local database.
   */
  public static void sendRating(
      Context context,
      TranscriptionRatingValue ratingValue,
      Uri voicemailUri,
      SuccessListener successListener,
      FailureListener failureListener) {
    DialerExecutorComponent.get(context)
        .dialerExecutorFactory()
        .createNonUiTaskBuilder(new RatingWorker(context, ratingValue, voicemailUri))
        .onSuccess(output -> successListener.onRatingSuccess(voicemailUri))
        .onFailure(e -> failureListener.onRatingFailure(e))
        .build()
        .executeParallel(null);
  }

  /** Worker class used to record a user's quality rating of a voicemail transcription. */
  private static class RatingWorker implements DialerExecutor.Worker<Void, Void> {
    private final Context context;
    private final TranscriptionRatingValue ratingValue;
    private final Uri voicemailUri;

    private RatingWorker(Context context, TranscriptionRatingValue ratingValue, Uri voicemailUri) {
      this.context = context;
      this.ratingValue = ratingValue;
      this.voicemailUri = voicemailUri;
    }

    @Override
    public Void doInBackground(Void input) {
      // Schedule a task to upload the feedback (requires network connectivity)
      TranscriptionRatingService.scheduleTask(context, getFeedbackRequest());

      // Record the fact that the transcription has been rated
      TranscriptionDbHelper dbHelper = new TranscriptionDbHelper(context, voicemailUri);
      dbHelper.setTranscriptionState(VoicemailCompat.TRANSCRIPTION_AVAILABLE_AND_RATED);
      return null;
    }

    private SendTranscriptionFeedbackRequest getFeedbackRequest() {
      ByteString audioData = TranscriptionUtils.getAudioData(context, voicemailUri);
      String salt = voicemailUri.toString();
      String voicemailId = TranscriptionUtils.getFingerprintFor(audioData, salt);
      TranscriptionRating rating =
          TranscriptionRating.newBuilder()
              .setTranscriptionId(voicemailId)
              .setRatingValue(ratingValue)
              .build();
      return SendTranscriptionFeedbackRequest.newBuilder().addRating(rating).build();
    }
  }
}
