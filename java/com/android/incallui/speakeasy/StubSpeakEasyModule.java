/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.incallui.speakeasy;

import android.preference.PreferenceActivity;
import com.android.dialer.inject.DialerVariant;
import com.android.dialer.inject.InstallIn;
import com.android.incallui.speakeasy.Annotations.SpeakEasyChipResourceId;
import com.android.incallui.speakeasy.Annotations.SpeakEasySettingsActivity;
import com.android.incallui.speakeasy.Annotations.SpeakEasySettingsObject;
import com.android.incallui.speakeasy.Annotations.SpeakEasyTextResourceId;
import com.google.common.base.Optional;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;

/** Module which binds {@link SpeakEasyCallManagerStub}. */
@InstallIn(variants = {DialerVariant.DIALER_TEST})
@Module
public abstract class StubSpeakEasyModule {

  @Binds
  abstract SpeakEasyCallManager bindsSpeakEasy(SpeakEasyCallManagerStub stub);

  @Provides
  static @SpeakEasySettingsActivity Optional<PreferenceActivity>
      provideSpeakEasySettingsActivity() {
    return Optional.absent();
  }

  @Provides
  static @SpeakEasySettingsObject Optional<Object> provideSpeakEasySettingsObject() {
    return Optional.absent();
  }

  @Provides
  static @SpeakEasyChipResourceId Optional<Integer> provideSpeakEasyChip() {
    return Optional.absent();
  }

  @Provides
  static @SpeakEasyTextResourceId Optional<Integer> provideSpeakEasyTextResource() {
    return Optional.absent();
  }
}
