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

import android.content.Intent;
import android.os.Bundle;
import com.android.voicemail.impl.scheduling.Task.TaskId;

/**
 * If a task with this policy succeeds, a {@link BlockerTask} with the same {@link TaskId} of the
 * task will be queued immediately, preventing the same task from running for a certain amount of
 * time.
 */
public class MinimalIntervalPolicy implements Policy {

  BaseTask mTask;
  TaskId mId;
  int mBlockForMillis;

  public MinimalIntervalPolicy(int blockForMillis) {
    mBlockForMillis = blockForMillis;
  }

  @Override
  public void onCreate(BaseTask task, Bundle extras) {
    mTask = task;
    mId = mTask.getId();
  }

  @Override
  public void onBeforeExecute() {}

  @Override
  public void onCompleted() {
    if (!mTask.hasFailed()) {
      Intent intent =
          BaseTask.createIntent(mTask.getContext(), BlockerTask.class, mId.phoneAccountHandle);
      intent.putExtra(BlockerTask.EXTRA_TASK_ID, mId.id);
      intent.putExtra(BlockerTask.EXTRA_BLOCK_FOR_MILLIS, mBlockForMillis);
      mTask.getContext().sendBroadcast(intent);
    }
  }

  @Override
  public void onFail() {}

  @Override
  public void onDuplicatedTaskAdded() {}
}
