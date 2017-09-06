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

import android.os.Build;
import android.os.StrictMode;
import android.support.annotation.Nullable;
import com.android.dialer.common.Assert;
import com.google.auto.value.AutoValue;
import java.util.Map;
import java.util.Map.Entry;

/** Utilities for enforcing strict-mode in an app. */
final class StrictModeUtils {

  /**
   * Set the recommended policy for the app.
   *
   * @param threadPenalties policy with preferred penalties. Detection bits will be ignored.
   */
  static void setRecommendedMainThreadPolicy(StrictMode.ThreadPolicy threadPenalties) {
    StrictMode.ThreadPolicy threadPolicy =
        new StrictMode.ThreadPolicy.Builder(threadPenalties).detectAll().build();
    StrictMode.setThreadPolicy(threadPolicy);
  }

  /**
   * Set the recommended policy for the app.
   *
   * @param vmPenalties policy with preferred penalties. Detection bits should be unset.
   */
  static void setRecommendedVMPolicy(StrictMode.VmPolicy vmPenalties) {
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
    if (Build.VERSION.SDK_INT >= 16) {
      vmPolicyBuilder.detectLeakedRegistrationObjects();
    }
    if (Build.VERSION.SDK_INT >= 18) {
      vmPolicyBuilder.detectFileUriExposure();
    }
    if (Build.VERSION.SDK_INT >= 21) {
      // Even though this API is available earlier, it did not properly run finalizers.
      // This avoids lots of false positives.

      // TODO(zachh): Use LeakCanary and remove this line.
      vmPolicyBuilder.detectActivityLeaks();

      if (config.maxInstanceLimits() != null) {
        for (Entry<Class<?>, Integer> entry : config.maxInstanceLimits().entrySet()) {
          vmPolicyBuilder.setClassInstanceLimit(entry.getKey(), entry.getValue());
        }
      }
    }
    if (Build.VERSION.SDK_INT >= 23) {
      // TODO(azlatin): Enable clear-text check once b/36730713 is fixed.
      // vmPolicyBuilder.detectCleartextNetwork();
    }
    // Once OC Lands:
    // .detectContentUriWithoutPermission()
    // .detectUntaggedSockets()
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
      return new AutoValue_StrictModeUtils_StrictModeVmConfig.Builder();
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

  private StrictModeUtils() {}
}
