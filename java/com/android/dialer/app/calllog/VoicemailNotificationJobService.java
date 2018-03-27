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

package com.android.dialer.app.calllog;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.provider.VoicemailContract;
import com.android.dialer.common.LogUtil;
import com.android.dialer.constants.ScheduledJobIds;

/** Monitors voicemail provider changes to update active notifications. */
public class VoicemailNotificationJobService extends JobService {

  private static JobInfo jobInfo;

  /**
   * Start monitoring the provider. The provider should be monitored whenever a visual voicemail
   * notification is visible.
   */
  public static void scheduleJob(Context context) {
    context.getSystemService(JobScheduler.class).schedule(getJobInfo(context));
    LogUtil.i("VoicemailNotificationJobService.scheduleJob", "job scheduled");
  }

  /**
   * Stop monitoring the provider. The provider should not be monitored when visual voicemail
   * notification is cleared.
   */
  public static void cancelJob(Context context) {
    context.getSystemService(JobScheduler.class).cancel(ScheduledJobIds.VVM_NOTIFICATION_JOB);
    LogUtil.i("VoicemailNotificationJobService.scheduleJob", "job canceled");
  }

  @Override
  public boolean onStartJob(JobParameters params) {
    LogUtil.i("VoicemailNotificationJobService.onStartJob", "updating notification");
    VisualVoicemailUpdateTask.scheduleTask(
        this,
        () -> {
          jobFinished(params, false);
        });
    return true; // Running in background
  }

  @Override
  public boolean onStopJob(JobParameters params) {
    return false;
  }

  private static JobInfo getJobInfo(Context context) {
    if (jobInfo == null) {
      jobInfo =
          new JobInfo.Builder(
                  ScheduledJobIds.VVM_NOTIFICATION_JOB,
                  new ComponentName(context, VoicemailNotificationJobService.class))
              .addTriggerContentUri(
                  new JobInfo.TriggerContentUri(
                      VoicemailContract.Voicemails.CONTENT_URI,
                      JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
              .setTriggerContentMaxDelay(0)
              .build();
    }

    return jobInfo;
  }
}
