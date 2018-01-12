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

package com.android.dialer.phonelookup;

import com.android.dialer.phonelookup.blockednumber.DialerBlockedNumberPhoneLookup;
import com.android.dialer.phonelookup.composite.CompositePhoneLookup;
import com.android.dialer.phonelookup.cp2.Cp2LocalPhoneLookup;
import com.android.dialer.phonelookup.cp2.Cp2RemotePhoneLookup;
import com.google.common.collect.ImmutableList;
import dagger.Module;
import dagger.Provides;

/** Dagger module which binds the PhoneLookup implementation. */
@Module
public abstract class PhoneLookupModule {

  @Provides
  @SuppressWarnings({"unchecked", "rawtype"})
  static ImmutableList<PhoneLookup> providePhoneLookupList(
      Cp2LocalPhoneLookup cp2LocalPhoneLookup,
      Cp2RemotePhoneLookup cp2RemotePhoneLookup,
      DialerBlockedNumberPhoneLookup dialerBlockedNumberPhoneLookup) {
    return ImmutableList.of(
        cp2LocalPhoneLookup, cp2RemotePhoneLookup, dialerBlockedNumberPhoneLookup);
  }

  @Provides
  static PhoneLookup<PhoneLookupInfo> providePhoneLookup(
      CompositePhoneLookup compositePhoneLookup) {
    return compositePhoneLookup;
  }
}
