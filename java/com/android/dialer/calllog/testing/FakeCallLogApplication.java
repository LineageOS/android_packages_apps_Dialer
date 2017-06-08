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
package com.android.dialer.calllog.testing;

import android.app.Application;
import com.android.dialer.calllog.CallLogModule;
import com.android.dialer.calllog.database.CallLogDatabaseComponent;
import com.android.dialer.inject.HasRootComponent;
import dagger.Component;
import javax.inject.Singleton;

/**
 * Fake application for call log robolectric tests which uses all real bindings but doesn't require
 * tests to depend on and use all of DialerApplication.
 */
public final class FakeCallLogApplication extends Application implements HasRootComponent {

  @Override
  public Object component() {
    return DaggerFakeCallLogApplication_FakeComponent.create();
  }

  @Singleton
  @Component(modules = CallLogModule.class)
  interface FakeComponent extends CallLogDatabaseComponent.HasComponent {}
}
