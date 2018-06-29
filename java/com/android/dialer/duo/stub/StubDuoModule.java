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
 * limitations under the License.
 */

package com.android.dialer.duo.stub;

import com.android.dialer.duo.Duo;
import com.android.dialer.inject.DialerVariant;
import com.android.dialer.inject.InstallIn;
import dagger.Binds;
import dagger.Module;
import javax.inject.Singleton;

@Module
@InstallIn(variants = DialerVariant.DIALER_TEST)
public abstract class StubDuoModule {

  @Binds
  @Singleton
  public abstract Duo bindsDuo(DuoStub duoStub);
}
