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

package com.android.dialer.buildtype;

import android.support.annotation.IntDef;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Utility to find out which build type the app is running as. */
public class BuildType {

  /** The type of build. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    BUGFOOD, FISHFOOD, DOGFOOD, RELEASE, TEST,
  })
  public @interface Type {}

  public static final int BUGFOOD = 1;
  public static final int FISHFOOD = 2;
  public static final int DOGFOOD = 3;
  public static final int RELEASE = 4;
  public static final int TEST = 5;

  private static int cachedBuildType;
  private static boolean didInitializeBuildType;

  @Type
  public static synchronized int get() {
    if (!didInitializeBuildType) {
      didInitializeBuildType = true;
      try {
        Class<?> clazz = Class.forName(BuildTypeAccessor.class.getName() + "Impl");
        BuildTypeAccessor accessorImpl = (BuildTypeAccessor) clazz.getConstructor().newInstance();
        cachedBuildType = accessorImpl.getBuildType();
      } catch (ReflectiveOperationException e) {
        LogUtil.e("BuildType.get", "error creating BuildTypeAccessorImpl", e);
        Assert.fail(
            "Unable to get build type. To fix this error include one of the build type "
                + "modules (bugfood, etc...) in your target.");
      }
    }
    return cachedBuildType;
  }

  private BuildType() {}
}
