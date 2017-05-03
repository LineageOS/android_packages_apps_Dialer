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

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.voicemail.impl.Assert;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.scheduling.Task.TaskId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;

/**
 * A queue that manages priority and duplication of {@link Task}. A task is identified by a {@link
 * TaskId}, which consists of an integer representing the operation the task, and a {@link
 * android.telecom.PhoneAccountHandle} representing which SIM it is operated on.
 */
class TaskQueue implements Iterable<Task> {

  private final Queue<Task> queue = new ArrayDeque<>();

  public List<Bundle> toBundles() {
    List<Bundle> result = new ArrayList<>(queue.size());
    for (Task task : queue) {
      result.add(Tasks.toBundle(task));
    }
    return result;
  }

  public void fromBundles(Context context, List<Bundle> pendingTasks) {
    Assert.isTrue(queue.isEmpty());
    for (Bundle pendingTask : pendingTasks) {
      Task task = Tasks.createTask(context, pendingTask);
      task.onRestore(pendingTask);
      add(task);
    }
  }

  /**
   * Add a new task to the queue. A new task with a TaskId collision will be discarded, and {@link
   * Task#onDuplicatedTaskAdded(Task)} will be called on the existing task.
   *
   * @return {@code true} if the task is added, or {@code false} if the task is discarded due to
   *     collision.
   */
  public boolean add(Task task) {
    if (task.getId().id == Task.TASK_INVALID) {
      throw new AssertionError("Task id was not set to a valid value before adding.");
    }
    if (task.getId().id != Task.TASK_ALLOW_DUPLICATES) {
      Task oldTask = getTask(task.getId());
      if (oldTask != null) {
        oldTask.onDuplicatedTaskAdded(task);
        VvmLog.i("TaskQueue.add", "duplicated task added");
        return false;
      }
    }
    queue.add(task);
    return true;
  }

  public void remove(Task task) {
    queue.remove(task);
  }

  public Task getTask(TaskId id) {
    Assert.isMainThread();
    for (Task task : queue) {
      if (task.getId().equals(id)) {
        return task;
      }
    }
    return null;
  }

  /**
   * Packed return value of {@link #getNextTask(long)}. If a runnable task is found {@link
   * #minimalWaitTimeMillis} will be {@code null}. If no tasks is runnable {@link #task} will be
   * {@code null}, and {@link #minimalWaitTimeMillis} will contain the time to wait. If there are no
   * tasks at all both will be {@code null}.
   */
  static final class NextTask {
    @Nullable final Task task;
    @Nullable final Long minimalWaitTimeMillis;

    NextTask(@Nullable Task task, @Nullable Long minimalWaitTimeMillis) {
      this.task = task;
      this.minimalWaitTimeMillis = minimalWaitTimeMillis;
    }
  }

  /**
   * The next task is the first task with {@link Task#getReadyInMilliSeconds()} return a value less
   * then {@code readyToleranceMillis}, in insertion order. If no task matches this criteria, the
   * minimal value of {@link Task#getReadyInMilliSeconds()} is returned instead. If there are no
   * tasks at all, the minimalWaitTimeMillis will also be null.
   */
  @NonNull
  NextTask getNextTask(long readyToleranceMillis) {
    Long minimalWaitTime = null;
    for (Task task : queue) {
      long waitTime = task.getReadyInMilliSeconds();
      if (waitTime < readyToleranceMillis) {
        return new NextTask(task, 0L);
      } else {
        if (minimalWaitTime == null || waitTime < minimalWaitTime) {
          minimalWaitTime = waitTime;
        }
      }
    }
    return new NextTask(null, minimalWaitTime);
  }

  public void clear() {
    queue.clear();
  }

  public int size() {
    return queue.size();
  }

  public boolean isEmpty() {
    return queue.isEmpty();
  }

  @Override
  public Iterator<Task> iterator() {
    return queue.iterator();
  }
}
