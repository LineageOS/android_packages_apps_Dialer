/*
 * Copyright (C) 2011 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.dialer.common.concurrent;

import androidx.annotation.MainThread;

import java.util.concurrent.Executor;

/**
 * Interface used to submit objects to run in the background.
 *
 * <p>This interface has a direct parallel with the {@link Executor} interface. It exists to
 * decouple the mechanics of AsyncTask submission from the description of how that AsyncTask will
 * execute.
 *
 * <p>One immediate benefit of this approach is that testing becomes much easier, since it is easy
 * to introduce a mock or fake AsyncTaskExecutor in unit/integration tests, and thus inspect which
 * tasks have been submitted and control their execution in an orderly manner.
 *
 * <p>Another benefit in due course will be the management of the submitted tasks. An extension to
 * this interface is planned to allow Activities to easily cancel all the submitted tasks that are
 * still pending in the onDestroy() method of the Activity.
 */
public interface AsyncTaskExecutor {

  @MainThread
  void submit(Object identifier, Runnable runnable);

  @MainThread
  void submit(Object identifier, Runnable runnable, Runnable postExecuteRunnable);
}
