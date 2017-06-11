/*
 * Copyright (C) 2016 The Android Open Source Project
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
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
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
 * A service to queue and run {@link Task} with the {@link android.app.job.JobScheduler}. A task is
 * queued using {@link Context#startService(Intent)}. The intent should contain enough information
 * in {@link Intent#getExtras()} to construct the task (see {@link Tasks#createIntent(Context,
 * Class)}).
 *
 * <p>All tasks are ran in the background with a wakelock being held by the {@link
 * android.app.job.JobScheduler}, which is between {@link #onStartJob(Job, List)} and {@link
 * #finishJob()}. The {@link TaskSchedulerJobService} also has a {@link TaskQueue}, but the data is
 * stored in the {@link android.app.job.JobScheduler} instead of the process memory, so if the
 * process is killed the queued tasks will be restored. If a new task is added, a new {@link
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
 * <p>The service will be started when a intent is received, and stopped when there are no more
 * tasks in the queue.
 *
 * <p>{@link android.app.job.JobScheduler} is not used directly due to:
 *
 * <ul>
 *   <li>The {@link android.telecom.PhoneAccountHandle} used to differentiate task can not be easily
 *       mapped into an integer for job id
 *   <li>A job cannot be mutated to store information such as retry count.
 * </ul>
 */
@SuppressWarnings("AndroidApiChecker") /* stream() */
@TargetApi(VERSION_CODES.O)
public class TaskSchedulerService extends Service {

  interface Job {
    void finish();
  }

  private static final String TAG = "VvmTaskScheduler";

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

  // The thread to run tasks on
  private volatile WorkerThreadHandler mWorkerThreadHandler;

  /**
   * Used by tests to turn task handling into a single threaded process by calling {@link
   * Handler#handleMessage(Message)} directly
   */
  private MessageSender mMessageSender = new MessageSender();

  private MainThreadHandler mMainThreadHandler;

  // Binder given to clients
  private final IBinder mBinder = new LocalBinder();

  /** Main thread only, access through {@link #getTasks()} */
  private final TaskQueue mTasks = new TaskQueue();

  private boolean mWorkerThreadIsBusy = false;

  private Job mJob;

  private final Runnable mStopServiceWithDelay =
      new Runnable() {
        @MainThread
        @Override
        public void run() {
          VvmLog.i(TAG, "Stopping service");
          finishJob();
          stopSelf();
        }
      };

  /** Should attempt to run the next task when a task has finished or been added. */
  private boolean mTaskAutoRunDisabledForTesting = false;

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

