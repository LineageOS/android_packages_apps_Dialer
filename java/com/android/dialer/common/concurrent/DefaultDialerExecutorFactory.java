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

import android.app.FragmentManager;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.Builder;
import com.android.dialer.common.concurrent.DialerExecutor.FailureListener;
import com.android.dialer.common.concurrent.DialerExecutor.SuccessListener;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

/** The production {@link DialerExecutorFactory}. */
public class DefaultDialerExecutorFactory implements DialerExecutorFactory {

  @Inject
  public DefaultDialerExecutorFactory() {}

  @Override
  @NonNull
  public <InputT, OutputT> DialerExecutor.Builder<InputT, OutputT> createUiTaskBuilder(
      @NonNull FragmentManager fragmentManager,
      @NonNull String taskId,
      @NonNull Worker<InputT, OutputT> worker) {
    return new UiTaskBuilder<>(
        Assert.isNotNull(fragmentManager), Assert.isNotNull(taskId), Assert.isNotNull(worker));
  }

  @Override
  @NonNull
  public <InputT, OutputT> DialerExecutor.Builder<InputT, OutputT> createNonUiTaskBuilder(
      @NonNull Worker<InputT, OutputT> worker) {
    return new NonUiTaskBuilder<>(Assert.isNotNull(worker));
  }

  private abstract static class BaseTaskBuilder<InputT, OutputT>
      implements DialerExecutor.Builder<InputT, OutputT> {

    private final Worker<InputT, OutputT> worker;
    private SuccessListener<OutputT> successListener = output -> {};
    private FailureListener failureListener =
        throwable -> {
          throw new RuntimeException(throwable);
        };
    @Nullable final ScheduledExecutorService serialExecutorService;
    @Nullable final Executor parallelExecutor;

    BaseTaskBuilder(
        Worker<InputT, OutputT> worker,
        @Nullable ScheduledExecutorService serialExecutorService,
        @Nullable Executor parallelExecutor) {
      this.worker = worker;
      this.serialExecutorService = serialExecutorService;
      this.parallelExecutor = parallelExecutor;
    }

    @NonNull
    @Override
    public Builder<InputT, OutputT> onSuccess(@NonNull SuccessListener<OutputT> successListener) {
      this.successListener = Assert.isNotNull(successListener);
      return this;
    }

    @NonNull
    @Override
    public Builder<InputT, OutputT> onFailure(@NonNull FailureListener failureListener) {
      this.failureListener = Assert.isNotNull(failureListener);
      return this;
    }
  }

  /** Convenience class for use by {@link DialerExecutorFactory} implementations. */
  public static class UiTaskBuilder<InputT, OutputT> extends BaseTaskBuilder<InputT, OutputT> {
    private static final ScheduledExecutorService defaultSerialExecutorService =
        Executors.newSingleThreadScheduledExecutor(
            new ThreadFactory() {
              @Override
              public Thread newThread(Runnable runnable) {
                LogUtil.i("UiTaskBuilder.newThread", "creating serial thread");
                Thread thread = new Thread(runnable, "UiTaskBuilder-Serial");
                thread.setPriority(5); // Corresponds to Process.THREAD_PRIORITY_DEFAULT
                return thread;
              }
            });

    private static final Executor defaultParallelExecutorService = AsyncTask.THREAD_POOL_EXECUTOR;

    private final FragmentManager fragmentManager;
    private final String id;

    private DialerUiTaskFragment<InputT, OutputT> dialerUiTaskFragment;

    UiTaskBuilder(FragmentManager fragmentManager, String id, Worker<InputT, OutputT> worker) {
      this(
          fragmentManager,
          id,
          worker,
          defaultSerialExecutorService,
          defaultParallelExecutorService);
    }

    public UiTaskBuilder(
        FragmentManager fragmentManager,
        String id,
        Worker<InputT, OutputT> worker,
        ScheduledExecutorService serialExecutor,
        Executor parallelExecutor) {
      super(worker, serialExecutor, parallelExecutor);
      this.fragmentManager = fragmentManager;
      this.id = id;
    }

    @NonNull
    @Override
    public DialerExecutor<InputT> build() {
      dialerUiTaskFragment =
          DialerUiTaskFragment.create(
              fragmentManager,
              id,
              super.worker,
              super.successListener,
              super.failureListener,
              serialExecutorService,
              parallelExecutor);
      return new UiDialerExecutor<>(dialerUiTaskFragment);
    }
  }

