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

package com.android.dialer.precall.impl;

import com.android.dialer.inject.DialerVariant;
import com.android.dialer.inject.InstallIn;
import com.android.dialer.precall.PreCall;
import com.android.dialer.precall.PreCallAction;
import com.google.common.collect.ImmutableList;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

/** Dagger module for {@link PreCall}. */
@InstallIn(variants = {DialerVariant.DIALER_TEST})
@Module
public abstract class PreCallModule {

  private PreCallModule() {}

  @Binds
  @Singleton
  public abstract PreCall to(PreCallImpl impl);

  @Provides
  public static ImmutableList<PreCallAction> provideActions(
      DuoAction duoAction, CallingAccountSelector callingAccountSelector) {
    return ImmutableList.of(
        new PermissionCheckAction(),
        new MalformedNumberRectifier(
            ImmutableList.of(new UkRegionPrefixInInternationalFormatHandler())),
        callingAccountSelector,
        duoAction,
        new AssistedDialAction());
  }
}
