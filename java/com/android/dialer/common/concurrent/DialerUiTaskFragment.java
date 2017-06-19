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

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.FailureListener;
import com.android.dialer.common.concurrent.DialerExecutor.SuccessListener;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Do not use this class directly. Instead use {@link DialerExecutors}.
 *
 * @param <InputT> the type of the object sent to the task upon execution
 * @param <OutputT> the type of the result of the background computation
 */
public final class DialerUiTaskFragment<InputT, OutputT> extends Fragment {

  private Worker<InputT, OutputT> worker;
  private SuccessListener<OutputT> successListener;
  private FailureListener failureListener;

  private ScheduledExecutorService serialExecutor;
  private Executor parallelExecutor;
  private ScheduledFuture<?> scheduledFuture;

  /**
   * Creates a new {@link DialerUiTaskFragment} or gets an existing one in the event that a
   * configuration change occurred while the previous activity's task was still running. Must be
   * called from onCreate of your activity or fragment.
   *
   * @param taskId used for the headless fragment ID and task ID
   * @param worker a function executed on a worker thread which accepts an {@link InputT} and
   *     returns an {@link OutputT}. It should ideally not be an inner class of your
   *     activity/fragment (meaning it should not be a lambda, anonymous, or non-static) but it can
   *     be a static nested class. The static nested class should not contain any reference to UI,
   *     including any activity or fragment or activity context, though it may reference some
   *     threadsafe system objects such as the application context.
   * @param successListener a function executed on the main thread upon task success. There are no
   *     restraints on this as it is executed on the main thread, so lambdas, anonymous, or inner
   *     classes of your activity or fragment are all fine.
   * @param failureListener a function executed on the main thread upon task failure. The exception
   *     is already logged so this can often be a no-op. There are no restraints on this as it is
   *     executed on the main thread, so lambdas, anonymous, or inner classes of your activity or
   *     fragment are all fine.
   * @param <InputT> the type of the object sent to the task upon execution
   * @param <OutputT> the type of the result of the background computation
   * @return a {@link DialerUiTaskFragment} which may be used to call the "execute*" methods
   */
  @MainThread
  static <InputT, OutputT> DialerUiTaskFragment<InputT, OutputT> create(
      FragmentManager fragmentManager,
      String taskId,
      Worker<InputT, OutputT> worker,
      SuccessListener<OutputT> successListener,
      FailureListener failureListener,
      @NonNull ScheduledExecutorService serialExecutorService,
      @NonNull Executor parallelExecutor) {
    Assert.isMainThread();

    DialerUiTaskFragment<InputT, OutputT> fragment =
        (DialerUiTaskFragment<InputT, OutputT>) fragmentManager.findFragmentByTag(taskId);

    if (fragment == null) {
      LogUtil.i("DialerUiTaskFragment.create", "creating new DialerUiTaskFragment");
      fragment = new DialerUiTaskFragment<>();
      fragmentManager.beginTransaction().add(fragment, taskId).commit();
    }
    fragment.worker = worker;
    fragment.successListener = successListener;
    fragment.failureListener = failureListener;
    fragment.serialExecutor = Assert.isNotNull(serialExecutorService);
    fragment.parallelExecutor = Assert.isNotNull(parallelExecutor);
    return fragment;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setRetainInstance(true);
  }

  @Override
  public void onDetach() {
    super.onDetach();
    LogUtil.enterBlock("DialerUiTaskFragment.onDetach");
    successListener = null;
    failureListener = null;
    if (scheduledFuture != null) {
      scheduledFuture.cancel(false /* mayInterrupt */);
      scheduledFuture = null;
    }
  }

  void executeSerial(InputT input) {
    serialExecutor.execute(() -> runTask(input));
  }

  void executeSerialWithWait(InputT input, long waitMillis) {
    if (scheduledFuture != null) {
      LogUtil.i("DialerUiTaskFragment.executeSerialWithWait", "cancelling waiting task");
      scheduledFuture.cancel(false /* mayInterrupt */);
    }
    scheduledFuture =
        serialExecutor.schedule(() -> runTask(input), waitMillis, TimeUnit.MILLISECONDS);
  }

  void executeParallel(InputT input) {
    parallelExecutor.execute(() -> runTask(input));
  }

  void executeOnCustomExecutor(ExecutorService executor, InputT input) {
    executor.execute(() -> runTask(input));
  }

  @WorkerThread
  private void runTask(@Nullable InputT input) {
    try {
      OutputT output = worker.doInBackground(input);
      if (successListener == null) {
        LogUtil.i("DialerUiTaskFragment.runTask", "task succeeded but UI is dead");
      } else {
        ThreadUtil.postOnUiThread(() -> successListener.onSuccess(output));
      }
    } catch (Throwable throwable) {
      LogUtil.e("DialerUiTaskFragment.runTask", "task failed", throwable);
      if (failureListener == null) {
        LogUtil.i("DialerUiTaskFragment.runTask", "task failed but UI is dead");
      } else {
        ThreadUtil.postOnUiThread(() -> failureListener.onFailure(throwable));
      }
    }
  }
}