  /** Convenience class for use by {@link DialerExecutorFactory} implementations. */
  public static class NonUiTaskBuilder<InputT, OutputT> extends BaseTaskBuilder<InputT, OutputT> {
    private static final ScheduledExecutorService defaultSerialExecutorService =
        Executors.newSingleThreadScheduledExecutor(
            new ThreadFactory() {
              @Override
              public Thread newThread(Runnable runnable) {
                LogUtil.i("NonUiTaskBuilder.newThread", "creating serial thread");
                Thread thread = new Thread(runnable, "NonUiTaskBuilder-Serial");
                thread.setPriority(4); // Corresponds to Process.THREAD_PRIORITY_BACKGROUND
                return thread;
              }
            });

    private static final Executor defaultParallelExecutor =
        Executors.newFixedThreadPool(
            5,
            new ThreadFactory() {
              @Override
              public Thread newThread(Runnable runnable) {
                LogUtil.i("NonUiTaskBuilder.newThread", "creating parallel thread");
                Thread thread = new Thread(runnable, "NonUiTaskBuilder-Parallel");
                thread.setPriority(4); // Corresponds to Process.THREAD_PRIORITY_BACKGROUND
                return thread;
              }
            });

    NonUiTaskBuilder(Worker<InputT, OutputT> worker) {
      this(worker, defaultSerialExecutorService, defaultParallelExecutor);
    }

    public NonUiTaskBuilder(
        Worker<InputT, OutputT> worker,
        @NonNull ScheduledExecutorService serialExecutor,
        @NonNull Executor parallelExecutor) {
      super(worker, Assert.isNotNull(serialExecutor), Assert.isNotNull(parallelExecutor));
    }

    @NonNull
    @Override
    public DialerExecutor<InputT> build() {
      return new NonUiDialerExecutor<>(
          super.worker,
          super.successListener,
          super.failureListener,
          serialExecutorService,
          parallelExecutor);
    }
  }

  private static class UiDialerExecutor<InputT, OutputT> implements DialerExecutor<InputT> {

    private final DialerUiTaskFragment<InputT, OutputT> dialerUiTaskFragment;

    UiDialerExecutor(DialerUiTaskFragment<InputT, OutputT> dialerUiTaskFragment) {
      this.dialerUiTaskFragment = dialerUiTaskFragment;
    }

    @Override
    public void executeSerial(@Nullable InputT input) {
      dialerUiTaskFragment.executeSerial(input);
    }

    @Override
    public void executeSerialWithWait(@Nullable InputT input, long waitMillis) {
      dialerUiTaskFragment.executeSerialWithWait(input, waitMillis);
    }

    @Override
    public void executeParallel(@Nullable InputT input) {
      dialerUiTaskFragment.executeParallel(input);
    }

    @Override
    public void executeOnCustomExecutorService(
        @NonNull ExecutorService executorService, @Nullable InputT input) {
      dialerUiTaskFragment.executeOnCustomExecutor(Assert.isNotNull(executorService), input);
    }
  }

  private static class NonUiDialerExecutor<InputT, OutputT> implements DialerExecutor<InputT> {

    private final Worker<InputT, OutputT> worker;
    private final SuccessListener<OutputT> successListener;
    private final FailureListener failureListener;

    private final ScheduledExecutorService serialExecutorService;
    private final Executor parallelExecutor;

    private ScheduledFuture<?> scheduledFuture;

    NonUiDialerExecutor(
        Worker<InputT, OutputT> worker,
        SuccessListener<OutputT> successListener,
        FailureListener failureListener,
        ScheduledExecutorService serialExecutorService,
        Executor parallelExecutor) {
      this.worker = worker;
      this.successListener = successListener;
      this.failureListener = failureListener;
      this.serialExecutorService = serialExecutorService;
      this.parallelExecutor = parallelExecutor;
    }

    @Override
    public void executeSerial(@Nullable InputT input) {
      serialExecutorService.execute(() -> run(input));
    }

    @Override
    public void executeSerialWithWait(@Nullable InputT input, long waitMillis) {
      if (scheduledFuture != null) {
        LogUtil.i("NonUiDialerExecutor.executeSerialWithWait", "cancelling waiting task");
        scheduledFuture.cancel(false /* mayInterrupt */);
      }
      scheduledFuture =
          serialExecutorService.schedule(() -> run(input), waitMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void executeParallel(@Nullable InputT input) {
      parallelExecutor.execute(() -> run(input));
    }

    @Override
    public void executeOnCustomExecutorService(
        @NonNull ExecutorService executorService, @Nullable InputT input) {
      Assert.isNotNull(executorService).execute(() -> run(input));
    }

    private void run(@Nullable InputT input) {
      OutputT output;
      try {
        output = worker.doInBackground(input);
      } catch (Throwable throwable) {
        ThreadUtil.postOnUiThread(() -> failureListener.onFailure(throwable));
        return;
      }
      ThreadUtil.postOnUiThread(() -> successListener.onSuccess(output));
    }
  }
}
