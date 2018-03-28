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
import android.support.annotation.AnyThread;
import android.support.annotation.VisibleForTesting;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.storage.Unencrypted;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

/** Provides information about the state of the annotated call log. */
@ThreadSafe
public final class CallLogState {

  private static final String ANNOTATED_CALL_LOG_BUILT_PREF = "annotated_call_log_built";

  private final SharedPreferences sharedPreferences;
  private final ListeningExecutorService backgroundExecutor;

  @VisibleForTesting
  @Inject
  public CallLogState(
      @Unencrypted SharedPreferences sharedPreferences,
      @BackgroundExecutor ListeningExecutorService backgroundExecutor) {
    this.sharedPreferences = sharedPreferences;
    this.backgroundExecutor = backgroundExecutor;
  }

  /**
   * Mark the call log as having been built. This is written to disk the first time the annotated
   * call log has been built and shouldn't ever be reset unless the user clears data.
   */
  @AnyThread
  public void markBuilt() {
    sharedPreferences.edit().putBoolean(ANNOTATED_CALL_LOG_BUILT_PREF, true).apply();
  }

  /**
   * Clear the call log state. This is useful for example if the annotated call log needs to be
   * disabled because there was a problem.
   */
  @AnyThread
  public void clearData() {
    sharedPreferences.edit().remove(ANNOTATED_CALL_LOG_BUILT_PREF).apply();
  }

  /**
   * Returns true if the annotated call log has been built at least once.
   *
   * <p>It may not yet have been built if the user was just upgraded to the new call log, or they
   * just cleared data.
   */
  @AnyThread
  public ListenableFuture<Boolean> isBuilt() {
    return backgroundExecutor.submit(
        () -> sharedPreferences.getBoolean(ANNOTATED_CALL_LOG_BUILT_PREF, false));
  }
}
