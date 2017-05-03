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

import android.content.Context;
import android.os.Bundle;
import com.android.dialer.proguard.UsedByReflection;
import com.android.voicemail.impl.VvmLog;

/** Task to block another task of the same ID from being queued for a certain amount of time. */
@UsedByReflection(value = "Tasks.java")
public class BlockerTask extends BaseTask {

  private static final String TAG = "BlockerTask";

  public static final String EXTRA_TASK_ID = "extra_task_id";
  public static final String EXTRA_BLOCK_FOR_MILLIS = "extra_block_for_millis";

  public BlockerTask() {
    super(TASK_INVALID);
  }

  @Override
  public void onCreate(Context context, Bundle extras) {
    super.onCreate(context, extras);
    setId(extras.getInt(EXTRA_TASK_ID, TASK_INVALID));
    setExecutionTime(getTimeMillis() + extras.getInt(EXTRA_BLOCK_FOR_MILLIS, 0));
  }

  @Override
  public void onExecuteInBackgroundThread() {
    // Do nothing.
  }

  @Override
  public void onDuplicatedTaskAdded(Task task) {
    VvmLog.i(TAG, task + "blocked, " + getReadyInMilliSeconds() + "millis remaining");
  }
}
