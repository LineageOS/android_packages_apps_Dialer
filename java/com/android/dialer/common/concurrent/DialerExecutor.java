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

package com.android.dialer.common.concurrent;

import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import java.util.concurrent.ExecutorService;

/**
 * Provides a consistent interface for doing background work in either UI or non-UI contexts.
 *
 * <p>See {@link DialerExecutors} for usage examples.
 */
public interface DialerExecutor<InputT> {

  /** Functional interface for doing work in the background. */
  interface Worker<InputT, OutputT> {
    @WorkerThread
    @Nullable
    OutputT doInBackground(@Nullable InputT input) throws Throwable;
  }

  /** Functional interface for handling the result of background work. */
  interface SuccessListener<OutputT> {
    @MainThread
    void onSuccess(@Nullable OutputT output);
  }

  /** Functional interface for handling an error produced while performing background work. */
  interface FailureListener {
    @MainThread
    void onFailure(@NonNull Throwable throwable);
  }

  /** Builder for {@link DialerExecutor}. */
  interface Builder<InputT, OutputT> {

    /**
     * Optional. Default is no-op.
     *
     * @param successListener a function executed on the main thread upon task success. There are no
     *     restraints on this as it is executed on the main thread, so lambdas, anonymous, or inner
     *     classes of your activity or fragment are all fine.
     */
    @NonNull
    Builder<InputT, OutputT> onSuccess(@NonNull SuccessListener<OutputT> successListener);

    /**
     * Optional. If this is not set and your worker throws an exception, the application will crash.
     *
     * @param failureListener a function executed on the main thread upon task failure. There are no
     *     restraints on this as it is executed on the main thread, so lambdas, anonymous, or inner
     *     classes of your activity or fragment are all fine.
     */
    @NonNull
    Builder<InputT, OutputT> onFailure(@NonNull FailureListener failureListener);

    /**
     * Builds the {@link DialerExecutor} which can be used to execute your task (repeatedly with
     * differing inputs if desired).
     */
    @NonNull
    DialerExecutor<InputT> build();
  }

  /** Executes the task such that repeated executions for this executor are serialized. */
  @MainThread
  void executeSerial(@Nullable InputT input);

  /**
   * Executes the task after waiting {@code waitMillis}. If called while the previous invocation is
   * still waiting to be started, the original invocation is cancelled.
   *
   * <p>This is useful for tasks which might get scheduled many times in very quick succession, but
   * it is only the last one that actually needs to be executed.
   */
  @MainThread
  void executeSerialWithWait(@Nullable InputT input, long waitMillis);

  /**
   * Executes the task on a thread pool shared across the application. Multiple calls using this
   * method may result in tasks being executed in parallel.
   */
  @MainThread
  void executeParallel(@Nullable InputT input);

  /**
   * Executes the task on a custom executor service. This should rarely be used; instead prefer
   * {@link #executeSerial(Object)} or {@link #executeParallel(Object)}.
   */
  @MainThread
  void executeOnCustomExecutorService(
      @NonNull ExecutorService executorService, @Nullable InputT input);
}
