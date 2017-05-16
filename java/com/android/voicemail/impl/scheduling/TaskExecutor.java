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

package com.android.voicemail.impl.scheduling;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import com.android.voicemail.impl.Assert;
import com.android.voicemail.impl.NeededForTesting;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.scheduling.TaskQueue.NextTask;
import java.util.List;

/**
 * A singleton to queue and run {@link Task} with the {@link android.app.job.JobScheduler}. A task
 * is queued by sending a broadcast to {@link TaskReceiver}. The intent should contain enough
 * information in {@link Intent#getExtras()} to construct the task (see {@link
 * Tasks#createIntent(Context, Class)}).
 *
 * <p>The executor will only exist when {@link TaskSchedulerJobService} is running.
 *
 * <p>All tasks are ran in the background with a wakelock being held by the {@link
 * android.app.job.JobScheduler}, which is between {@link #onStartJob(Job, List)} and {@link
 * #finishJobAsync()}. The {@link TaskSchedulerJobService} also has a {@link TaskQueue}, but the
 * data is stored in the {@link android.app.job.JobScheduler} instead of the process memory, so if
 * the process is killed the queued tasks will be restored. If a new task is added, a new {@link
 * TaskSchedulerJobService} will be scheduled to run the task. If the job is already scheduled, the
 * new task will be pushed into the queue of the scheduled job. If the job is already running, the
 * job will be queued in process memory.
 *
 * <p>Only one task will be ran at a time, and same task cannot exist in the queue at the same time.
 * Refer to {@link TaskQueue} for queuing and execution order.
 *
 * <p>If there are still tasks in the queue but none are executable immediately, the service will
 * enter a "sleep", pushing all remaining task into a new job and end the current job.
 *
 * <p>The executor will be started when {@link TaskSchedulerJobService} is running, and stopped when
 * there are no more tasks in the queue or when the executor is put to sleep.
 *
 * <p>{@link android.app.job.JobScheduler} is not used directly due to:
 *
 * <ul>
 *   <li>The {@link android.telecom.PhoneAccountHandle} used to differentiate task can not be easily
 *       mapped into an integer for job id
 *   <li>A job cannot be mutated to store information such as retry count.
 * </ul>
 */
@TargetApi(VERSION_CODES.O)
final class TaskExecutor {

  /**
   * An entity that holds execution resources for the {@link TaskExecutor} to run, usually a {@link
   * android.app.job.JobService}.
   */
  interface Job {

    /**
     * Signals to Job to end and release its' resources. This is an asynchronous call and may not
     * take effect immediately.
     */
    @MainThread
    void finishAsync();

    /** Whether the call to {@link #finishAsync()} has actually taken effect. */
    @MainThread
    boolean isFinished();
  }

  private static final String TAG = "VvmTaskExecutor";

  private static final int READY_TOLERANCE_MILLISECONDS = 100;

  /**
   * Threshold to determine whether to do a short or long sleep when a task is scheduled in the
   * future.
   *
   * <p>A short sleep will continue the job and use {@link Handler#postDelayed(Runnable, long)} to
   * wait for the next task.
   *
   * <p>A long sleep will finish the job and schedule a new one. The exact execution time is
   * subjected to {@link android.app.job.JobScheduler} battery optimization, and is not exact.
   */
  private static final int SHORT_SLEEP_THRESHOLD_MILLISECONDS = 10_000;
  /**
   * When there are no more tasks to be run the service should be stopped. But when all tasks has
   * finished there might still be more tasks in the message queue waiting to be processed,
   * especially the ones submitted in {@link Task#onCompleted()}. Wait for a while before stopping
   * the service to make sure there are no pending messages.
   */
  private static final int STOP_DELAY_MILLISECONDS = 5_000;

  /** Interval between polling of whether the job is finished. */
  private static final int TERMINATE_POLLING_INTERVAL_MILLISECONDS = 1_000;

  // The thread to run tasks on
  private final WorkerThreadHandler workerThreadHandler;

  private static TaskExecutor instance;

  /**
   * Used by tests to turn task handling into a single threaded process by calling {@link
   * Handler#handleMessage(Message)} directly
   */
  private MessageSender messageSender = new MessageSender();

  private final MainThreadHandler mainThreadHandler;

  private final Context context;

  /** Main thread only, access through {@link #getTasks()} */
  private final TaskQueue tasks = new TaskQueue();

  private boolean isWorkerThreadBusy = false;

  private boolean isTerminating = false;

  private Job job;

  private final Runnable stopServiceWithDelay =
      new Runnable() {
        @MainThread
        @Override
        public void run() {
          VvmLog.i(TAG, "Stopping service");
          if (!isJobRunning() || isTerminating()) {
            VvmLog.e(TAG, "Service already stopped");
            return;
          }
          scheduleJobAndTerminate(0, true);
        }
      };

