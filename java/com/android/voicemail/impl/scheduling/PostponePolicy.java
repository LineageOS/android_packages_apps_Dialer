/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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

import android.os.Bundle;

import com.android.voicemail.impl.VvmLog;

/**
 * A task with Postpone policy will not be executed immediately. It will wait for a while and if a
 * duplicated task is queued during the duration, the task will be postponed further. The task will
 * only be executed if no new task was added in postponeMillis. Useful to batch small tasks in quick
 * succession together.
 */
public class PostponePolicy implements Policy {

  private static final String TAG = "PostponePolicy";

  private final int postponeMillis;
  private BaseTask task;

  public PostponePolicy(int postponeMillis) {
    this.postponeMillis = postponeMillis;
  }

  @Override
  public void onCreate(BaseTask task, Bundle extras) {
    this.task = task;
    this.task.setExecutionTime(this.task.getTimeMillis() + postponeMillis);
  }

  @Override
  public void onBeforeExecute() {
    // Do nothing
  }

  @Override
  public void onCompleted() {
    // Do nothing
  }

  @Override
  public void onFail() {
    // Do nothing
  }

  @Override
  public void onDuplicatedTaskAdded() {
    if (task.hasStarted()) {
      return;
    }
    VvmLog.i(TAG, "postponing " + task);
    task.setExecutionTime(task.getTimeMillis() + postponeMillis);
  }
}
