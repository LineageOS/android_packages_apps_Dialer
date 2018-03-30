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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import android.util.Pair;
import com.android.dialer.common.Assert;
import com.android.dialer.common.backoff.ExponentialBaseCalculator;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.transcribe.grpc.GetTranscriptResponseAsync;
import com.android.voicemail.impl.transcribe.grpc.TranscriptionClient;
import com.android.voicemail.impl.transcribe.grpc.TranscriptionClientFactory;
import com.google.internal.communications.voicemailtranscription.v1.GetTranscriptRequest;
import com.google.internal.communications.voicemailtranscription.v1.TranscriptionStatus;
import java.util.List;

/**
 * This class uses the AlarmManager to poll for the result of a voicemail transcription request.
 * Initially it waits for the estimated transcription time, and if the result is not available then
 * it polls using an exponential backoff scheme.
 */
public class GetTranscriptReceiver extends BroadcastReceiver {
  private static final String TAG = "GetTranscriptReceiver";
  static final String EXTRA_IS_INITIAL_ESTIMATED_WAIT = "extra_is_initial_estimated_wait";
  static final String EXTRA_VOICEMAIL_URI = "extra_voicemail_uri";
  static final String EXTRA_TRANSCRIPT_ID = "extra_transcript_id";
  static final String EXTRA_DELAY_MILLIS = "extra_delay_millis";
  static final String EXTRA_BASE_MULTIPLIER = "extra_base_multiplier";
  static final String EXTRA_REMAINING_ATTEMPTS = "extra_remaining_attempts";
  static final String EXTRA_PHONE_ACCOUNT = "extra_phone_account";
  static final String POLL_ALARM_ACTION =
      "com.android.voicemail.impl.transcribe.GetTranscriptReceiver.POLL_ALARM";

  // Schedule an initial alarm to begin checking for a voicemail transcription result.
  static void beginPolling(
      Context context,
      Uri voicemailUri,
      String transcriptId,
      long estimatedTranscriptionTimeMillis,
      TranscriptionConfigProvider configProvider,
      PhoneAccountHandle account) {
    Assert.checkState(!hasPendingAlarm(context));
    long initialDelayMillis = configProvider.getInitialGetTranscriptPollDelayMillis();
    long maxBackoffMillis = configProvider.getMaxGetTranscriptPollTimeMillis();
    int maxAttempts = configProvider.getMaxGetTranscriptPolls();
    double baseMultiplier =
        ExponentialBaseCalculator.findBase(initialDelayMillis, maxBackoffMillis, maxAttempts);
    Intent intent =
        makeAlarmIntent(
            context,
            voicemailUri,
            transcriptId,
            initialDelayMillis,
            baseMultiplier,
            maxAttempts,
            account);
    // Add an extra to distinguish this initial estimated transcription wait from subsequent backoff
    // waits
    intent.putExtra(EXTRA_IS_INITIAL_ESTIMATED_WAIT, true);
    VvmLog.i(
        TAG,
        String.format(
            "beginPolling, check in %d millis, for: %s",
            estimatedTranscriptionTimeMillis, transcriptId));
    scheduleAlarm(context, estimatedTranscriptionTimeMillis, intent);
  }

  static boolean hasPendingAlarm(Context context) {
    Intent intent = makeBaseAlarmIntent(context);
    return getPendingIntent(context, intent, PendingIntent.FLAG_NO_CREATE) != null;
  }

