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

package com.android.voicemail.impl;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobInfo.TriggerContentUri;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.app.job.JobWorkItem;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build.VERSION_CODES;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.telecom.PhoneAccountHandle;
import com.android.dialer.constants.ScheduledJobIds;

/**
 * JobService triggered when the setup wizard is completed, and rerun all {@link ActivationTask}
 * scheduled during the setup.
 */
@TargetApi(VERSION_CODES.O)
public class DeviceProvisionedJobService extends JobService {

  private static final String EXTRA_PHONE_ACCOUNT_HANDLE = "EXTRA_PHONE_ACCOUNT_HANDLE";

  /** Queue the phone account to be reactivated after the setup wizard has completed. */
  public static void activateAfterProvisioned(
      Context context, PhoneAccountHandle phoneAccountHandle) {
    JobInfo jobInfo =
        new JobInfo.Builder(
                ScheduledJobIds.VVM_DEVICE_PROVISIONED_JOB,
                new ComponentName(context, DeviceProvisionedJobService.class))
            .addTriggerContentUri(
                new TriggerContentUri(Global.getUriFor(Global.DEVICE_PROVISIONED), 0))
            // VVM activation must be run as soon as possible to avoid voicemail loss
            .setTriggerContentMaxDelay(0)
            .build();

    Intent intent = new Intent();
    intent.putExtra(EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
    context.getSystemService(JobScheduler.class).enqueue(jobInfo, new JobWorkItem(intent));
  }

  @Override
  public boolean onStartJob(JobParameters params) {
    Assert.isTrue(isDeviceProvisioned());
    VvmLog.i("DeviceProvisionedJobService.onStartJob", "device provisioned");
    for (JobWorkItem item = params.dequeueWork(); item != null; item = params.dequeueWork()) {
      PhoneAccountHandle phoneAccountHandle =
          item.getIntent().getParcelableExtra(EXTRA_PHONE_ACCOUNT_HANDLE);
      VvmLog.i(
          "DeviceProvisionedJobService.onStartJob",
          "restarting activation for " + phoneAccountHandle);
      ActivationTask.start(this, phoneAccountHandle, null);
    }
    return false; // job not running in background
  }

  @Override
  public boolean onStopJob(JobParameters params) {
    return true; // reschedule job
  }

  private boolean isDeviceProvisioned() {
    return Settings.Global.getInt(getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 0) == 1;
  }
}
