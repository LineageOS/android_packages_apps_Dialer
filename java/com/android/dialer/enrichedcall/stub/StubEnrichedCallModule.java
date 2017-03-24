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

package com.android.dialer.enrichedcall.stub;

import com.android.dialer.enrichedcall.EnrichedCallManager;
import com.android.dialer.enrichedcall.RcsVideoShareFactory;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

/** Module which binds {@link EnrichedCallManagerStub}. */
@Module
public class StubEnrichedCallModule {

  @Provides
  @Singleton
  static EnrichedCallManager provideEnrichedCallManager() {
    return new EnrichedCallManagerStub();
  }

  @Provides
  @Singleton
  static RcsVideoShareFactory providesRcsVideoShareFactory() {
    return (enrichedCallManager, videoTechListener, number) -> null;
  }

  private StubEnrichedCallModule() {}
}
