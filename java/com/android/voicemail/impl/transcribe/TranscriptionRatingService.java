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

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobWorkItem;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.WorkerThread;
import android.support.v4.app.JobIntentService;
import com.android.dialer.common.LogUtil;
import com.android.dialer.constants.ScheduledJobIds;
import com.android.voicemail.impl.transcribe.grpc.TranscriptionClientFactory;
import com.google.internal.communications.voicemailtranscription.v1.SendTranscriptionFeedbackRequest;
import com.google.protobuf.InvalidProtocolBufferException;

/**
 * JobScheduler service for uploading transcription feedback. This service requires a network
 * connection.
 */
public class TranscriptionRatingService extends JobIntentService {
  private static final String FEEDBACK_REQUEST_EXTRA = "feedback_request_extra";

  /** Schedule a task to upload transcription rating feedback */
  public static boolean scheduleTask(Context context, SendTranscriptionFeedbackRequest request) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      LogUtil.enterBlock("TranscriptionRatingService.scheduleTask");
      ComponentName componentName = new ComponentName(context, TranscriptionRatingService.class);
      JobInfo.Builder builder =
          new JobInfo.Builder(ScheduledJobIds.VVM_TRANSCRIPTION_RATING_JOB, componentName)
              .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
      JobScheduler scheduler = context.getSystemService(JobScheduler.class);
      return scheduler.enqueue(builder.build(), makeWorkItem(request))
          == JobScheduler.RESULT_SUCCESS;
    } else {
      LogUtil.i("TranscriptionRatingService.scheduleTask", "not supported");
      return false;
    }
  }

  public TranscriptionRatingService() {}

  private static JobWorkItem makeWorkItem(SendTranscriptionFeedbackRequest request) {
    Intent intent = new Intent();
    intent.putExtra(FEEDBACK_REQUEST_EXTRA, request.toByteArray());
    return new JobWorkItem(intent);
  }

  @Override
  @WorkerThread
  protected void onHandleWork(Intent intent) {
    LogUtil.enterBlock("TranscriptionRatingService.onHandleWork");

    TranscriptionConfigProvider configProvider = new TranscriptionConfigProvider(this);
    TranscriptionClientFactory factory = new TranscriptionClientFactory(this, configProvider);
    try {
      // Send rating to server
      SendTranscriptionFeedbackRequest request =
          SendTranscriptionFeedbackRequest.parseFrom(
              intent.getByteArrayExtra(FEEDBACK_REQUEST_EXTRA));
      factory.getClient().sendTranscriptFeedbackRequest(request);
    } catch (InvalidProtocolBufferException e) {
      LogUtil.e("TranscriptionRatingService.onHandleWork", "failed to send feedback", e);
    } finally {
      factory.shutdown();
    }
  }

  @Override
  public void onDestroy() {
    LogUtil.enterBlock("TranscriptionRatingService.onDestroy");
    super.onDestroy();
  }
}
