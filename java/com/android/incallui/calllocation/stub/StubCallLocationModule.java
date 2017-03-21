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

package com.android.incallui.calllocation.stub;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import com.android.dialer.common.Assert;
import com.android.incallui.calllocation.CallLocation;
import dagger.Binds;
import dagger.Module;
import javax.inject.Inject;

/** This module provides an instance of call location. */
@Module
public abstract class StubCallLocationModule {

  @Binds
  public abstract CallLocation bindCallLocation(StubCallLocation callLocation);

  static class StubCallLocation implements CallLocation {
    @Inject
    StubCallLocation() {}

    @Override
    public boolean canGetLocation(@NonNull Context context) {
      return false;
    }

    @Override
    @NonNull
    public Fragment getLocationFragment(@NonNull Context context) {
      throw Assert.createUnsupportedOperationFailException();
    }

    @Override
    public void close() {
    }
  }
}
