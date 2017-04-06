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
import android.support.annotation.NonNull;
import com.android.dialer.common.Assert;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;

/**
 * Factory methods for creating {@link DialerExecutor} objects for doing background work.
 *
 * <p>You may create an executor from a UI component (activity or fragment) or a non-UI component.
 * Using this class provides a number of benefits:
 *
 * <ul>
 *   <li>Ensures that UI tasks keep running across configuration changes by using a headless
 *       fragment.
 *   <li>Forces exceptions to crash the application, unless the user implements their own onFailure
 *       method.
 *   <li>Checks for dead UI components which can be encountered if a UI task runs longer than its
 *       UI. If a dead UI component is encountered, onSuccess/onFailure are not called (because they
 *       can't be) but a message is logged.
 *   <li>Helps prevents memory leaks in UI tasks by ensuring that callbacks are nulled out when the
 *       headless fragment is detached.
 *   <li>UI and non-UI threads are shared across the application and run at reasonable priorities
 * </ul>
 *
 * <p>Executors accept a single input and output parameter which should be immutable data objects.
 * If you don't require an input or output, use Void and null as needed.
 *
 * <p>You may optionally specify onSuccess and onFailure listeners; the default behavior on success
 * is a no-op and the default behavior on failure is to crash the application.
 *
 * <p>To use an executor from a UI component, you must create it in your onCreate method and then
 * use it from anywhere:
 *
 * <pre><code>
 *
 * public class MyActivity extends Activity {
 *
 *   private final DialerExecutor&lt;MyInputType&gt; myExecutor;
 *
 *   public void onCreate(Bundle state) {
 *     super.onCreate(bundle);
 *
 *     // Must be called in onCreate; don't use non-static or anonymous inner classes for worker!
 *     myExecutor = DialerExecutors.createUiTaskBuilder(fragmentManager, taskId, worker)
 *         .onSuccess(this::onSuccess)  // Lambdas, anonymous, or non-static inner classes all fine
 *         .onFailure(this::onFailure)  // Lambdas, anonymous, or non-static inner classes all fine
 *         .build();
 *     );
 *   }
 *
 *   private static class MyWorker implements Worker&lt;MyInputType, MyOutputType&gt; {
 *     MyOutputType doInBackground(MyInputType input) { ... }
 *   }
 *   private void onSuccess(MyOutputType output) { ... }
 *   private void onFailure(Throwable throwable) { ... }
 *
 *   private void userDidSomething() { myExecutor.executeParallel(input); }
 * }
 * </code></pre>
 *
 * <p>Usage for non-UI tasks is the same, except that tasks can be created from anywhere instead of
 * in onCreate. Non-UI tasks use low-priority threads separate from the UI task threads so as not to
 * compete with more critical UI tasks.
 *
 * <pre><code>
 *
 * public class MyManager {
 *
 *   private final DialerExecutor&lt;MyInputType&gt; myExecutor;
 *
 *   public void init() {
 *     // Don't use non-static or anonymous inner classes for worker!
 *     myExecutor = DialerExecutors.createNonUiTaskBuilder(worker)
 *         .onSuccess(this::onSuccess)  // Lambdas, anonymous, or non-static inner classes all fine
 *         .onFailure(this::onFailure)  // Lambdas, anonymous, or non-static inner classes all fine
 *         .build();
 *     );
 *   }
 *
 *   private static class MyWorker implements Worker&lt;MyInputType, MyOutputType&gt; {
 *     MyOutputType doInBackground(MyInputType input) { ... }
 *   }
 *   private void onSuccess(MyOutputType output) { ... }
 *   private void onFailure(Throwable throwable) { ... }
 *
 *   private void userDidSomething() { myExecutor.executeParallel(input); }
 * }
 * </code></pre>
 *
 * Note that non-UI tasks are intended to be relatively quick; for example reading/writing shared
 * preferences or doing simple database work. If you submit long running non-UI tasks you may
 * saturate the shared application threads and block other tasks. Also, this class does not create
 * any wakelocks, so a long running task could be killed if the device goes to sleep while your task
 * is still running. If you have to do long running or periodic work, consider using a job
 * scheduler.
 */
public final class DialerExecutors {

  /** @see DialerExecutorFactory#createUiTaskBuilder(FragmentManager, String, Worker) */
  @NonNull
  public static <InputT, OutputT> DialerExecutor.Builder<InputT, OutputT> createUiTaskBuilder(
      @NonNull FragmentManager fragmentManager,
      @NonNull String taskId,
      @NonNull Worker<InputT, OutputT> worker) {
    return new DefaultDialerExecutorFactory()
        .createUiTaskBuilder(
            Assert.isNotNull(fragmentManager), Assert.isNotNull(taskId), Assert.isNotNull(worker));
  }

  /** @see DialerExecutorFactory#createNonUiTaskBuilder(Worker) */
  @NonNull
  public static <InputT, OutputT> DialerExecutor.Builder<InputT, OutputT> createNonUiTaskBuilder(
      @NonNull Worker<InputT, OutputT> worker) {
    return new DefaultDialerExecutorFactory().createNonUiTaskBuilder(Assert.isNotNull(worker));
  }
}
