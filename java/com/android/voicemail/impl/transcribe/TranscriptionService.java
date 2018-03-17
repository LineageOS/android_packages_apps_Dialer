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
import android.os.Build;
import android.support.annotation.MainThread;
import android.support.annotation.VisibleForTesting;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.constants.ScheduledJobIds;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.voicemail.CarrierConfigKeys;
import com.android.voicemail.VoicemailClient;
import com.android.voicemail.VoicemailComponent;
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
  @VisibleForTesting static final String EXTRA_ACCOUNT_HANDLE = "extra_account_handle";

  private ExecutorService executorService;
  private JobParameters jobParameters;
  private TranscriptionClientFactory clientFactory;
  private TranscriptionConfigProvider configProvider;
  private TranscriptionTask activeTask;
  private boolean stopped;

  /** Callback used by a task to indicate it has finished processing its work item */
  interface JobCallback {
    void onWorkCompleted(JobWorkItem completedWorkItem);
  }

  // Schedule a task to transcribe the indicated voicemail, return true if transcription task was
  // scheduled.
  @MainThread
  public static boolean scheduleNewVoicemailTranscriptionJob(
      Context context, Uri voicemailUri, PhoneAccountHandle account, boolean highPriority) {
    Assert.isMainThread();
    if (!canTranscribeVoicemail(context, account)) {
      return false;
    }

    LogUtil.i(
        "TranscriptionService.scheduleNewVoicemailTranscriptionJob", "scheduling transcription");
    Logger.get(context).logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_VOICEMAIL_RECEIVED);

    ComponentName componentName = new ComponentName(context, TranscriptionService.class);
    JobInfo.Builder builder =
        new JobInfo.Builder(ScheduledJobIds.VVM_TRANSCRIPTION_JOB, componentName);
    if (highPriority) {
      builder
          .setMinimumLatency(0)
          .setOverrideDeadline(0)
          .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
    } else {
      builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
    }
    JobScheduler scheduler = context.getSystemService(JobScheduler.class);
    JobWorkItem workItem = makeWorkItem(voicemailUri, account);
    return scheduler.enqueue(builder.build(), workItem) == JobScheduler.RESULT_SUCCESS;
  }

  private static boolean canTranscribeVoicemail(Context context, PhoneAccountHandle account) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      LogUtil.i("TranscriptionService.canTranscribeVoicemail", "not supported by sdk");
      return false;
    }
    VoicemailClient client = VoicemailComponent.get(context).getVoicemailClient();
    if (!client.isVoicemailTranscriptionEnabled(context, account)) {
      LogUtil.i("TranscriptionService.canTranscribeVoicemail", "transcription is not enabled");
      return false;
    }
    if (!client.hasAcceptedTos(context, account)) {
      LogUtil.i("TranscriptionService.canTranscribeVoicemail", "hasn't accepted TOS");
      return false;
    }
    if (!Boolean.parseBoolean(
        client.getCarrierConfigString(
            context, account, CarrierConfigKeys.VVM_CARRIER_ALLOWS_OTT_TRANSCRIPTION_STRING))) {
      LogUtil.i(
          "TranscriptionService.canTranscribeVoicemail", "carrier doesn't allow transcription");
      return false;
    }
    return true;
  }

  // Cancel all transcription tasks
  @MainThread
  public static void cancelTranscriptions(Context context) {
    Assert.isMainThread();
    LogUtil.enterBlock("TranscriptionService.cancelTranscriptions");
    JobScheduler scheduler = context.getSystemService(JobScheduler.class);
    scheduler.cancel(ScheduledJobIds.VVM_TRANSCRIPTION_JOB);
  }

  @MainThread
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
  @MainThread
  public boolean onStartJob(JobParameters params) {
    Assert.isMainThread();
    LogUtil.enterBlock("TranscriptionService.onStartJob");
    if (!getConfigProvider().isVoicemailTranscriptionAvailable()) {
      LogUtil.i("TranscriptionService.onStartJob", "transcription not available, exiting.");
      return false;
    } else if (TextUtils.isEmpty(getConfigProvider().getServerAddress())) {
      LogUtil.i("TranscriptionService.onStartJob", "transcription server not configured, exiting.");
      return false;
    } else {
      LogUtil.i(
          "TranscriptionService.onStartJob",
          "transcription server address: " + configProvider.getServerAddress());
      jobParameters = params;
      return checkForWork();
    }
  }

  @Override
  @MainThread
  public boolean onStopJob(JobParameters params) {
    Assert.isMainThread();
    LogUtil.i("TranscriptionService.onStopJob", "params: " + params);
    stopped = true;
    Logger.get(this).logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_JOB_STOPPED);
    if (activeTask != null) {
      LogUtil.i("TranscriptionService.onStopJob", "cancelling active task");
      activeTask.cancel();
      Logger.get(this).logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_TASK_CANCELLED);
    }
    return true;
  }

  @Override
  @MainThread
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
  }

  @MainThread
  private boolean checkForWork() {
    Assert.isMainThread();
    if (stopped) {
      LogUtil.i("TranscriptionService.checkForWork", "stopped");
      return false;
    }
    JobWorkItem workItem = jobParameters.dequeueWork();
    if (workItem != null) {
      Assert.checkState(activeTask == null);
      activeTask =
          configProvider.shouldUseSyncApi()
              ? new TranscriptionTaskSync(
                  this, new Callback(), workItem, getClientFactory(), configProvider)
              : new TranscriptionTaskAsync(
                  this, new Callback(), workItem, getClientFactory(), configProvider);
      getExecutorService().execute(activeTask);
      return true;
    } else {
      return false;
    }
  }

  static Uri getVoicemailUri(JobWorkItem workItem) {
    return workItem.getIntent().getParcelableExtra(EXTRA_VOICEMAIL_URI);
  }

  static PhoneAccountHandle getPhoneAccountHandle(JobWorkItem workItem) {
    return workItem.getIntent().getParcelableExtra(EXTRA_ACCOUNT_HANDLE);
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
    @MainThread
    public void onWorkCompleted(JobWorkItem completedWorkItem) {
      Assert.isMainThread();
      LogUtil.i("TranscriptionService.Callback.onWorkCompleted", completedWorkItem.toString());
      activeTask = null;
      if (stopped) {
        LogUtil.i("TranscriptionService.Callback.onWorkCompleted", "stopped");
      } else {
        jobParameters.completeWork(completedWorkItem);
        checkForWork();
      }
    }
  }

  private static JobWorkItem makeWorkItem(Uri voicemailUri, PhoneAccountHandle account) {
    Intent intent = new Intent();
    intent.putExtra(EXTRA_VOICEMAIL_URI, voicemailUri);
    if (account != null) {
      intent.putExtra(EXTRA_ACCOUNT_HANDLE, account);
    }
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
