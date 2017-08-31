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

import android.os.Handler;
import android.os.Looper;

/** Application-wide utility methods for working with threads. */
public class ThreadUtil {
  private static volatile Handler mainThreadHandler;

  /** Posts a runnable to the UI thread. */
  public static void postOnUiThread(Runnable runnable) {
    getUiThreadHandler().post(runnable);
  }

  /** Posts a runnable to the UI thread, to be run after the specified amount of time elapses. */
  public static void postDelayedOnUiThread(Runnable runnable, long delayMillis) {
    getUiThreadHandler().postDelayed(runnable, delayMillis);
  }

  /** Gets a handler which uses the main looper. */
  public static Handler getUiThreadHandler() {
    if (mainThreadHandler == null) {
      mainThreadHandler = new Handler(Looper.getMainLooper());
    }
    return mainThreadHandler;
  }
}
