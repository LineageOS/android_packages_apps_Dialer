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

package com.android.incallui.maps.impl;

import android.location.Location;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import com.android.incallui.maps.Maps;
import javax.inject.Inject;

/** Uses Google Play Services APIs to create a static map fragment. */
final class MapsImpl implements Maps {
  @Inject
  public MapsImpl() {}

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  @NonNull
  public Fragment createStaticMapFragment(@NonNull Location location) {
    return StaticMapFragment.newInstance(location);
  }
}