  /**
   * Reschedule the {@link TaskSchedulerJobService} and terminate the executor when the {@link Job}
   * is truly finished. If the job is still not finished, this runnable will requeue itself on the
   * main thread. The requeue is only expected to happen a few times.
   */
  private class JobFinishedPoller implements Runnable {

    private final long delayMillis;
    private final boolean isNewJob;
    private int invocationCounter = 0;

    JobFinishedPoller(long delayMillis, boolean isNewJob) {
      this.delayMillis = delayMillis;
      this.isNewJob = isNewJob;
    }

    @Override
    public void run() {
      // The job should be finished relatively quickly. Assert to make sure this assumption is true.
      Assert.isTrue(invocationCounter < 10);
      invocationCounter++;
      if (job.isFinished()) {
        VvmLog.i("JobFinishedPoller.run", "Job finished");
        if (!getTasks().isEmpty()) {
          TaskSchedulerJobService.scheduleJob(
              context, serializePendingTasks(), delayMillis, isNewJob);
          tasks.clear();
        }
        terminate();
        return;
      }
      VvmLog.w("JobFinishedPoller.run", "Job still running");
      mainThreadHandler.postDelayed(this, TERMINATE_POLLING_INTERVAL_MILLISECONDS);
    }
  };

  /** Should attempt to run the next task when a task has finished or been added. */
  private boolean taskAutoRunDisabledForTesting = false;

  /** Handles execution of the background task in teh worker thread. */
  @VisibleForTesting
  final class WorkerThreadHandler extends Handler {

    public WorkerThreadHandler(Looper looper) {
      super(looper);
    }
    @Override
    @WorkerThread
    public void handleMessage(Message msg) {
      Assert.isNotMainThread();
      Task task = (Task) msg.obj;
      try {
        VvmLog.i(TAG, "executing task " + task);
        task.onExecuteInBackgroundThread();
      } catch (Throwable throwable) {
        VvmLog.e(TAG, "Exception while executing task " + task + ":", throwable);
      }

      Message schedulerMessage = mainThreadHandler.obtainMessage();
      schedulerMessage.obj = task;
      messageSender.send(schedulerMessage);
    }
  }

  /** Handles completion of the background task in the main thread. */
  @VisibleForTesting
  final class MainThreadHandler extends Handler {

    public MainThreadHandler(Looper looper) {
      super(looper);
    }

    @Override
    @MainThread
    public void handleMessage(Message msg) {
      Assert.isMainThread();
      Task task = (Task) msg.obj;
      getTasks().remove(task);
      task.onCompleted();
      isWorkerThreadBusy = false;
      if (!isJobRunning() || isTerminating()) {
        // TaskExecutor was terminated when the task is running in background, don't need to run the
        // next task or terminate again
        return;
      }
      maybeRunNextTask();
    }
  }

  /** Starts a new TaskExecutor. May only be called by {@link TaskSchedulerJobService}. */
  @MainThread
  static void createRunningInstance(Context context) {
    Assert.isMainThread();
    Assert.isTrue(instance == null);
    instance = new TaskExecutor(context);
  }

  /** @return the currently running instance, or {@code null} if the executor is not running. */
  @MainThread
  @Nullable
  static TaskExecutor getRunningInstance() {
    return instance;
  }

  private TaskExecutor(Context context) {
    this.context = context;
    HandlerThread thread = new HandlerThread("VvmTaskExecutor");
    thread.start();

    workerThreadHandler = new WorkerThreadHandler(thread.getLooper());
    mainThreadHandler = new MainThreadHandler(Looper.getMainLooper());
  }

  @VisibleForTesting
  void terminate() {
    VvmLog.i(TAG, "terminated");
    Assert.isMainThread();
    job = null;
    workerThreadHandler.getLooper().quit();
    instance = null;
    TaskReceiver.resendDeferredBroadcasts(context);
  }

  @MainThread
  void addTask(Task task) {
    Assert.isMainThread();
    getTasks().add(task);
    VvmLog.i(TAG, task + " added");
    mainThreadHandler.removeCallbacks(stopServiceWithDelay);
    maybeRunNextTask();
  }

  @MainThread
  @VisibleForTesting
  TaskQueue getTasks() {
    Assert.isMainThread();
    return tasks;
  }

  @MainThread
  private void maybeRunNextTask() {
    Assert.isMainThread();

    if (isWorkerThreadBusy) {
      return;
    }
    if (taskAutoRunDisabledForTesting) {
      // If taskAutoRunDisabledForTesting is true, runNextTask() must be explicitly called
      // to run the next task.
      return;
    }

    runNextTask();
  }

