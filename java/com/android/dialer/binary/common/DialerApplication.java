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

package com.android.dialer.binary.common;

import android.app.Application;
import android.os.Trace;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import com.android.dialer.blocking.BlockedNumbersAutoMigrator;
import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler;

/** A common application subclass for all Dialer build variants. */
public abstract class DialerApplication extends Application {

  private volatile Object rootComponent;

  @Override
  public void onCreate() {
    Trace.beginSection("DialerApplication.onCreate");
    super.onCreate();
    new BlockedNumbersAutoMigrator(
            this,
            PreferenceManager.getDefaultSharedPreferences(this),
            new FilteredNumberAsyncQueryHandler(this))
        .autoMigrate();
    Trace.endSection();
  }

}