  // Alarm fired, poll for transcription result on a background thread
  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent == null || !POLL_ALARM_ACTION.equals(intent.getAction())) {
      return;
    }
    String transcriptId = intent.getStringExtra(EXTRA_TRANSCRIPT_ID);
    VvmLog.i(TAG, "onReceive, for transcript id: " + transcriptId);
    DialerExecutorComponent.get(context)
        .dialerExecutorFactory()
        .createNonUiTaskBuilder(new PollWorker(context))
        .onSuccess(this::onSuccess)
        .onFailure(this::onFailure)
        .build()
        .executeParallel(intent);
  }

  private void onSuccess(Void unused) {
    VvmLog.i(TAG, "onSuccess");
  }

  private void onFailure(Throwable t) {
    VvmLog.e(TAG, "onFailure", t);
  }

  private static void scheduleAlarm(Context context, long delayMillis, Intent intent) {
    PendingIntent alarmIntent =
        getPendingIntent(context, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    alarmMgr.set(
        AlarmManager.ELAPSED_REALTIME_WAKEUP,
        SystemClock.elapsedRealtime() + delayMillis,
        alarmIntent);
  }

  private static boolean cancelAlarm(Context context, Intent intent) {
    PendingIntent alarmIntent = getPendingIntent(context, intent, PendingIntent.FLAG_NO_CREATE);
    if (alarmIntent != null) {
      AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
      alarmMgr.cancel(alarmIntent);
      alarmIntent.cancel();
      return true;
    } else {
      return false;
    }
  }

  private static Intent makeAlarmIntent(
      Context context,
      Uri voicemailUri,
      String transcriptId,
      long delayMillis,
      double baseMultiplier,
      int remainingAttempts,
      PhoneAccountHandle account) {
    Intent intent = makeBaseAlarmIntent(context);
    intent.putExtra(EXTRA_VOICEMAIL_URI, voicemailUri);
    intent.putExtra(EXTRA_TRANSCRIPT_ID, transcriptId);
    intent.putExtra(EXTRA_DELAY_MILLIS, delayMillis);
    intent.putExtra(EXTRA_BASE_MULTIPLIER, baseMultiplier);
    intent.putExtra(EXTRA_REMAINING_ATTEMPTS, remainingAttempts);
    intent.putExtra(EXTRA_PHONE_ACCOUNT, account);
    return intent;
  }

  private static Intent makeBaseAlarmIntent(Context context) {
    Intent intent = new Intent(context.getApplicationContext(), GetTranscriptReceiver.class);
    intent.setAction(POLL_ALARM_ACTION);
    return intent;
  }

  private static PendingIntent getPendingIntent(Context context, Intent intent, int flags) {
    return PendingIntent.getBroadcast(context.getApplicationContext(), 0, intent, flags);
  }

  private static class PollWorker implements Worker<Intent, Void> {
    private final Context context;

    PollWorker(Context context) {
      this.context = context;
    }

    @Override
    public Void doInBackground(Intent intent) {
      String transcriptId = intent.getStringExtra(EXTRA_TRANSCRIPT_ID);
      VvmLog.i(TAG, "doInBackground, for transcript id: " + transcriptId);
      Pair<String, TranscriptionStatus> result = pollForTranscription(transcriptId);
      if (result.first == null && result.second == null) {
        // No result, try again if possible
        Intent nextIntent = getNextAlarmIntent(intent);
        if (nextIntent == null) {
          VvmLog.i(TAG, "doInBackground, too many failures for: " + transcriptId);
          result = new Pair<>(null, TranscriptionStatus.FAILED_NO_RETRY);
        } else {
          long nextDelayMillis = nextIntent.getLongExtra(EXTRA_DELAY_MILLIS, 0L);
          VvmLog.i(
              TAG,
              String.format(
                  "doInBackground, check again in %d, for: %s", nextDelayMillis, transcriptId));
          scheduleAlarm(context, nextDelayMillis, nextIntent);
          return null;
        }
      }

      // Got transcript or failed too many times
      Uri voicemailUri = intent.getParcelableExtra(EXTRA_VOICEMAIL_URI);
      TranscriptionDbHelper dbHelper = new TranscriptionDbHelper(context, voicemailUri);
      TranscriptionTask.recordResult(context, result, dbHelper);

      // Check if there are other pending transcriptions
      PhoneAccountHandle account = intent.getParcelableExtra(EXTRA_PHONE_ACCOUNT);
      processPendingTranscriptions(account);
      return null;
    }

    private void processPendingTranscriptions(PhoneAccountHandle account) {
      TranscriptionDbHelper dbHelper = new TranscriptionDbHelper(context);
      List<Uri> inProgress = dbHelper.getTranscribingVoicemails();
      if (!inProgress.isEmpty()) {
        Uri uri = inProgress.get(0);
        VvmLog.i(TAG, "getPendingTranscription, found pending transcription " + uri);
        if (hasPendingAlarm(context)) {
          // Cancel the current alarm so that the next transcription task won't be postponed
          cancelAlarm(context, makeBaseAlarmIntent(context));
        }
        ThreadUtil.postOnUiThread(
            () -> {
              TranscriptionService.scheduleNewVoicemailTranscriptionJob(
                  context, uri, account, true);
            });
      } else {
        VvmLog.i(TAG, "getPendingTranscription, no more pending transcriptions");
      }
    }

    private Pair<String, TranscriptionStatus> pollForTranscription(String transcriptId) {
      VvmLog.i(TAG, "pollForTranscription, transcript id: " + transcriptId);
      GetTranscriptRequest request = getGetTranscriptRequest(transcriptId);
      TranscriptionClientFactory factory = null;
      try {
        factory = getTranscriptionClientFactory(context);
        TranscriptionClient client = factory.getClient();
        Logger.get(context).logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_POLL_REQUEST);
        GetTranscriptResponseAsync response = client.sendGetTranscriptRequest(request);
        if (response == null) {
          VvmLog.i(TAG, "pollForTranscription, no transcription result.");
          return new Pair<>(null, null);
        } else if (response.isTranscribing()) {
          VvmLog.i(TAG, "pollForTranscription, transcribing");
          return new Pair<>(null, null);
        } else if (response.hasFatalError()) {
          VvmLog.i(TAG, "pollForTranscription, fail. " + response.getErrorDescription());
          return new Pair<>(null, response.getTranscriptionStatus());
        } else {
          VvmLog.i(TAG, "pollForTranscription, got transcription");
          return new Pair<>(response.getTranscript(), TranscriptionStatus.SUCCESS);
        }
      } finally {
        if (factory != null) {
          factory.shutdown();
        }
      }
    }

    private GetTranscriptRequest getGetTranscriptRequest(String transcriptionId) {
      Assert.checkArgument(transcriptionId != null);
      return GetTranscriptRequest.newBuilder().setTranscriptionId(transcriptionId).build();
    }

    private @Nullable Intent getNextAlarmIntent(Intent previous) {
      int remainingAttempts = previous.getIntExtra(EXTRA_REMAINING_ATTEMPTS, 0);
      double baseMultiplier = previous.getDoubleExtra(EXTRA_BASE_MULTIPLIER, 0);
      long nextDelay = previous.getLongExtra(EXTRA_DELAY_MILLIS, 0);
      if (!previous.getBooleanExtra(EXTRA_IS_INITIAL_ESTIMATED_WAIT, false)) {
        // After waiting the estimated transcription time, start decrementing the remaining attempts
        // and incrementing the backoff time delay
        remainingAttempts--;
        if (remainingAttempts <= 0) {
          return null;
        }
        nextDelay = (long) (nextDelay * baseMultiplier);
      }
      return makeAlarmIntent(
          context,
          previous.getParcelableExtra(EXTRA_VOICEMAIL_URI),
          previous.getStringExtra(EXTRA_TRANSCRIPT_ID),
          nextDelay,
          baseMultiplier,
          remainingAttempts,
          previous.getParcelableExtra(EXTRA_PHONE_ACCOUNT));
    }
  }

  private static TranscriptionClientFactory transcriptionClientFactoryForTesting;

  static void setTranscriptionClientFactoryForTesting(TranscriptionClientFactory factory) {
    transcriptionClientFactoryForTesting = factory;
  }

  static TranscriptionClientFactory getTranscriptionClientFactory(Context context) {
    if (transcriptionClientFactoryForTesting != null) {
      return transcriptionClientFactoryForTesting;
    }
    return new TranscriptionClientFactory(context, new TranscriptionConfigProvider(context));
  }
}
