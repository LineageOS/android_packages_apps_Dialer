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
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.CallSuper;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.telecom.PhoneAccountHandle;
import com.android.dialer.proguard.UsedByReflection;
import com.android.voicemail.impl.Assert;
import com.android.voicemail.impl.NeededForTesting;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides common utilities for task implementations, such as execution time and managing {@link
 * Policy}
 */
@UsedByReflection(value = "Tasks.java")
public abstract class BaseTask implements Task {

  @VisibleForTesting
  public static final String EXTRA_PHONE_ACCOUNT_HANDLE = "extra_phone_account_handle";

  private static final String EXTRA_EXECUTION_TIME = "extra_execution_time";

  private Bundle extras;

  private Context context;

  private int id;
  private PhoneAccountHandle phoneAccountHandle;

  private boolean hasStarted;
  private volatile boolean hasFailed;

  @NonNull private final List<Policy> policies = new ArrayList<>();

  private long executionTime;

  private static Clock clock = new Clock();

  protected BaseTask(int id) {
    this.id = id;
    executionTime = getTimeMillis();
  }

  /**
   * Modify the task ID to prevent arbitrary task from executing. Can only be called before {@link
   * #onCreate(Context, Bundle)} returns.
   */
  @MainThread
  public void setId(int id) {
    Assert.isMainThread();
    this.id = id;
  }

  @MainThread
  public boolean hasStarted() {
    Assert.isMainThread();
    return hasStarted;
  }

  @MainThread
  public boolean hasFailed() {
    Assert.isMainThread();
    return hasFailed;
  }

  public Context getContext() {
    return context;
  }

  public PhoneAccountHandle getPhoneAccountHandle() {
    return phoneAccountHandle;
  }
  /**
   * Should be call in the constructor or {@link Policy#onCreate(BaseTask, Bundle)} will be missed.
   */
  @MainThread
  public BaseTask addPolicy(Policy policy) {
    Assert.isMainThread();
    policies.add(policy);
    return this;
  }

  /**
   * Indicate the task has failed. {@link Policy#onFail()} will be triggered once the execution
   * ends. This mechanism is used by policies for actions such as determining whether to schedule a
   * retry. Must be call inside {@link #onExecuteInBackgroundThread()}
   */
  @WorkerThread
  public void fail() {
    Assert.isNotMainThread();
    hasFailed = true;
  }

  /** @param timeMillis the time since epoch, in milliseconds. */
  @MainThread
  public void setExecutionTime(long timeMillis) {
    Assert.isMainThread();
    executionTime = timeMillis;
  }

  public long getTimeMillis() {
    return clock.getTimeMillis();
  }

  /**
   * Creates an intent that can be used to restart the current task. Derived class should build
   * their intent upon this.
   */
  public Intent createRestartIntent() {
    return createIntent(getContext(), this.getClass(), phoneAccountHandle);
  }

  /**
   * Creates an intent that can be used to be broadcast to the {@link TaskReceiver}. Derived class
   * should build their intent upon this.
   */
  public static Intent createIntent(
      Context context, Class<? extends BaseTask> task, PhoneAccountHandle phoneAccountHandle) {
    Intent intent = Tasks.createIntent(context, task);
    intent.putExtra(EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
    return intent;
  }

  @Override
  public TaskId getId() {
    return new TaskId(id, phoneAccountHandle);
  }

  @Override
  public Bundle toBundle() {
    extras.putLong(EXTRA_EXECUTION_TIME, executionTime);
    return extras;
  }

  @Override
  @CallSuper
  public void onCreate(Context context, Bundle extras) {
    this.context = context;
    this.extras = extras;
    phoneAccountHandle = extras.getParcelable(EXTRA_PHONE_ACCOUNT_HANDLE);
    for (Policy policy : policies) {
      policy.onCreate(this, extras);
    }
  }

  @Override
  @CallSuper
  public void onRestore(Bundle extras) {
    if (this.extras.containsKey(EXTRA_EXECUTION_TIME)) {
      executionTime = extras.getLong(EXTRA_EXECUTION_TIME);
    }
  }

  @Override
  public long getReadyInMilliSeconds() {
    return executionTime - getTimeMillis();
  }

  @Override
  @CallSuper
  public void onBeforeExecute() {
    for (Policy policy : policies) {
      policy.onBeforeExecute();
    }
    hasStarted = true;
  }

  @Override
  @CallSuper
  public void onCompleted() {
    if (hasFailed) {
      for (Policy policy : policies) {
        policy.onFail();
      }
    }

    for (Policy policy : policies) {
      policy.onCompleted();
    }
  }

  @Override
  public void onDuplicatedTaskAdded(Task task) {
    for (Policy policy : policies) {
      policy.onDuplicatedTaskAdded();
    }
  }

  @NeededForTesting
  static class Clock {

    public long getTimeMillis() {
      return SystemClock.elapsedRealtime();
    }
  }

  /** Used to replace the clock with an deterministic clock */
  @NeededForTesting
  static void setClockForTesting(Clock clock) {
    BaseTask.clock = clock;
  }
}
