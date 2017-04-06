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
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.AsyncTaskExecutors.SimpleAsyncTaskExecutor;
import com.android.dialer.common.concurrent.DialerExecutor.FailureListener;
import com.android.dialer.common.concurrent.DialerExecutor.SuccessListener;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import java.util.concurrent.ExecutorService;

/**
 * Do not use this class directly. Instead use {@link DialerExecutors}.
 *
 * @param <InputT> the type of the object sent to the task upon execution
 * @param <OutputT> the type of the result of the background computation
 */
public final class DialerUiTaskFragment<InputT, OutputT> extends Fragment {

  private String taskId;
  private Worker<InputT, OutputT> worker;
  private SuccessListener<OutputT> successListener;
  private FailureListener failureListener;

  private AsyncTaskExecutor serialExecutor = AsyncTaskExecutors.createAsyncTaskExecutor();
  private AsyncTaskExecutor parallelExecutor = AsyncTaskExecutors.createThreadPoolExecutor();

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
      @Nullable ExecutorService serialExecutorService,
      @Nullable ExecutorService parallelExecutorService) {
    Assert.isMainThread();

    DialerUiTaskFragment<InputT, OutputT> fragment =
        (DialerUiTaskFragment<InputT, OutputT>) fragmentManager.findFragmentByTag(taskId);

    if (fragment == null) {
      LogUtil.i("DialerUiTaskFragment.create", "creating new DialerUiTaskFragment");
      fragment = new DialerUiTaskFragment<>();
      fragmentManager.beginTransaction().add(fragment, taskId).commit();
    }
    fragment.taskId = taskId;
    fragment.worker = worker;
    fragment.successListener = successListener;
    fragment.failureListener = failureListener;
    if (serialExecutorService != null) {
      fragment.serialExecutor = new SimpleAsyncTaskExecutor(serialExecutorService);
    }
    if (parallelExecutorService != null) {
      fragment.parallelExecutor = new SimpleAsyncTaskExecutor(parallelExecutorService);
    }
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
    taskId = null;
    successListener = null;
    failureListener = null;
  }

  void executeSerial(InputT input) {
    serialExecutor.submit(taskId, new InternalTask(), input);
  }

  void executeParallel(InputT input) {
    parallelExecutor.submit(taskId, new InternalTask(), input);
  }

  void executeOnCustomExecutor(ExecutorService executor, InputT input) {
    new SimpleAsyncTaskExecutor(executor).submit(taskId, new InternalTask(), input);
  }

  private final class InternalTask extends AsyncTask<InputT, Void, InternalTaskResult<OutputT>> {

    @SafeVarargs
    @Override
    protected final InternalTaskResult<OutputT> doInBackground(InputT... params) {
      try {
        return new InternalTaskResult<>(null, worker.doInBackground(params[0]));
      } catch (Throwable throwable) {
        LogUtil.e("InternalTask.doInBackground", "task failed", throwable);
        return new InternalTaskResult<>(throwable, null);
      }
    }

    @Override
    protected void onPostExecute(InternalTaskResult<OutputT> result) {
      if (result.throwable != null) {
        if (failureListener == null) {
          LogUtil.i("InternalTask.onPostExecute", "task failed but UI is dead");
        } else {
          failureListener.onFailure(result.throwable);
        }
      } else if (successListener == null) {
        LogUtil.i("InternalTask.onPostExecute", "task succeeded but UI is dead");
      } else {
        successListener.onSuccess(result.result);
      }
    }
  }

  private static class InternalTaskResult<OutputT> {

    private final Throwable throwable;
    private final OutputT result;

    InternalTaskResult(Throwable throwable, OutputT result) {
      this.throwable = throwable;
      this.result = result;
    }
  }
}
