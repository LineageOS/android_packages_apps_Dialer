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

import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.common.concurrent.FallibleAsyncTask.FallibleTaskResult;
import com.google.auto.value.AutoValue;

/**
 * A task that runs work in the background, passing Throwables from {@link
 * #doInBackground(Object[])} to {@link #onPostExecute(Object)} through a {@link
 * FallibleTaskResult}.
 *
 * @param <ParamsT> the type of the parameters sent to the task upon execution
 * @param <ProgressT> the type of the progress units published during the background computation
 * @param <ResultT> the type of the result of the background computation
 */
public abstract class FallibleAsyncTask<ParamsT, ProgressT, ResultT>
    extends AsyncTask<ParamsT, ProgressT, FallibleTaskResult<ResultT>> {

  @Override
  protected final FallibleTaskResult<ResultT> doInBackground(ParamsT... params) {
    try {
      return FallibleTaskResult.createSuccessResult(doInBackgroundFallible(params));
    } catch (Throwable t) {
      return FallibleTaskResult.createFailureResult(t);
    }
  }

  /** Performs background work that may result in a Throwable. */
  @Nullable
  protected abstract ResultT doInBackgroundFallible(ParamsT... params) throws Throwable;

  /**
   * Holds the result of processing from {@link #doInBackground(Object[])}.
   *
   * @param <ResultT> the type of the result of the background computation
   */
  @AutoValue
  public abstract static class FallibleTaskResult<ResultT> {

    /** Creates an instance of FallibleTaskResult for the given throwable. */
    private static <ResultT> FallibleTaskResult<ResultT> createFailureResult(@NonNull Throwable t) {
      return new AutoValue_FallibleAsyncTask_FallibleTaskResult<>(t, null);
    }

    /** Creates an instance of FallibleTaskResult for the given result. */
    private static <ResultT> FallibleTaskResult<ResultT> createSuccessResult(
        @Nullable ResultT result) {
      return new AutoValue_FallibleAsyncTask_FallibleTaskResult<>(null, result);
    }

    /**
     * Returns the Throwable thrown in {@link #doInBackground(Object[])}, or {@code null} if
     * background work completed without throwing.
     */
    @Nullable
    public abstract Throwable getThrowable();

    /**
     * Returns the result of {@link #doInBackground(Object[])}, which may be {@code null}, or {@code
     * null} if the background work threw a Throwable.
     *
     * <p>Use {@link #isFailure()} to determine if a {@code null} return is the result of a
     * Throwable from the background work.
     */
    @Nullable
    public abstract ResultT getResult();

    /**
     * Returns {@code true} if this object is the result of background work that threw a Throwable.
     */
    public boolean isFailure() {
      //noinspection ThrowableResultOfMethodCallIgnored
      return getThrowable() != null;
    }
  }
}
