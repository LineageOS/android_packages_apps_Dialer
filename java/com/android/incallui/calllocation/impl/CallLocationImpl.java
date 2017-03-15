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

package com.android.incallui.calllocation.impl;

import android.content.Context;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import com.android.dialer.common.Assert;
import com.android.incallui.calllocation.CallLocation;
import javax.inject.Inject;

/** Uses Google Play Services to show the user's location during an emergency call. */
public class CallLocationImpl implements CallLocation {

  private LocationHelper locationHelper;
  private LocationFragment locationFragment;

  @Inject
  public CallLocationImpl() {}

  @MainThread
  @Override
  public boolean canGetLocation(@NonNull Context context) {
    Assert.isMainThread();
    return LocationHelper.canGetLocation(context);
  }

  @MainThread
  @NonNull
  @Override
  public Fragment getLocationFragment(@NonNull Context context) {
    Assert.isMainThread();
    if (locationFragment == null) {
      locationFragment = new LocationFragment();
      locationHelper = new LocationHelper(context);
      locationHelper.addLocationListener(locationFragment.getPresenter());
    }
    return locationFragment;
  }

  @MainThread
  @Override
  public void close() {
    Assert.isMainThread();
    if (locationFragment != null) {
      locationHelper.removeLocationListener(locationFragment.getPresenter());
      locationHelper.close();
      locationFragment = null;
      locationHelper = null;
    }
  }
}
