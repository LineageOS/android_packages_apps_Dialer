/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.incallui.maps;

import android.app.Application;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

/** Utility for getting a {@link StaticMapFactory} */
public class StaticMapBinding {

  @Nullable
  public static StaticMapFactory get(@NonNull Application application) {
    if (useTestingInstance) {
      return testingInstance;
    }
    if (application instanceof StaticMapFactory) {
      return ((StaticMapFactory) application);
    }
    return null;
  }

  private static StaticMapFactory testingInstance;
  private static boolean useTestingInstance;

  @VisibleForTesting
  public static void setForTesting(@Nullable StaticMapFactory staticMapFactory) {
    testingInstance = staticMapFactory;
    useTestingInstance = true;
  }

  @VisibleForTesting
  public static void clearForTesting() {
    useTestingInstance = false;
  }
}
