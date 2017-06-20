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
 * limitations under the License.
 */

package com.android.dialer.constants;

import android.support.annotation.NonNull;
import com.android.dialer.common.Assert;
import com.android.dialer.proguard.UsedByReflection;

/**
 * Utility to access constants that are different across build variants (Google Dialer, AOSP,
 * etc...). This functionality depends on a an implementation being present in the app that has the
 * same package and the class name ending in "Impl". For example,
 * com.android.dialer.constants.ConstantsImpl. This class is found by the module using reflection.
 */
@UsedByReflection(value = "Constants.java")
public abstract class Constants {
  private static Constants instance;
  private static boolean didInitializeInstance;

  @NonNull
  public static synchronized Constants get() {
    if (!didInitializeInstance) {
      didInitializeInstance = true;
      try {
        Class<?> clazz = Class.forName(Constants.class.getName() + "Impl");
        instance = (Constants) clazz.getConstructor().newInstance();
      } catch (ReflectiveOperationException e) {
        Assert.fail(
            "Unable to create an instance of ConstantsImpl. To fix this error include one of the "
                + "constants modules (googledialer, aosp etc...) in your target.");
      }
    }
    return instance;
  }

  @NonNull
  public abstract String getFilteredNumberProviderAuthority();

  @NonNull
  public abstract String getFileProviderAuthority();

  @NonNull
  public abstract String getAnnotatedCallLogProviderAuthority();

  protected Constants() {}
}
