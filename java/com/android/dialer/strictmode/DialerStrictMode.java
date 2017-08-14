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
import android.os.StrictMode.VmPolicy;
import com.android.dialer.buildtype.BuildType;

/** Enables strict mode for the application, and provides means of temporarily disabling it. */
public final class DialerStrictMode {

  /** Initializes strict mode on application start. */
  public static void onApplicationCreate() {
    enableDeathPenalty();
  }

  /**
   * Disables the strict mode death penalty. If strict mode is enabled for the build, warnings are
   * printed instead of the application crashing.
   *
   * <p>You should typically do this only temporarily and restore the death penalty in a finally
   * block using {@link #enableDeathPenalty()}.
   *
   * <p>The thread policy is only mutated if this is called from the main thread.
   */
  public static void disableDeathPenalty() {
    if (isStrictModeAllowed()) {
      if (onMainThread()) {
        StrictMode.setThreadPolicy(threadPolicyTemplate().build());
      }
      StrictMode.setVmPolicy(vmPolicyTemplate().build());
    }
  }

  /**
   * Restore the death penalty. This should typically be called in a finally block after calling
   * {@link #disableDeathPenalty()}.
   *
   * <p>The thread policy is only mutated if this is called from the main thread.
   */
  public static void enableDeathPenalty() {
    if (isStrictModeAllowed()) {
      if (onMainThread()) {
        StrictMode.setThreadPolicy(threadPolicyTemplate().penaltyDeath().build());
      }
      StrictMode.setVmPolicy(vmPolicyTemplate().penaltyDeath().build());
    }
  }

  private static ThreadPolicy.Builder threadPolicyTemplate() {
    return new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog();
  }

  private static VmPolicy.Builder vmPolicyTemplate() {
    return new StrictMode.VmPolicy.Builder().detectAll().penaltyLog();
  }

  private static boolean isStrictModeAllowed() {
    return BuildType.get() == BuildType.BUGFOOD;
  }

  private static boolean onMainThread() {
    return Looper.getMainLooper().equals(Looper.myLooper());
  }

  /** Functional interface intended to be used with {@link #bypass(Provider)}. */
  public interface Provider<T> {
    T get();
  }

  /**
   * Convenience method for disabling and enabling the death penalty using lambdas.
   *
   * <p>For example:
   *
   * <p><code>
   *   Value foo = DialerStrictMode.bypass(() -> doDiskAccessOnMainThreadReturningValue());
   * </code>
   *
   * <p>The thread policy is only mutated if this is called from the main thread.
   */
  public static <T> T bypass(Provider<T> provider) {
    disableDeathPenalty();
    try {
      return provider.get();
    } finally {
      enableDeathPenalty();
    }
  }

  /**
   * Convenience method for disabling and enabling the death penalty using lambdas.
   *
   * <p>For example:
   *
   * <p><code>
   *   DialerStrictMode.bypass(() -> doDiskAccessOnMainThread());
   * </code>
   */
  public static void bypass(Runnable runnable) {
    disableDeathPenalty();
    try {
      runnable.run();
    } finally {
      enableDeathPenalty();
    }
  }
}
