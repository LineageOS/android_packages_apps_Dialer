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

package com.android.dialer.strictmode;

import android.os.Looper;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.support.annotation.AnyThread;
import com.android.dialer.buildtype.BuildType;
import com.android.dialer.function.Supplier;

/** Utilities for enforcing strict-mode in an app. */
public final class StrictModeUtils {

  private static final ThreadPolicy THREAD_NO_PENALTY =
      new StrictMode.ThreadPolicy.Builder().permitAll().build();

  /**
   * Convenience method for disabling and enabling the thread policy death penalty using lambdas.
   *
   * <p>For example:
   *
   * <p><code>
   *   Value foo = StrictModeUtils.bypass(() -> doDiskAccessOnMainThreadReturningValue());
   * </code>
   *
   * <p>The thread policy is only mutated if this is called from the main thread.
   */
  @AnyThread
  public static <T> T bypass(Supplier<T> supplier) {
    if (isStrictModeAllowed() && onMainThread()) {
      ThreadPolicy originalPolicy = StrictMode.getThreadPolicy();
      StrictMode.setThreadPolicy(THREAD_NO_PENALTY);
      try {
        return supplier.get();
      } finally {
        StrictMode.setThreadPolicy(originalPolicy);
      }
    }
    return supplier.get();
  }

  /**
   * Convenience method for disabling and enabling the thread policy death penalty using lambdas.
   *
   * <p>For example:
   *
   * <p><code>
   *   StrictModeUtils.bypass(() -> doDiskAccessOnMainThread());
   * </code>
   *
   * <p>The thread policy is only mutated if this is called from the main thread.
   */
  @AnyThread
  public static void bypass(Runnable runnable) {
    if (isStrictModeAllowed() && onMainThread()) {
      ThreadPolicy originalPolicy = StrictMode.getThreadPolicy();
      StrictMode.setThreadPolicy(THREAD_NO_PENALTY);
      try {
        runnable.run();
      } finally {
        StrictMode.setThreadPolicy(originalPolicy);
      }
    } else {
      runnable.run();
    }
  }

  public static boolean isStrictModeAllowed() {
    return BuildType.get() == BuildType.BUGFOOD;
  }

  private static boolean onMainThread() {
    return Looper.getMainLooper().equals(Looper.myLooper());
  }

  private StrictModeUtils() {}
}