      Message schedulerMessage = mMainThreadHandler.obtainMessage();
      schedulerMessage.obj = task;
      mMessageSender.send(schedulerMessage);
    }
  }

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
      mWorkerThreadIsBusy = false;
      maybeRunNextTask();
    }
  }

  @Override
  @MainThread
  public void onCreate() {
    super.onCreate();
    HandlerThread thread = new HandlerThread("VvmTaskSchedulerService");
    thread.start();

    mWorkerThreadHandler = new WorkerThreadHandler(thread.getLooper());
    mMainThreadHandler = new MainThreadHandler(Looper.getMainLooper());
  }

  @Override
  public void onDestroy() {
    mWorkerThreadHandler.getLooper().quit();
  }

  @Override
  @MainThread
  public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
    Assert.isMainThread();
    if (intent == null) {
      VvmLog.w(TAG, "null intent received");
      return START_NOT_STICKY;
    }
    Task task = Tasks.createTask(this, intent.getExtras());
    Assert.isTrue(task != null);
    addTask(task);

    mMainThreadHandler.removeCallbacks(mStopServiceWithDelay);
    VvmLog.i(TAG, "task added");
    if (mJob == null) {
      scheduleJob(0, true);
    } else {
      maybeRunNextTask();
    }
    // STICKY means the service will be automatically restarted will the last intent if it is
    // killed.
    return START_NOT_STICKY;
  }

  @MainThread
  @VisibleForTesting
  void addTask(Task task) {
    Assert.isMainThread();
    getTasks().add(task);
  }

  @MainThread
  @VisibleForTesting
  TaskQueue getTasks() {
    Assert.isMainThread();
    return mTasks;
  }

  @MainThread
  private void maybeRunNextTask() {
    Assert.isMainThread();
    if (mWorkerThreadIsBusy) {
      return;
    }
    if (mTaskAutoRunDisabledForTesting) {
      // If mTaskAutoRunDisabledForTesting is true, runNextTask() must be explicitly called
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
      Message message = mWorkerThreadHandler.obtainMessage();
      message.obj = nextTask.task;
      mWorkerThreadIsBusy = true;
      mMessageSender.send(message);
      return;
    }
    VvmLog.i(TAG, "minimal wait time:" + nextTask.minimalWaitTimeMillis);
    if (!mTaskAutoRunDisabledForTesting && nextTask.minimalWaitTimeMillis != null) {
      // No tasks are currently ready. Sleep until the next one should be.
      // If a new task is added during the sleep the service will wake immediately.
      sleep(nextTask.minimalWaitTimeMillis);
    }
  }

  @MainThread
  private void sleep(long timeMillis) {
    VvmLog.i(TAG, "sleep for " + timeMillis + " millis");
    if (timeMillis < SHORT_SLEEP_THRESHOLD_MILLISECONDS) {
      mMainThreadHandler.postDelayed(
          new Runnable() {
            @Override
            public void run() {
              maybeRunNextTask();
            }
          },
          timeMillis);
      return;
    }
    finishJob();
    mMainThreadHandler.post(() -> scheduleJob(timeMillis, false));
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
    mMainThreadHandler.postDelayed(mStopServiceWithDelay, STOP_DELAY_MILLISECONDS);
  }

  @NeededForTesting
  static class MessageSender {

    public void send(Message message) {
      message.sendToTarget();
    }
  }

  @NeededForTesting
  void setTaskAutoRunDisabledForTest(boolean value) {
    mTaskAutoRunDisabledForTesting = value;
  }

  @NeededForTesting
  void setMessageSenderForTest(MessageSender sender) {
    mMessageSender = sender;
  }

  /**
   * The {@link TaskSchedulerJobService} has started and all queued task should be executed in the
   * worker thread.
   */
  @MainThread
  public void onStartJob(Job job, List<Bundle> pendingTasks) {
    VvmLog.i(TAG, "onStartJob");
    mJob = job;
    mTasks.fromBundles(this, pendingTasks);
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
    if (isJobRunning()) {
      finishJob();
      mMainThreadHandler.post(() -> scheduleJob(0, true));
    }
  }

  /**
   * Serializes all pending tasks and schedule a new {@link TaskSchedulerJobService}.
   *
   * @param delayMillis the delay before stating the job, see {@link
   *     android.app.job.JobInfo.Builder#setMinimumLatency(long)}. This must be 0 if {@code
   *     isNewJob} is true.
   * @param isNewJob a new job will be requested to run immediately, bypassing all requirements.
   */
  @MainThread
  private void scheduleJob(long delayMillis, boolean isNewJob) {
    Assert.isMainThread();
    TaskSchedulerJobService.scheduleJob(this, serializePendingTasks(), delayMillis, isNewJob);
    mTasks.clear();
  }

  /**
   * Signals {@link TaskSchedulerJobService} the current session of tasks has finished, and the wake
   * lock can be released. Note: this only takes effect after the main thread has been returned. If
   * a new job need to be scheduled, it should be posted on the main thread handler instead of
   * calling directly.
   */
  @MainThread
  private void finishJob() {
    Assert.isMainThread();
    VvmLog.i(TAG, "finishing Job");
    mJob.finish();
    mJob = null;
  }

  @Override
  @Nullable
  public IBinder onBind(Intent intent) {
    return mBinder;
  }

  @NeededForTesting
  class LocalBinder extends Binder {

    @NeededForTesting
    public TaskSchedulerService getService() {
      return TaskSchedulerService.this;
    }
  }

  private boolean isJobRunning() {
    return mJob != null;
  }
}
