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

import android.content.Context;
import android.support.annotation.NonNull;
import com.android.dialer.common.Assert;
import java.util.concurrent.ExecutorService;

/** Shared application executors. */
public final class DialerExecutors {

  /**
   * An application-wide thread pool used for low priority (non-UI) tasks.
   *
   * <p>This exists to prevent each individual dialer component from having to create its own
   * threads/pools, which would result in the application having more threads than really necessary.
   *
   * @param context any valid context object from which the application context can be retrieved
   */
  public static ExecutorService getLowPriorityThreadPool(@NonNull Context context) {
    return DialerExecutorComponent.get(Assert.isNotNull(context)).lowPriorityThreadPool();
  }
}
