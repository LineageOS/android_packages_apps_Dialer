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

package com.android.dialer.strictmode.impl;

import android.app.Application;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.os.StrictMode.VmPolicy;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import com.android.dialer.common.Assert;
import com.android.dialer.strictmode.DialerStrictMode;
import com.android.dialer.strictmode.StrictModeUtils;
import com.google.auto.value.AutoValue;
import java.util.Map;
import javax.inject.Inject;

final class SystemDialerStrictMode implements DialerStrictMode {
  private static final VmPolicy VM_DEATH_PENALTY =
      new StrictMode.VmPolicy.Builder().penaltyLog().penaltyDeath().build();

  private static final ThreadPolicy THREAD_DEATH_PENALTY =
      new StrictMode.ThreadPolicy.Builder().penaltyLog().penaltyDeath().build();

  @Inject
  public SystemDialerStrictMode() {}

  @MainThread
  @Override
  public void onApplicationCreate(Application application) {
    if (StrictModeUtils.isStrictModeAllowed()) {
      StrictModeUtils.warmupSharedPrefs(application);
      setRecommendedMainThreadPolicy(THREAD_DEATH_PENALTY);
      setRecommendedVMPolicy(VM_DEATH_PENALTY);

      // Because Android resets StrictMode policies after Application.onCreate is done, we set it
      // again right after.
      // See cl/105932355 for the discussion.
      // See a bug for the public bug.
      Handler handler = new Handler(Looper.myLooper());
      handler.postAtFrontOfQueue(() -> setRecommendedMainThreadPolicy(THREAD_DEATH_PENALTY));
    }
  }

  /**
   * Set the recommended policy for the app.
   *
   * @param threadPenalties policy with preferred penalties. Detection bits will be ignored.
   */
  private static void setRecommendedMainThreadPolicy(StrictMode.ThreadPolicy threadPenalties) {
    StrictMode.ThreadPolicy threadPolicy =
        new StrictMode.ThreadPolicy.Builder(threadPenalties).detectAll().build();
    StrictMode.setThreadPolicy(threadPolicy);
  }

  /**
   * Set the recommended policy for the app.
   *
   * @param vmPenalties policy with preferred penalties. Detection bits should be unset.
   */
  private static void setRecommendedVMPolicy(StrictMode.VmPolicy vmPenalties) {
    setRecommendedVMPolicy(vmPenalties, StrictModeVmConfig.empty());
  }

  /**
   * Set the recommended policy for the app.
   *
   * @param vmPenalties policy with preferred penalties. Detection bits should be unset.
   */
  private static void setRecommendedVMPolicy(
      StrictMode.VmPolicy vmPenalties, StrictModeVmConfig config) {
    Assert.isNotNull(config);
    StrictMode.VmPolicy.Builder vmPolicyBuilder =
        new StrictMode.VmPolicy.Builder(vmPenalties)
            .detectLeakedClosableObjects()
            .detectLeakedSqlLiteObjects();
    if (Build.VERSION.SDK_INT >= VERSION_CODES.O) {
      vmPolicyBuilder.detectContentUriWithoutPermission();
      // TODO(azlatin): Enable detecting untagged sockets once: a bug is fixed.
      // vmPolicyBuilder.detectUntaggedSockets();
    }
    StrictMode.setVmPolicy(vmPolicyBuilder.build());
  }

  /** VmPolicy configuration. */
  @AutoValue
  abstract static class StrictModeVmConfig {
    /** A map of a class to the maximum number of allowed instances of that class. */
    @Nullable
    abstract Map<Class<?>, Integer> maxInstanceLimits();

    public static StrictModeVmConfig empty() {
      return builder().build();
    }

    public static Builder builder() {
      return new AutoValue_SystemDialerStrictMode_StrictModeVmConfig.Builder();
    }

    /** VmPolicy configuration builder. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setMaxInstanceLimits(Map<Class<?>, Integer> limits);

      public abstract StrictModeVmConfig build();

      Builder() {}
    }

    StrictModeVmConfig() {}
  }
}
