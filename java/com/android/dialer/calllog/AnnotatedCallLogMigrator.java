/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.calllog;

import android.content.SharedPreferences;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.storage.Unencrypted;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import javax.inject.Inject;

/**
 * Builds the annotated call log on application create once after the feature is enabled to reduce
 * the latency the first time call log is shown.
 */
public final class AnnotatedCallLogMigrator {

  private static final String PREF_MIGRATED = "annotatedCallLogMigratorMigrated";

  private final SharedPreferences sharedPreferences;
  private final RefreshAnnotatedCallLogWorker refreshAnnotatedCallLogWorker;
  private final ListeningExecutorService backgroundExecutor;

  @Inject
  AnnotatedCallLogMigrator(
      @Unencrypted SharedPreferences sharedPreferences,
      @BackgroundExecutor ListeningExecutorService backgroundExecutor,
      RefreshAnnotatedCallLogWorker refreshAnnotatedCallLogWorker) {
    this.sharedPreferences = sharedPreferences;
    this.backgroundExecutor = backgroundExecutor;
    this.refreshAnnotatedCallLogWorker = refreshAnnotatedCallLogWorker;
  }

  /**
   * Builds the annotated call log on application create once after the feature is enabled to reduce
   * the latency the first time call log is shown.
   */
  public ListenableFuture<Void> migrate() {
    return Futures.transformAsync(
        shouldMigrate(),
        (shouldMigrate) -> {
          if (!shouldMigrate) {
            return Futures.immediateFuture(null);
          }
          LogUtil.i("AnnotatedCallLogMigrator.migrate", "migrating annotated call log");
          return Futures.transform(
              refreshAnnotatedCallLogWorker.refreshWithoutDirtyCheck(),
              (unused) -> {
                sharedPreferences.edit().putBoolean(PREF_MIGRATED, true).apply();
                return null;
              },
              MoreExecutors.directExecutor());
        },
        MoreExecutors.directExecutor());
  }

  private ListenableFuture<Boolean> shouldMigrate() {
    return backgroundExecutor.submit(() -> !sharedPreferences.getBoolean(PREF_MIGRATED, false));
  }

  /**
   * Clears data that indicates if migration happened or not. This is necessary if migration needs
   * to happen again, for example because the call log framework was disabled via flags due to a
   * problem.
   */
  ListenableFuture<Void> clearData() {
    return backgroundExecutor.submit(
        () -> {
          sharedPreferences.edit().remove(PREF_MIGRATED).apply();
          return null;
        });
  }
}
