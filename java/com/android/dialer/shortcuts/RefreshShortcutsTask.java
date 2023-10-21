/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.android.dialer.common.LogUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/** Class used by the periodic job service to refresh dynamic and pinned shortcuts. */
final class RefreshShortcutsTask {

  private final JobService jobService;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Handler handler = new Handler(Looper.getMainLooper());

  private boolean mIsCancelled = false;

  RefreshShortcutsTask(@NonNull JobService jobService) {
    this.jobService = jobService;
  }

  /** @param param provided from PeriodicJobService */
  public void execute(JobParameters param) {
    executor.execute(() -> {
      LogUtil.enterBlock("RefreshShortcutsTask.doInBackground");

      // Dynamic shortcuts are refreshed from the UI but icons can become stale, so update them
      // periodically using the job service.
      //
      // The reason that icons can become is stale is that there is no last updated timestamp for
      // pictures; there is only a last updated timestamp for the entire contact row, which changes
      // frequently (for example, when they are called their "times_contacted" is incremented).
      // Relying on such a spuriously updated timestamp would result in too frequent shortcut
      // updates, so instead we just allow the icon to become stale in the case that the contact's
      // photo is updated, and then rely on the job service to periodically force update it.
      new DynamicShortcuts(jobService, new IconFactory(jobService)).updateIcons(); // Blocking
      new PinnedShortcuts(jobService).refresh(); // Blocking

      if (!mIsCancelled) {
        handler.post(() -> {
          LogUtil.enterBlock("RefreshShortcutsTask.onPostExecute");
          jobService.jobFinished(param, false /* needsReschedule */);
        });
      }
    });
  }

  public void cancel() {
    mIsCancelled = true;
  }
}
