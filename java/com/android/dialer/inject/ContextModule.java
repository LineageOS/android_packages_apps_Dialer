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

package com.android.dialer.inject;

import android.content.Context;
import android.support.annotation.NonNull;
import dagger.Module;
import dagger.Provides;

/** Provides the singleton application context object. */
@Module
@InstallIn(variants = {DialerVariant.DIALER_DEMO, DialerVariant.DIALER_TEST})
public final class ContextModule {

  @NonNull private final Context context;

  public ContextModule(@NonNull Context appContext) {
    this.context = appContext;
  }

  @Provides
  @ApplicationContext
  Context provideContext() {
    return context;
  }
}
