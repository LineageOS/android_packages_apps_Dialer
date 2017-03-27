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
import android.support.annotation.WorkerThread;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;

/**
 * Helps use async task properly.
 *
 * <p>This provides a number of benefits over using AsyncTask directly:
 *
 * <ul>
 *   <li>Ensures that tasks keep running across configuration changes by using a headless fragment.
 *   <li>Propagates exceptions to users, who must implement both onSuccess and onFailure methods.
 *   <li>Checks for dead UI components which can be encountered if a task runs longer than its UI.
 *       If a dead UI component is encountered, onSuccess/onFailure are not called (because they
 *       can't be) but a message is logged.
 *   <li>Helps prevents memory leaks by ensuring that callbacks are nulled out when the headless
 *       fragment is detached.
 * </ul>
 *
 * <p>In your activity or fragment:
 *
 * <pre><code>
 *
 * public class MyActivity extends Activity {
 *
 *   private final DialerAsyncTaskHelper&lt;MyInputType, MyOutputType&gt; myTaskHelper;
 *
 *   public void onCreate(Bundle state) {
 *     super.onCreate(bundle);
 *
 *     // Must be called in onCreate
 *     myTaskHelper = DialerAsyncTaskHelper.create(
 *         fragmentManager,
 *         taskId,
 *         new MyWorker(),  # Don't use non-static or anonymous inner classes!
 *         this::onSuccess, # Lambdas, anonymous, or non-static inner classes all fine
 *         this::onFailure  # Lambdas, anonymous, or non-static inner classes all fine
 *     );
 *   }
 *
 *   private static class MyWorker implements Worker&lt;MyInputType, MyOutputType&gt; {
 *     MyOutputType doInBackground(MyInputType input) { ... }
 *   }
 *   private void onSuccess(MyOutputType output) { ... }
 *   private void onFailure(Throwable throwable) { ... }
 *
 *   private void userDidSomething() { myTaskHelper.execute(executor, input); }
 * }
 * </code></pre>
 *
 * @param <InputT> the type of the object sent to the task upon execution
 * @param <OutputT> the type of the result of the background computation
 */
public final class DialerAsyncTaskHelper<InputT, OutputT> extends Fragment {

  private String taskId;
  private Worker<InputT, OutputT> worker;
  private SuccessListener<OutputT> successListener;
  private FailureListener failureListener;

  private final AsyncTaskExecutor serialExecutor = AsyncTaskExecutors.createAsyncTaskExecutor();
  private final AsyncTaskExecutor parallelExecutor = AsyncTaskExecutors.createThreadPoolExecutor();

  /** Functional interface for doing work in the background. */
  public interface Worker<InputT, OutputT> {
    @WorkerThread
    OutputT doInBackground(InputT input);
  }

  /** Functional interface for handling the result of background work. */
  public interface SuccessListener<OutputT> {
    @MainThread
    void onSuccess(OutputT output);
  }

  /** Functional interface for handling an error produced while performing background work. */
  public interface FailureListener {
    @MainThread
    void onFailure(Throwable throwable);
  }

  /**
   * Creates a new {@link DialerAsyncTaskHelper} or gets an existing one in the event that a
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
   * @return a {@link DialerAsyncTaskHelper} which may be used to call the "execute*" methods
   */
  @MainThread
  public static <InputT, OutputT> DialerAsyncTaskHelper<InputT, OutputT> create(
      FragmentManager fragmentManager,
      String taskId,
      Worker<InputT, OutputT> worker,
      SuccessListener<OutputT> successListener,
      FailureListener failureListener) {
    Assert.isMainThread();

    DialerAsyncTaskHelper<InputT, OutputT> helperFragment =
        (DialerAsyncTaskHelper<InputT, OutputT>) fragmentManager.findFragmentByTag(taskId);

    if (helperFragment == null) {
      LogUtil.i("DialerAsyncTaskHelper.create", "creating new helper fragment");
      helperFragment = new DialerAsyncTaskHelper<>();
      fragmentManager.beginTransaction().add(helperFragment, taskId).commit();
    }
    helperFragment.taskId = taskId;
    helperFragment.worker = worker;
    helperFragment.successListener = successListener;
    helperFragment.failureListener = failureListener;
    return helperFragment;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setRetainInstance(true);
  }

  @Override
  public void onDetach() {
    super.onDetach();
    LogUtil.enterBlock("DialerAsyncTaskHelper.onDetach");
    taskId = null;
    successListener = null;
    failureListener = null;
  }

  /**
   * Executes the task on a single thread global to the process. Multiple calls using this method
   * will result in tasks being executed serially.
   */
  @MainThread
  public void executeSerial(InputT input) {
    Assert.isMainThread();
    serialExecutor.submit(taskId, new InternalTask(), input);
  }

  /**
   * Executes the task on a thread pool shared across the application. Multiple calls using this
   * method may result in tasks being executed in parallel.
   */
  @MainThread
  public void executeParallel(InputT input) {
    Assert.isMainThread();
    parallelExecutor.submit(taskId, new InternalTask(), input);
  }

  /**
   * Executes the task on a custom executor. This should rarely be used; instead prefer {@link
   * #executeSerial(Object)} or {@link #executeParallel(Object)}.
   */
  @MainThread
  public void executeOnCustomExecutor(AsyncTaskExecutor executor, InputT input) {
    Assert.isMainThread();
    executor.submit(taskId, new InternalTask(), input);
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
