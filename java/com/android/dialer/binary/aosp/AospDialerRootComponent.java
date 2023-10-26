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

package com.android.dialer.binary.aosp;

import com.android.dialer.activecalls.ActiveCallsModule;
import com.android.dialer.binary.basecomponent.BaseDialerRootComponent;
import com.android.dialer.calllog.CallLogModule;
import com.android.dialer.common.concurrent.DialerExecutorModule;
import com.android.dialer.contacts.ContactsModule;
import com.android.dialer.glidephotomanager.GlidePhotoManagerModule;
import com.android.dialer.inject.ContextModule;
import com.android.dialer.phonelookup.PhoneLookupModule;
import com.android.dialer.phonenumbergeoutil.impl.PhoneNumberGeoUtilModule;
import com.android.dialer.precall.impl.PreCallModule;
import com.android.dialer.preferredsim.PreferredSimModule;
import com.android.dialer.preferredsim.suggestion.stub.StubSimSuggestionModule;
import com.android.dialer.promotion.impl.PromotionModule;
import com.android.dialer.simulator.impl.SimulatorModule;
import com.android.dialer.storage.StorageModule;
import com.android.dialer.theme.base.impl.AospThemeModule;
import com.android.voicemail.impl.VoicemailModule;

import javax.inject.Singleton;

import dagger.Component;

/** Root component for the AOSP Dialer application. */
@Singleton
@Component(
    modules = {
      ActiveCallsModule.class,
      CallLogModule.class,
      ContactsModule.class,
      ContextModule.class,
      DialerExecutorModule.class,
      GlidePhotoManagerModule.class,
      PhoneLookupModule.class,
      PhoneNumberGeoUtilModule.class,
      PreCallModule.class,
      PreferredSimModule.class,
      PromotionModule.class,
      SimulatorModule.class,
      StorageModule.class,
      StubSimSuggestionModule.class,
      AospThemeModule.class,
      VoicemailModule.class,
    })
public interface AospDialerRootComponent extends BaseDialerRootComponent {}
