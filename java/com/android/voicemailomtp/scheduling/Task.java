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

package com.android.voicemailomtp.scheduling;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.MainThread;
import android.support.annotation.WorkerThread;
import android.telecom.PhoneAccountHandle;

import java.util.Objects;

/**
 * A task for {@link TaskSchedulerService} to execute. Since the task is sent through a intent to
 * the scheduler, The task must be constructable with the intent. Specifically, It must have a
 * constructor with zero arguments, and have all relevant data packed inside the intent. Use {@link
 * TaskSchedulerService#createIntent(Context, Class)} to create a intent that will construct the
 * Task.
 *
 * <p>Only {@link #onExecuteInBackgroundThread()} is run on the worker thread.
 */
public interface Task {

    /**
     * TaskId to indicate it has not be set. If a task does not provide a default TaskId it should
     * be set before {@link Task#onCreate(Context, Intent, int, int) returns}
     */
    int TASK_INVALID = -1;

    /**
     * TaskId to indicate it should always be queued regardless of duplicates. {@link
     * Task#onDuplicatedTaskAdded(Task)} will never be called on tasks with this TaskId.
     */
    int TASK_ALLOW_DUPLICATES = -2;

    int TASK_UPLOAD = 1;
    int TASK_SYNC = 2;
    int TASK_ACTIVATION = 3;

    /**
     * Used to differentiate between types of tasks. If a task with the same TaskId is already in
     * the queue the new task will be rejected.
     */
    class TaskId {

        /**
         * Indicates the operation type of the task.
         */
        public final int id;
        /**
         * Same operation for a different phoneAccountHandle is allowed. phoneAccountHandle is used
         * to differentiate phone accounts in multi-SIM scenario. For example, each SIM can queue a
         * sync task for their own.
         */
        public final PhoneAccountHandle phoneAccountHandle;

        public TaskId(int id, PhoneAccountHandle phoneAccountHandle) {
            this.id = id;
            this.phoneAccountHandle = phoneAccountHandle;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof TaskId)) {
                return false;
            }
            TaskId other = (TaskId) object;
            return id == other.id && phoneAccountHandle.equals(other.phoneAccountHandle);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, phoneAccountHandle);
        }
    }

    TaskId getId();

    @MainThread
    void onCreate(Context context, Intent intent, int flags, int startId);

    /**
     * @return number of milliSeconds the scheduler should wait before running this task. A value
     * less than {@link TaskSchedulerService#READY_TOLERANCE_MILLISECONDS} will be considered ready.
     * If no tasks are ready, the scheduler will sleep for this amount of time before doing another
     * check (it will still wake if a new task is added). The first task in the queue that is ready
     * will be executed.
     */
    @MainThread
    long getReadyInMilliSeconds();

    /**
     * Called on the main thread when the scheduler is about to send the task into the worker
     * thread, calling {@link #onExecuteInBackgroundThread()}
     */
    @MainThread
    void onBeforeExecute();

    /**
     * The actual payload of the task, executed on the worker thread.
     */
    @WorkerThread
    void onExecuteInBackgroundThread();

    /**
     * Called on the main thread when {@link #onExecuteInBackgroundThread()} has finished or thrown
     * an uncaught exception. The task is already removed from the queue at this point, and a same
     * task can be queued again.
     */
    @MainThread
    void onCompleted();

    /**
     * Another task with the same TaskId has been added. Necessary data can be retrieved from the
     * other task, and after this returns the task will be discarded.
     */
    @MainThread
    void onDuplicatedTaskAdded(Task task);
}
