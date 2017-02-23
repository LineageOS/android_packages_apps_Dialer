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
 * limitations under the License.
 */

package com.android.dialer.app;

import android.content.Context;
import com.android.dialer.app.bindings.DialerBindings;
import com.android.dialer.app.bindings.DialerBindingsFactory;
import com.android.dialer.app.bindings.DialerBindingsStub;
import com.android.dialer.app.legacybindings.DialerLegacyBindings;
import com.android.dialer.app.legacybindings.DialerLegacyBindingsFactory;
import com.android.dialer.app.legacybindings.DialerLegacyBindingsStub;
import java.util.Objects;

/** Accessor for the in call UI bindings. */
public class Bindings {

  private static DialerBindings instance;
  private static DialerLegacyBindings legacyInstance;

  private Bindings() {}

  public static DialerBindings get(Context context) {
    Objects.requireNonNull(context);
    if (instance != null) {
      return instance;
    }

    Context application = context.getApplicationContext();
    if (application instanceof DialerBindingsFactory) {
      instance = ((DialerBindingsFactory) application).newDialerBindings();
    }

    if (instance == null) {
      instance = new DialerBindingsStub();
    }
    return instance;
  }

  public static DialerLegacyBindings getLegacy(Context context) {
    Objects.requireNonNull(context);
    if (legacyInstance != null) {
      return legacyInstance;
    }

    Context application = context.getApplicationContext();
    if (application instanceof DialerLegacyBindingsFactory) {
      legacyInstance = ((DialerLegacyBindingsFactory) application).newDialerLegacyBindings();
    }

    if (legacyInstance == null) {
      legacyInstance = new DialerLegacyBindingsStub();
    }
    return legacyInstance;
  }

  public static void setForTesting(DialerBindings testInstance) {
    instance = testInstance;
  }

  public static void setLegacyBindingForTesting(DialerLegacyBindings testLegacyInstance) {
    legacyInstance = testLegacyInstance;
  }
}
