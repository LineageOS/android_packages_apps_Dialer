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

package com.android.incallui.maps.stub;

import android.location.Location;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import com.android.dialer.common.Assert;
import com.android.incallui.maps.Maps;
import dagger.Binds;
import dagger.Module;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Stub for the maps module for build variants that don't support Google Play Services. */
@Module
public abstract class StubMapsModule {

  @Binds
  @Singleton
  public abstract Maps bindMaps(StubMaps maps);

  static final class StubMaps implements Maps {
    @Inject
    public StubMaps() {}

    @Override
    public boolean isAvailable() {
      return false;
    }

    @NonNull
    @Override
    public Fragment createStaticMapFragment(@NonNull Location location) {
      throw Assert.createUnsupportedOperationFailException();
    }
  }
}
