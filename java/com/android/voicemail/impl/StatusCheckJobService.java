/**
 * Copyright (C) 2017 The Android Open Source Project
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.android.voicemail.impl;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import com.android.dialer.constants.ScheduledJobIds;
import com.android.voicemail.impl.sync.VvmAccountManager;
import java.util.concurrent.TimeUnit;

/**
 * A job to perform {@link StatusCheckTask} once per day, performing book keeping to ensure the
 * credentials and status for a activated voicemail account is still correct. A task will be
 * scheduled for each active voicemail account. The status is expected to be always in sync, the
 * check is a failsafe to mimic the previous status check on signal return behavior.
 */
@TargetApi(VERSION_CODES.O)
public class StatusCheckJobService extends JobService {

  public static void schedule(Context context) {
    JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
    if (jobScheduler.getPendingJob(ScheduledJobIds.VVM_STATUS_CHECK_JOB) != null) {
      VvmLog.i("StatusCheckJobService.schedule", "job already scheduled");
      return;
    }

    jobScheduler.schedule(
        new JobInfo.Builder(
                ScheduledJobIds.VVM_STATUS_CHECK_JOB,
                new ComponentName(context, StatusCheckJobService.class))
            .setPeriodic(TimeUnit.DAYS.toMillis(1))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setRequiresCharging(true)
            .build());
  }

  @Override
  public boolean onStartJob(JobParameters params) {
    for (PhoneAccountHandle phoneAccountHandle :
        getSystemService(TelecomManager.class).getCallCapablePhoneAccounts()) {
      if (VvmAccountManager.isAccountActivated(this, phoneAccountHandle)) {
        StatusCheckTask.start(this, phoneAccountHandle);
      }
    }
    return false; // not running in background
  }

  @Override
  public boolean onStopJob(JobParameters params) {
    return false; // don't retry
  }
}
