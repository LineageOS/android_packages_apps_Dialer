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
package com.android.dialer.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.inject.DialerVariant;
import com.android.dialer.inject.InstallIn;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

/** Module for the storage component. */
@InstallIn(variants = {DialerVariant.DIALER_TEST})
@Module
public class StorageModule {

  @Provides
  @Singleton
  @Unencrypted
  static SharedPreferences provideUnencryptedSharedPrefs(@ApplicationContext Context appContext) {
    // #createDeviceProtectedStorageContext returns a new context each time, so we cache the shared
    // preferences object in order to avoid accessing disk for every operation.
    Context deviceProtectedContext = ContextCompat.createDeviceProtectedStorageContext(appContext);

    // ContextCompat.createDeviceProtectedStorageContext(context) returns null on pre-N, thus fall
    // back to regular default shared preference for pre-N devices since devices protected context
    // is not available.
    return PreferenceManager.getDefaultSharedPreferences(
        deviceProtectedContext != null ? deviceProtectedContext : appContext);
  }
}
