/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.dialer.app;

import android.app.Application;
import android.os.Trace;
import android.preference.PreferenceManager;
import com.android.dialer.blocking.BlockedNumbersAutoMigrator;
import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler;
import com.android.dialer.enrichedcall.EnrichedCallManager;
import com.android.dialer.inject.ApplicationModule;
import com.android.dialer.inject.DaggerDialerAppComponent;
import com.android.dialer.inject.DialerAppComponent;

public class DialerApplication extends Application implements EnrichedCallManager.Factory {

  private static final String TAG = "DialerApplication";

  private volatile DialerAppComponent component;

  @Override
  public void onCreate() {
    Trace.beginSection(TAG + " onCreate");
    super.onCreate();
    new BlockedNumbersAutoMigrator(
            this,
            PreferenceManager.getDefaultSharedPreferences(this),
            new FilteredNumberAsyncQueryHandler(this))
        .autoMigrate();
    Trace.endSection();
  }

  @Override
  public EnrichedCallManager getEnrichedCallManager() {
    return component().enrichedCallManager();
  }

  protected DialerAppComponent buildApplicationComponent() {
    return DaggerDialerAppComponent.builder()
        .applicationModule(new ApplicationModule(this))
        .build();
  }

  /**
   * Returns the application component.
   *
   * <p>A single Component is created per application instance. Note that it won't be instantiated
   * until it's first requested, but guarantees that only one will ever be created.
   */
  private final DialerAppComponent component() {
    // Double-check idiom for lazy initialization
    DialerAppComponent result = component;
    if (result == null) {
      synchronized (this) {
        result = component;
        if (result == null) {
          component = result = buildApplicationComponent();
        }
      }
    }
    return result;
  }
}
