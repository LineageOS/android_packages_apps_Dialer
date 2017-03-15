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

package com.android.incallui.maps.testing;

import android.support.annotation.Nullable;
import com.android.incallui.maps.Maps;
import dagger.Module;
import dagger.Provides;

/** This module provides a instance of maps for testing. */
@Module
public final class TestMapsModule {

  @Nullable private static Maps maps;

  public static void setMaps(@Nullable Maps maps) {
    TestMapsModule.maps = maps;
  }

  @Provides
  static Maps getMaps() {
    return maps;
  }

  private TestMapsModule() {}
}
