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

package com.android.dialer.precall.testing;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.precall.PreCall;
import com.android.dialer.precall.PreCallAction;
import com.google.common.collect.ImmutableList;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

/** Provides test implementation of {@link PreCall} */
@Module
public class TestPreCallModule {
  private static PreCall preCall = new StubPreCall();

  public static void setPreCall(PreCall preCall) {
    TestPreCallModule.preCall = preCall;
  }

  @Provides
  @Singleton
  public static PreCall providePreCall() {
    return preCall;
  }

  private static class StubPreCall implements PreCall {

    @NonNull
    @Override
    public ImmutableList<PreCallAction> getActions() {
      return ImmutableList.of();
    }

    @NonNull
    @Override
    public Intent buildIntent(Context context, CallIntentBuilder builder) {
      return builder.build();
    }
  }
}
