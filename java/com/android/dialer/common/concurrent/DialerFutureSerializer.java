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

import com.google.common.util.concurrent.AsyncCallable;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.Executor;

/**
 * Serializes execution of a set of operations. This class guarantees that a submitted callable will
 * not be called before previously submitted callables have completed.
 */
public final class DialerFutureSerializer {

  /** Enqueues a task to run when the previous task (if any) completes. */
  public <T> ListenableFuture<T> submitAsync(final AsyncCallable<T> callable, Executor executor) {
    // TODO(zachh): This is just a dummy implementation until we fix guava API level issues.
    try {
      return callable.call();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }
}