  @VisibleForTesting
  @MainThread
  void runNextTask() {
    Assert.isMainThread();
    if (getTasks().isEmpty()) {
      prepareStop();
      return;
    }
    NextTask nextTask = getTasks().getNextTask(READY_TOLERANCE_MILLISECONDS);

    if (nextTask.task != null) {
      nextTask.task.onBeforeExecute();
      Message message = workerThreadHandler.obtainMessage();
      message.obj = nextTask.task;
      isWorkerThreadBusy = true;
      messageSender.send(message);
      return;
    }
    VvmLog.i(TAG, "minimal wait time:" + nextTask.minimalWaitTimeMillis);
    if (!taskAutoRunDisabledForTesting && nextTask.minimalWaitTimeMillis != null) {
      // No tasks are currently ready. Sleep until the next one should be.
      // If a new task is added during the sleep the service will wake immediately.
      sleep(nextTask.minimalWaitTimeMillis);
    }
  }

  @MainThread
  private void sleep(long timeMillis) {
    VvmLog.i(TAG, "sleep for " + timeMillis + " millis");
    if (timeMillis < SHORT_SLEEP_THRESHOLD_MILLISECONDS) {
      mainThreadHandler.postDelayed(
          new Runnable() {
            @Override
            public void run() {
              maybeRunNextTask();
            }
          },
          timeMillis);
      return;
    }
    scheduleJobAndTerminate(timeMillis, false);
  }

  private List<Bundle> serializePendingTasks() {
    return getTasks().toBundles();
  }

  private void prepareStop() {
    VvmLog.i(
        TAG,
        "no more tasks, stopping service if no task are added in "
            + STOP_DELAY_MILLISECONDS
            + " millis");
    mainThreadHandler.postDelayed(stopServiceWithDelay, STOP_DELAY_MILLISECONDS);
  }

  @NeededForTesting
  static class MessageSender {

    public void send(Message message) {
      message.sendToTarget();
    }
  }

  @NeededForTesting
  void setTaskAutoRunDisabledForTest(boolean value) {
    taskAutoRunDisabledForTesting = value;
  }

  @NeededForTesting
  void setMessageSenderForTest(MessageSender sender) {
    messageSender = sender;
  }

  /**
   * The {@link TaskSchedulerJobService} has started and all queued task should be executed in the
   * worker thread.
   */
  @MainThread
  public void onStartJob(Job job, List<Bundle> pendingTasks) {
    VvmLog.i(TAG, "onStartJob");
    this.job = job;
    tasks.fromBundles(context, pendingTasks);
    maybeRunNextTask();
  }

  /**
   * The {@link TaskSchedulerJobService} is being terminated by the system (timeout or network
   * lost). A new job will be queued to resume all pending tasks. The current unfinished job may be
   * ran again.
   */
  @MainThread
  public void onStopJob() {
    VvmLog.e(TAG, "onStopJob");
    if (isJobRunning() && !isTerminating()) {
      scheduleJobAndTerminate(0, true);
    }
  }

  /**
   * Send all pending tasks and schedule a new {@link TaskSchedulerJobService}. The current executor
   * will start the termination process, but restarted when the scheduled job runs in the future.
   *
   * @param delayMillis the delay before stating the job, see {@link
   *     android.app.job.JobInfo.Builder#setMinimumLatency(long)}. This must be 0 if {@code
   *     isNewJob} is true.
   * @param isNewJob a new job will be requested to run immediately, bypassing all requirements.
   */
  @MainThread
  @VisibleForTesting
  void scheduleJobAndTerminate(long delayMillis, boolean isNewJob) {
    Assert.isMainThread();
    finishJobAsync();
    mainThreadHandler.post(new JobFinishedPoller(delayMillis, isNewJob));
  }

  /**
   * Whether the TaskExecutor is still terminating. {@link TaskReceiver} should defer all new task
   * until {@link #getRunningInstance()} returns {@code null} so a new job can be started. {@link
   * #scheduleJobAndTerminate(long, boolean)} does not run immediately because the job can only be
   * scheduled after the main thread has returned. The TaskExecutor will be in a intermediate state
   * between scheduleJobAndTerminate() and terminate(). In this state, {@link #getRunningInstance()}
   * returns non-null because it has not been fully stopped yet, but the TaskExecutor cannot do
   * anything. A new job should not be scheduled either because the current job might still be
   * running.
   */
  @MainThread
  public boolean isTerminating() {
    return isTerminating;
  }

  /**
   * Signals {@link TaskSchedulerJobService} the current session of tasks has finished, and the wake
   * lock can be released. Note: this only takes effect after the main thread has been returned. If
   * a new job need to be scheduled, it should be posted on the main thread handler instead of
   * calling directly.
   */
  @MainThread
  private void finishJobAsync() {
    Assert.isTrue(!isTerminating());
    Assert.isMainThread();
    VvmLog.i(TAG, "finishing Job");
    job.finishAsync();
    isTerminating = true;
    mainThreadHandler.removeCallbacks(stopServiceWithDelay);
  }

  private boolean isJobRunning() {
    return job != null;
  }
}
