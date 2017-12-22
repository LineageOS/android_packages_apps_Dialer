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

package com.android.dialer.binary.aosp;

import com.android.dialer.binary.basecomponent.BaseDialerRootComponent;
import com.android.dialer.calllog.CallLogModule;
import com.android.dialer.common.concurrent.DialerExecutorModule;
import com.android.dialer.configprovider.SharedPrefConfigProviderModule;
import com.android.dialer.duo.stub.StubDuoModule;
import com.android.dialer.enrichedcall.stub.StubEnrichedCallModule;
import com.android.dialer.feedback.stub.StubFeedbackModule;
import com.android.dialer.inject.ContextModule;
import com.android.dialer.phonelookup.PhoneLookupModule;
import com.android.dialer.phonenumbergeoutil.impl.PhoneNumberGeoUtilModule;
import com.android.dialer.precall.impl.PreCallModule;
import com.android.dialer.preferredsim.suggestion.stub.StubSimSuggestionModule;
import com.android.dialer.simulator.impl.SimulatorModule;
import com.android.dialer.spam.StubSpamModule;
import com.android.dialer.storage.StorageModule;
import com.android.dialer.strictmode.impl.SystemStrictModeModule;
import com.android.incallui.calllocation.stub.StubCallLocationModule;
import com.android.incallui.maps.stub.StubMapsModule;
import com.android.voicemail.impl.VoicemailModule;
import dagger.Component;
import javax.inject.Singleton;

/** Root component for the AOSP Dialer application. */
@Singleton
@Component(
  modules = {
    CallLogModule.class,
    ContextModule.class,
    DialerExecutorModule.class,
    PhoneLookupModule.class,
    PhoneNumberGeoUtilModule.class,
    PreCallModule.class,
    SharedPrefConfigProviderModule.class,
    SimulatorModule.class,
    StorageModule.class,
    SystemStrictModeModule.class,
    StubCallLocationModule.class,
    StubDuoModule.class,
    StubEnrichedCallModule.class,
    StubMapsModule.class,
    VoicemailModule.class,
    StubSimSuggestionModule.class,
    StubFeedbackModule.class,
    StubSpamModule.class,
  }
)
public interface AospDialerRootComponent extends BaseDialerRootComponent {}
