/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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

package com.android.dialer.phonelookup;

import com.android.dialer.phonelookup.blockednumber.SystemBlockedNumberPhoneLookup;
import com.android.dialer.phonelookup.cequint.CequintPhoneLookup;
import com.android.dialer.phonelookup.cnap.CnapPhoneLookup;
import com.android.dialer.phonelookup.cp2.Cp2DefaultDirectoryPhoneLookup;
import com.android.dialer.phonelookup.cp2.Cp2ExtendedDirectoryPhoneLookup;
import com.android.dialer.phonelookup.emergency.EmergencyPhoneLookup;
import com.google.common.collect.ImmutableList;

import dagger.Module;
import dagger.Provides;

/** Dagger module which binds the PhoneLookup implementation. */
@Module
public abstract class PhoneLookupModule {

  @Provides
  @SuppressWarnings("rawtypes")
  static ImmutableList<PhoneLookup> providePhoneLookupList(
      CequintPhoneLookup cequintPhoneLookup,
      CnapPhoneLookup cnapPhoneLookup,
      Cp2DefaultDirectoryPhoneLookup cp2DefaultDirectoryPhoneLookup,
      Cp2ExtendedDirectoryPhoneLookup cp2ExtendedDirectoryPhoneLookup,
      EmergencyPhoneLookup emergencyPhoneLookup,
      SystemBlockedNumberPhoneLookup systemBlockedNumberPhoneLookup) {
    return ImmutableList.of(
        cequintPhoneLookup,
        cnapPhoneLookup,
        cp2DefaultDirectoryPhoneLookup,
        cp2ExtendedDirectoryPhoneLookup,
        emergencyPhoneLookup,
        systemBlockedNumberPhoneLookup);
  }
}
