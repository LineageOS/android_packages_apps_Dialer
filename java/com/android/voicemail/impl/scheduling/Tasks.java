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
import android.content.Intent;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.support.annotation.NonNull;
import com.android.voicemail.impl.VvmLog;

/** Common operations on {@link Task} */
final class Tasks {

  private Tasks() {}

  static final String EXTRA_CLASS_NAME = "extra_class_name";

  /** The task cannot be created. */
  static final class TaskCreationException extends Exception {
    TaskCreationException(Throwable throwable) {
      super(throwable);
    }
  }

  /**
   * Create a task from a bundle. The bundle is created either with {@link #toBundle(Task)} or
   * {@link #createIntent(Context, Class)} from the target {@link Task}
   */
  @NonNull
  public static Task createTask(Context context, Bundle extras) throws TaskCreationException {
    // The extra contains custom parcelables which cannot be unmarshalled by the framework class
    // loader.
    extras.setClassLoader(context.getClassLoader());
    String className;
    try {
      className = extras.getString(EXTRA_CLASS_NAME);
    } catch (BadParcelableException e) {
      // BadParcelableException:Parcelable protocol requires that the class implements Parcelable
      // This happens when the task is submitted before an update, and can no longer be unparceled.
      throw new TaskCreationException(e);
    }
    VvmLog.i("Task.createTask", "create task:" + className);
    if (className == null) {
      throw new IllegalArgumentException("EXTRA_CLASS_NAME expected");
    }
    try {
      Task task = (Task) Class.forName(className).getDeclaredConstructor().newInstance();
      task.onCreate(context, extras);
      return task;
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Serializes necessary states to a bundle that can be used to restore the task with {@link
   * #createTask(Context, Bundle)}
   */
  public static Bundle toBundle(Task task) {
    Bundle result = task.toBundle();
    result.putString(EXTRA_CLASS_NAME, task.getClass().getName());
    return result;
  }

  /**
   * Create an intent that when called with {@link Context#startService(Intent)}, will queue the
   * <code>task</code>. Implementations of {@link Task} should use the result of this and fill in
   * necessary information.
   */
  public static Intent createIntent(Context context, Class<? extends Task> task) {
    Intent intent = new Intent(context, TaskReceiver.class);
    intent.setPackage(context.getPackageName());
    intent.putExtra(EXTRA_CLASS_NAME, task.getName());
    return intent;
  }
}
