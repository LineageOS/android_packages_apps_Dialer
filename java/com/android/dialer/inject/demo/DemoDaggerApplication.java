/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.inject.demo;

import android.app.Application;
import android.support.annotation.NonNull;
import com.android.dialer.inject.ContextModule;
import com.android.dialer.inject.DialerRootComponent;
import com.android.dialer.inject.DialerVariant;
import com.android.dialer.inject.HasRootComponent;

/** Demo dialer dagger application. */
@DialerRootComponent(variant = DialerVariant.DIALER_DEMO)
public final class DemoDaggerApplication extends Application implements HasRootComponent {

  private volatile Object rootComponent;

  /** Returns a cached instance of application's root component. */
  @Override
  @NonNull
  public final Object component() {
    Object result = rootComponent;
    if (result == null) {
      synchronized (this) {
        result = rootComponent;
        if (result == null) {
          rootComponent =
              result = DaggerDialerDemo.builder().contextModule(new ContextModule(this)).build();
        }
      }
    }
    return result;
  }

  @Override
  public void onCreate() {
    super.onCreate();

    DemoSubcomponent.get(this).demoObjects();
  }
}
