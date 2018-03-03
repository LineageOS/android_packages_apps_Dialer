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

package com.android.dialer.metrics;

import android.os.SystemClock;
import android.support.annotation.IntDef;
import android.support.annotation.VisibleForTesting;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.LightweightExecutor;
import com.google.common.base.Function;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.inject.Inject;

/** Applies logcat and metric logging to a supplied future. */
public final class FutureTimer {

  /** Operations which exceed this threshold will have logcat warnings printed. */
  @VisibleForTesting static final long LONG_OPERATION_LOGCAT_THRESHOLD_MILLIS = 100L;

  private final Metrics metrics;
  private final ListeningExecutorService lightweightExecutorService;

  /** Modes for logging Future results to logcat. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({LogCatMode.DONT_LOG_VALUES, LogCatMode.LOG_VALUES})
  public @interface LogCatMode {
    /**
     * Don't ever log the result of the future to logcat. For example, may be appropriate if your
     * future returns a proto and you don't want to spam the logs with multi-line entries, or if
     * your future returns void/null and so would have no value being logged.
     */
    int DONT_LOG_VALUES = 1;
    /**
     * Always log the result of the future to logcat (at DEBUG level). Useful when your future
     * returns a type which has a short and useful string representation (such as a boolean). PII
     * will be sanitized.
     */
    int LOG_VALUES = 2;
  }

  @Inject
  public FutureTimer(
      Metrics metrics, @LightweightExecutor ListeningExecutorService lightweightExecutorService) {
    this.metrics = metrics;
    this.lightweightExecutorService = lightweightExecutorService;
  }

  /**
   * Applies logcat and metric logging to the supplied future.
   *
   * <p>This should be called as soon as possible after the future is submitted for execution, as
   * timing is not started until this method is called. While work for the supplied future may have
   * already begun, the time elapsed since it started is expected to be negligible for the purposes
   * of tracking heavyweight operations (which is what this method is intended for).
   */
  public <T> void applyTiming(ListenableFuture<T> future, String eventName) {
    applyTiming(future, unused -> eventName, LogCatMode.DONT_LOG_VALUES);
  }

  /**
   * Overload of {@link #applyTiming(ListenableFuture, String)} which allows setting of the {@link
   * LogCatMode}.
   */
  public <T> void applyTiming(
      ListenableFuture<T> future, String eventName, @LogCatMode int logCatMode) {
    applyTiming(future, unused -> eventName, logCatMode);
  }

  /**
   * Overload of {@link #applyTiming(ListenableFuture, String)} that accepts a function which
   * specifies how to compute an event name from the result of the future.
   *
   * <p>This is useful when the event name depends on the result of the future.
   */
  public <T> void applyTiming(
      ListenableFuture<T> future, Function<T, String> eventNameFromResultFunction) {
    applyTiming(future, eventNameFromResultFunction, LogCatMode.DONT_LOG_VALUES);
  }

  private <T> void applyTiming(
      ListenableFuture<T> future,
      Function<T, String> eventNameFromResultFunction,
      @LogCatMode int logCatMode) {
    long startTime = SystemClock.elapsedRealtime();
    Integer timerId = metrics.startUnnamedTimer();
    Futures.addCallback(
        future,
        new FutureCallback<T>() {
          @Override
          public void onSuccess(T result) {
            String eventName = eventNameFromResultFunction.apply(result);
            if (timerId != null) {
              metrics.stopUnnamedTimer(timerId, eventName);
            }
            long operationTime = SystemClock.elapsedRealtime() - startTime;

            // If the operation took a long time, do some WARNING logging.
            if (operationTime > LONG_OPERATION_LOGCAT_THRESHOLD_MILLIS) {
              switch (logCatMode) {
                case LogCatMode.DONT_LOG_VALUES:
                  LogUtil.w(
                      "FutureTimer.onSuccess",
                      "%s took more than %dms (took %dms)",
                      eventName,
                      LONG_OPERATION_LOGCAT_THRESHOLD_MILLIS,
                      operationTime);
                  break;
                case LogCatMode.LOG_VALUES:
                  LogUtil.w(
                      "FutureTimer.onSuccess",
                      "%s took more than %dms (took %dms and returned %s)",
                      eventName,
                      LONG_OPERATION_LOGCAT_THRESHOLD_MILLIS,
                      operationTime,
                      LogUtil.sanitizePii(result));
                  break;
                default:
                  throw new UnsupportedOperationException("unknown logcat mode: " + logCatMode);
              }
              return;
            }

            // The operation didn't take a long time, so just do some DEBUG logging.
            if (LogUtil.isDebugEnabled()) {
              switch (logCatMode) {
                case LogCatMode.DONT_LOG_VALUES:
                  // Operation was fast and we're not logging values, so don't log anything.
                  break;
                case LogCatMode.LOG_VALUES:
                  LogUtil.d(
                      "FutureTimer.onSuccess",
                      "%s took %dms and returned %s",
                      eventName,
                      operationTime,
                      LogUtil.sanitizePii(result));
                  break;
                default:
                  throw new UnsupportedOperationException("unknown logcat mode: " + logCatMode);
              }
            }
          }

          @Override
          public void onFailure(Throwable throwable) {
            // This callback is just for logging performance metrics; errors are handled elsewhere.
          }
        },
        lightweightExecutorService);
  }
}
