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

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.os.StrictMode.VmPolicy;
import android.preference.PreferenceManager;
import android.support.annotation.AnyThread;
import android.support.annotation.MainThread;
import android.support.v4.os.UserManagerCompat;
import com.android.dialer.buildtype.BuildType;
import com.android.dialer.function.Supplier;
import com.android.dialer.util.DialerUtils;

/** Enables strict mode for the application, and provides means of temporarily disabling it. */
public final class DialerStrictMode {

  private static final VmPolicy VM_DEATH_PENALTY =
      new StrictMode.VmPolicy.Builder().penaltyLog().penaltyDeath().build();

  private static final ThreadPolicy THREAD_LOG_PENALTY =
      new StrictMode.ThreadPolicy.Builder().penaltyLog().build();
  private static final ThreadPolicy THREAD_DEATH_PENALTY =
      new StrictMode.ThreadPolicy.Builder().penaltyLog().penaltyDeath().build();

  private DialerStrictMode() {}

  /** Initializes strict mode on application start. */
  @MainThread
  public static void onApplicationCreate(Application application) {
    if (isStrictModeAllowed()) {
      warmupSharedPrefs(application);
      StrictModeUtils.setRecommendedMainThreadPolicy(THREAD_DEATH_PENALTY);
      StrictModeUtils.setRecommendedVMPolicy(VM_DEATH_PENALTY);

      // Because Android resets StrictMode policies after Application.onCreate is done, we set it
      // again right after.
      // See cl/105932355 for the discussion.
      // See b/36951662 for the public bug.
      Handler handler = new Handler(Looper.myLooper());
      handler.postAtFrontOfQueue(
          () -> StrictModeUtils.setRecommendedMainThreadPolicy(THREAD_DEATH_PENALTY));
    }
  }

  /**
   * We frequently access shared preferences on the main thread, which causes strict mode
   * violations. When strict mode is allowed, warm up the shared preferences so that later uses of
   * shared preferences access the in-memory versions and we don't have to bypass strict mode at
   * every point in the application where shared preferences are accessed.
   */
  private static void warmupSharedPrefs(Application application) {
    // From credential-encrypted (CE) storage, i.e.:
    //    /data/data/com.android.dialer/shared_prefs

    if (UserManagerCompat.isUserUnlocked(application)) {
      // <package_name>_preferences.xml
      PreferenceManager.getDefaultSharedPreferences(application);

      // <package_name>.xml
      application.getSharedPreferences(application.getPackageName(), Context.MODE_PRIVATE);
    }

    // From device-encrypted (DE) storage, i.e.:
    //   /data/user_de/0/com.android.dialer/shared_prefs/

    // <package_name>_preferences.xml
    DialerUtils.getDefaultSharedPreferenceForDeviceProtectedStorageContext(application);
  }

  private static boolean isStrictModeAllowed() {
    return BuildType.get() == BuildType.BUGFOOD;
  }

  private static boolean onMainThread() {
    return Looper.getMainLooper().equals(Looper.myLooper());
  }

  /**
   * Convenience method for disabling and enabling the thread policy death penalty using lambdas.
   *
   * <p>For example:
   *
   * <p><code>
   *   Value foo = DialerStrictMode.bypass(() -> doDiskAccessOnMainThreadReturningValue());
   * </code>
   *
   * <p>The thread policy is only mutated if this is called from the main thread.
   */
  @AnyThread
  public static <T> T bypass(Supplier<T> supplier) {
    if (isStrictModeAllowed() && onMainThread()) {
      ThreadPolicy originalPolicy = StrictMode.getThreadPolicy();
      StrictModeUtils.setRecommendedMainThreadPolicy(THREAD_LOG_PENALTY);
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
   *   DialerStrictMode.bypass(() -> doDiskAccessOnMainThread());
   * </code>
   *
   * <p>The thread policy is only mutated if this is called from the main thread.
   */
  @AnyThread
  public static void bypass(Runnable runnable) {
    if (isStrictModeAllowed() && onMainThread()) {
      ThreadPolicy originalPolicy = StrictMode.getThreadPolicy();
      StrictModeUtils.setRecommendedMainThreadPolicy(THREAD_LOG_PENALTY);
      try {
        runnable.run();
      } finally {
        StrictMode.setThreadPolicy(originalPolicy);
      }
    }
    runnable.run();
  }
}
