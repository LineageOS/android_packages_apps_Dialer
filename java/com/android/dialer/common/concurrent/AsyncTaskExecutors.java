/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.dialer.common.concurrent;

import android.os.AsyncTask;

import androidx.annotation.MainThread;

import com.android.dialer.common.Assert;
import java.util.concurrent.Executor;

/**
 * Factory methods for creating AsyncTaskExecutors.
 */
public final class AsyncTaskExecutors {

  /**
   * Creates an AsyncTaskExecutor that submits tasks to run with {@link AsyncTask#SERIAL_EXECUTOR}.
   */
  public static AsyncTaskExecutor createAsyncTaskExecutor() {
    synchronized (AsyncTaskExecutors.class) {
      return new SimpleAsyncTaskExecutor(AsyncTask.SERIAL_EXECUTOR);
    }
  }

  /**
   * Creates an AsyncTaskExecutor that submits tasks to run with {@link
   * AsyncTask#THREAD_POOL_EXECUTOR}.
   */
  public static AsyncTaskExecutor createThreadPoolExecutor() {
    synchronized (AsyncTaskExecutors.class) {
      return new SimpleAsyncTaskExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  static class SimpleAsyncTaskExecutor implements AsyncTaskExecutor {

    private final Executor executor;

    public SimpleAsyncTaskExecutor(Executor executor) {
      this.executor = executor;
    }

    @Override
    @MainThread
    public <T> AsyncTask<T, ?, ?> submit(Object identifer, AsyncTask<T, ?, ?> task, T... params) {
      Assert.isMainThread();
      return task.executeOnExecutor(executor, params);
    }
  }
}
