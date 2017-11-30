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

import com.google.common.util.concurrent.AbstractListeningExecutorService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

/**
 * An ExecutorService that delegates to the UI thread. Rejects attempts to shut down, and all
 * shutdown related APIs are unimplemented.
 *
 */
public class UiThreadExecutor extends AbstractListeningExecutorService {

  @Inject
  UiThreadExecutor() {}

  @Override
  public void shutdown() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Runnable> shutdownNow() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isShutdown() {
    return false;
  }

  @Override
  public boolean isTerminated() {
    return false;
  }

  @Override
  public boolean awaitTermination(long l, TimeUnit timeUnit) throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  @Override
  public <V> ListenableFuture<V> submit(final Callable<V> task) {
    final SettableFuture<V> resultFuture = SettableFuture.create();
    ThreadUtil.postOnUiThread(
        () -> {
          try {
            resultFuture.set(task.call());
          } catch (Exception e) {
            // uncaught exceptions on the UI thread should crash the app
            resultFuture.setException(e);
            throw new RuntimeException(e);
          }
        });
    return resultFuture;
  }

  @Override
  public void execute(final Runnable runnable) {
    ThreadUtil.postOnUiThread(runnable);
  }
}
