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
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.app.job.JobWorkItem;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.StrictMode;
import android.support.annotation.MainThread;
import android.support.annotation.VisibleForTesting;
import android.support.v4.os.BuildCompat;
import android.text.TextUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.constants.ScheduledJobIds;
import com.android.voicemail.impl.transcribe.grpc.TranscriptionClientFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Job scheduler callback for launching voicemail transcription tasks. The transcription tasks will
 * run in the background and will typically last for approximately the length of the voicemail audio
 * (since thats how long the backend transcription service takes to do the transcription).
 */
public class TranscriptionService extends JobService {
  @VisibleForTesting static final String EXTRA_VOICEMAIL_URI = "extra_voicemail_uri";

  private ExecutorService executorService;
  private JobParameters jobParameters;
  private TranscriptionClientFactory clientFactory;
  private TranscriptionConfigProvider configProvider;
  private StrictMode.VmPolicy originalPolicy;

  /** Callback used by a task to indicate it has finished processing its work item */
  interface JobCallback {
    void onWorkCompleted(JobWorkItem completedWorkItem);
  }

  // Schedule a task to transcribe the indicated voicemail, return true if transcription task was
  // scheduled.
  public static boolean transcribeVoicemail(Context context, Uri voicemailUri) {
    Assert.isMainThread();
    if (BuildCompat.isAtLeastO()) {
      LogUtil.i("TranscriptionService.transcribeVoicemail", "scheduling transcription");
      ComponentName componentName = new ComponentName(context, TranscriptionService.class);
      JobInfo.Builder builder =
          new JobInfo.Builder(ScheduledJobIds.VVM_TRANSCRIPTION_JOB, componentName)
              .setMinimumLatency(0)
              .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
      JobScheduler scheduler = context.getSystemService(JobScheduler.class);
      JobWorkItem workItem = makeWorkItem(voicemailUri);
      return scheduler.enqueue(builder.build(), workItem) == JobScheduler.RESULT_SUCCESS;
    } else {
      LogUtil.i("TranscriptionService.transcribeVoicemail", "not supported");
      return false;
    }
  }

  // Cancel all transcription tasks
  public static void cancelTranscriptions(Context context) {
    Assert.isMainThread();
    LogUtil.enterBlock("TranscriptionService.cancelTranscriptions");
    JobScheduler scheduler = context.getSystemService(JobScheduler.class);
    scheduler.cancel(ScheduledJobIds.VVM_TRANSCRIPTION_JOB);
  }

  public TranscriptionService() {
    Assert.isMainThread();
  }

  @VisibleForTesting
  TranscriptionService(
      ExecutorService executorService,
      TranscriptionClientFactory clientFactory,
      TranscriptionConfigProvider configProvider) {
    this.executorService = executorService;
    this.clientFactory = clientFactory;
    this.configProvider = configProvider;
  }

  @Override
  public boolean onStartJob(JobParameters params) {
    Assert.isMainThread();
    LogUtil.enterBlock("TranscriptionService.onStartJob");
    if (!getConfigProvider().isVoicemailTranscriptionEnabled()) {
      LogUtil.i("TranscriptionService.onStartJob", "transcription not enabled, exiting.");
      return false;
    } else if (TextUtils.isEmpty(getConfigProvider().getServerAddress())) {
      LogUtil.i("TranscriptionService.onStartJob", "transcription server not configured, exiting.");
      return false;
    } else {
      LogUtil.i(
          "TranscriptionService.onStartJob",
          "transcription server address: " + configProvider.getServerAddress());
      originalPolicy = StrictMode.getVmPolicy();
      StrictMode.enableDefaults();
      jobParameters = params;
      return checkForWork();
    }
  }

  @Override
  public boolean onStopJob(JobParameters params) {
    Assert.isMainThread();
    LogUtil.enterBlock("TranscriptionService.onStopJob");
    cleanup();
    return true;
  }

  @Override
  public void onDestroy() {
    Assert.isMainThread();
    LogUtil.enterBlock("TranscriptionService.onDestroy");
    cleanup();
  }

  private void cleanup() {
    if (clientFactory != null) {
      clientFactory.shutdown();
      clientFactory = null;
    }
    if (executorService != null) {
      executorService.shutdownNow();
      executorService = null;
    }
    if (originalPolicy != null) {
      StrictMode.setVmPolicy(originalPolicy);
      originalPolicy = null;
    }
  }

  @MainThread
  private boolean checkForWork() {
    Assert.isMainThread();
    JobWorkItem workItem = jobParameters.dequeueWork();
    if (workItem != null) {
      getExecutorService()
          .execute(new TranscriptionTask(this, new Callback(), workItem, getClientFactory()));
      return true;
    } else {
      return false;
    }
  }

  private ExecutorService getExecutorService() {
    if (executorService == null) {
      // The common use case is transcribing a single voicemail so just use a single thread executor
      // The reason we're not using DialerExecutor here is because the transcription task can be
      // very long running (ie. multiple minutes).
      executorService = Executors.newSingleThreadExecutor();
    }
    return executorService;
  }

  private class Callback implements JobCallback {
    @Override
    public void onWorkCompleted(JobWorkItem completedWorkItem) {
      Assert.isMainThread();
      LogUtil.i("TranscriptionService.Callback.onWorkCompleted", completedWorkItem.toString());
      jobParameters.completeWork(completedWorkItem);
      checkForWork();
    }
  }

  private static JobWorkItem makeWorkItem(Uri voicemailUri) {
    Intent intent = new Intent();
    intent.putExtra(EXTRA_VOICEMAIL_URI, voicemailUri);
    return new JobWorkItem(intent);
  }

  private TranscriptionConfigProvider getConfigProvider() {
    if (configProvider == null) {
      configProvider = new TranscriptionConfigProvider(this);
    }
    return configProvider;
  }

  private TranscriptionClientFactory getClientFactory() {
    if (clientFactory == null) {
      clientFactory = new TranscriptionClientFactory(this, getConfigProvider());
    }
    return clientFactory;
  }
}
