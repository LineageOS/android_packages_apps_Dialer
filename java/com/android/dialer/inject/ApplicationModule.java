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
 * limitations under the License
 */

package com.android.dialer.inject;

import android.app.Application;
import android.support.annotation.NonNull;
import com.android.dialer.common.Assert;
import dagger.Module;
import dagger.Provides;

/** Provides the singleton application object. */
@Module
public final class ApplicationModule {

  @NonNull private final Application application;

  public ApplicationModule(@NonNull Application application) {
    this.application = Assert.isNotNull(application);
  }

  @Provides
  Application provideApplication() {
    return application;
  }
}
