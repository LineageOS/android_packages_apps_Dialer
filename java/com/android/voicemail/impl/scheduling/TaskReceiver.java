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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import com.android.voicemail.impl.VvmLog;
import java.util.ArrayList;
import java.util.List;

/**
 * BroadcastReceiver to queue and run {@link Task} with the {@link android.app.job.JobScheduler}. A
 * task is queued using a explicit broadcast to this receiver. The intent should contain enough
 * information in {@link Intent#getExtras()} to construct the task (see {@link
 * Tasks#createIntent(Context, Class)}). The task will be queued directly in {@link TaskExecutor} if
 * it is already running, or in {@link TaskSchedulerJobService} if not.
 */
@TargetApi(VERSION_CODES.O)
public class TaskReceiver extends BroadcastReceiver {

  private static final String TAG = "VvmTaskReceiver";

  private static final List<Intent> deferredBroadcasts = new ArrayList<>();

  /**
   * When {@link TaskExecutor#isTerminating()} is {@code true}, newly added tasks will be deferred
   * to allow the TaskExecutor to terminate properly. After termination is completed this should be
   * called to add the tasks again.
   */
  public static void resendDeferredBroadcasts(Context context) {
    for (Intent intent : deferredBroadcasts) {
      context.sendBroadcast(intent);
    }
    deferredBroadcasts.clear();
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent == null) {
      VvmLog.w(TAG, "null intent received");
      return;
    }
    VvmLog.i(TAG, "task received");
    TaskExecutor taskExecutor = TaskExecutor.getRunningInstance();
    if (taskExecutor != null) {
      VvmLog.i(TAG, "TaskExecutor already running");
      if (taskExecutor.isTerminating()) {
        // The current taskExecutor and cannot do anything and a new job cannot be scheduled. Defer
        // the task until a new job can be scheduled.
        VvmLog.w(TAG, "TaskExecutor is terminating, bouncing task");
        deferredBroadcasts.add(intent);
        return;
      }
      Task task = Tasks.createTask(context.getApplicationContext(), intent.getExtras());
      taskExecutor.addTask(task);
    } else {
      VvmLog.i(TAG, "scheduling new job");
      List<Bundle> taskList = new ArrayList<>();
      taskList.add(intent.getExtras());
      TaskSchedulerJobService.scheduleJob(context.getApplicationContext(), taskList, 0, true);
    }
  }
}
