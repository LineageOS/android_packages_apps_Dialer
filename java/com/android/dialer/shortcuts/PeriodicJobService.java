/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.dialer.shortcuts;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.v4.os.UserManagerCompat;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.constants.ScheduledJobIds;
import java.util.concurrent.TimeUnit;

/**
 * {@link JobService} which starts the periodic job to refresh dynamic and pinned shortcuts.
 *
 * <p>Only {@link #schedulePeriodicJob(Context)} should be used by callers.
 */
@TargetApi(VERSION_CODES.N_MR1) // Shortcuts introduced in N MR1
public final class PeriodicJobService extends JobService {

  private static final long REFRESH_PERIOD_MILLIS = TimeUnit.HOURS.toMillis(24);

  private RefreshShortcutsTask refreshShortcutsTask;

  /**
   * Schedules the periodic job to refresh shortcuts. If called repeatedly, the job will just be
   * rescheduled.
   *
   * <p>The job will not be scheduled if the build version is not at least N MR1 or if the user is
   * locked.
   */
  @MainThread
  public static void schedulePeriodicJob(@NonNull Context context) {
    Assert.isMainThread();
    LogUtil.enterBlock("PeriodicJobService.schedulePeriodicJob");

    if (VERSION.SDK_INT >= VERSION_CODES.N_MR1 && UserManagerCompat.isUserUnlocked(context)) {
      JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
      if (jobScheduler.getPendingJob(ScheduledJobIds.SHORTCUT_PERIODIC_JOB) != null) {
        LogUtil.i("PeriodicJobService.schedulePeriodicJob", "job already scheduled.");
        return;
      }
      JobInfo jobInfo =
          new JobInfo.Builder(
                  ScheduledJobIds.SHORTCUT_PERIODIC_JOB,
                  new ComponentName(context, PeriodicJobService.class))
              .setPeriodic(REFRESH_PERIOD_MILLIS)
              .setPersisted(true)
              .setRequiresCharging(true)
              .setRequiresDeviceIdle(true)
              .build();
      jobScheduler.schedule(jobInfo);
    }
  }

  /** Cancels the periodic job. */
  @MainThread
  public static void cancelJob(@NonNull Context context) {
    Assert.isMainThread();
    LogUtil.enterBlock("PeriodicJobService.cancelJob");

    context.getSystemService(JobScheduler.class).cancel(ScheduledJobIds.SHORTCUT_PERIODIC_JOB);
  }

  @Override
  @MainThread
  public boolean onStartJob(@NonNull JobParameters params) {
    Assert.isMainThread();
    LogUtil.enterBlock("PeriodicJobService.onStartJob");

    if (VERSION.SDK_INT >= VERSION_CODES.N_MR1) {
      (refreshShortcutsTask = new RefreshShortcutsTask(this)).execute(params);
    } else {
      // It is possible for the job to have been scheduled on NMR1+ and then the system was
      // downgraded to < NMR1. In this case, shortcuts are no longer supported so we cancel the job
      // which creates them.
      LogUtil.i("PeriodicJobService.onStartJob", "not running on NMR1, cancelling job");
      cancelJob(this);
      return false;
    }
    return true;
  }

  @Override
  @MainThread
  public boolean onStopJob(@NonNull JobParameters params) {
    Assert.isMainThread();
    LogUtil.enterBlock("PeriodicJobService.onStopJob");

    if (refreshShortcutsTask != null) {
      refreshShortcutsTask.cancel(false /* mayInterruptIfRunning */);
    }
    return false;
  }
}
