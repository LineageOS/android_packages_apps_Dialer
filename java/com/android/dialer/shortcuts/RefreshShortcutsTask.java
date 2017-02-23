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
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.AsyncTask;
import android.os.Build.VERSION_CODES;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;

/** {@link AsyncTask} used by the periodic job service to refresh dynamic and pinned shortcuts. */
@TargetApi(VERSION_CODES.N_MR1) // Shortcuts introduced in N MR1
final class RefreshShortcutsTask extends AsyncTask<JobParameters, Void, JobParameters> {

  private final JobService jobService;

  RefreshShortcutsTask(@NonNull JobService jobService) {
    this.jobService = jobService;
  }

  /** @param params array with length 1, provided from PeriodicJobService */
  @Override
  @NonNull
  @WorkerThread
  protected JobParameters doInBackground(JobParameters... params) {
    Assert.isWorkerThread();
    LogUtil.enterBlock("RefreshShortcutsTask.doInBackground");

    // Dynamic shortcuts are refreshed from the UI but icons can become stale, so update them
    // periodically using the job service.
    //
    // The reason that icons can become is stale is that there is no last updated timestamp for
    // pictures; there is only a last updated timestamp for the entire contact row, which changes
    // frequently (for example, when they are called their "times_contacted" is incremented).
    // Relying on such a spuriously updated timestamp would result in too frequent shortcut updates,
    // so instead we just allow the icon to become stale in the case that the contact's photo is
    // updated, and then rely on the job service to periodically force update it.
    new DynamicShortcuts(jobService, new IconFactory(jobService)).updateIcons(); // Blocking
    new PinnedShortcuts(jobService).refresh(); // Blocking

    return params[0];
  }

  @Override
  @MainThread
  protected void onPostExecute(JobParameters params) {
    Assert.isMainThread();
    LogUtil.enterBlock("RefreshShortcutsTask.onPostExecute");

    jobService.jobFinished(params, false /* needsReschedule */);
  }
}
