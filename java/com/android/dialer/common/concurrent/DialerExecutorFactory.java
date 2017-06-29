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

package com.android.dialer.common.concurrent;

import android.app.FragmentManager;
import android.support.annotation.NonNull;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;

/**
 * Factory interface for creating {@link DialerExecutor} objects.
 *
 * <p>Factory instances may be used instead of the static methods in {@link DialerExecutors} in
 * order to improve testability.
 *
 * @see DialerExecutors
 */
public interface DialerExecutorFactory {

  /**
   * Must be called from onCreate of your activity or fragment.
   *
   * @param taskId used for the headless fragment ID and task ID
   * @param worker a function executed on a worker thread which accepts an {@link InputT} and
   *     returns an {@link OutputT}. It should ideally not be an inner class of your (meaning it
   *     should not be a lambda, anonymous, or non-static) but it can be a static nested class. The
   *     static nested class should not contain any reference to UI, including any activity or
   *     fragment or activity context, though it may reference some threadsafe system objects such
   *     as the application context.
   */
  @NonNull
  <InputT, OutputT> DialerExecutor.Builder<InputT, OutputT> createUiTaskBuilder(
      @NonNull FragmentManager fragmentManager,
      @NonNull String taskId,
      @NonNull Worker<InputT, OutputT> worker);

  /**
   * Create a task from a non-UI context.
   *
   * @param worker a function executed on a worker thread which accepts an {@link InputT} and
   *     returns an {@link OutputT}. It should ideally not be an inner class of your (meaning it
   *     should not be a lambda, anonymous, or non-static) but it can be a static nested class. The
   *     static nested class should not contain any reference to UI, including any activity or
   *     fragment or activity context, though it may reference some threadsafe system objects such
   *     as the application context.
   */
  @NonNull
  <InputT, OutputT> DialerExecutor.Builder<InputT, OutputT> createNonUiTaskBuilder(
      @NonNull Worker<InputT, OutputT> worker);
}
