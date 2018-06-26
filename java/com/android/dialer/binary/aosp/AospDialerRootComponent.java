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

import com.android.bubble.stub.StubBubbleModule;
import com.android.dialer.activecalls.ActiveCallsModule;
import com.android.dialer.binary.basecomponent.BaseDialerRootComponent;
import com.android.dialer.calllog.CallLogModule;
import com.android.dialer.calllog.config.CallLogConfigModule;
import com.android.dialer.commandline.CommandLineModule;
import com.android.dialer.common.concurrent.DialerExecutorModule;
import com.android.dialer.configprovider.SharedPrefConfigProviderModule;
import com.android.dialer.contacts.ContactsModule;
import com.android.dialer.duo.stub.StubDuoModule;
import com.android.dialer.enrichedcall.stub.StubEnrichedCallModule;
import com.android.dialer.feedback.stub.StubFeedbackModule;
import com.android.dialer.glidephotomanager.GlidePhotoManagerModule;
import com.android.dialer.inject.ContextModule;
import com.android.dialer.metrics.StubMetricsModule;
import com.android.dialer.phonelookup.PhoneLookupModule;
import com.android.dialer.phonenumbergeoutil.impl.PhoneNumberGeoUtilModule;
import com.android.dialer.precall.impl.PreCallModule;
import com.android.dialer.preferredsim.PreferredSimModule;
import com.android.dialer.preferredsim.suggestion.stub.StubSimSuggestionModule;
import com.android.dialer.promotion.impl.PromotionModule;
import com.android.dialer.simulator.impl.SimulatorModule;
import com.android.dialer.simulator.stub.StubSimulatorEnrichedCallModule;
import com.android.dialer.spam.stub.StubSpamModule;
import com.android.dialer.storage.StorageModule;
import com.android.dialer.strictmode.impl.SystemStrictModeModule;
import com.android.dialer.theme.base.impl.AospThemeModule;
import com.android.incallui.calllocation.stub.StubCallLocationModule;
import com.android.incallui.maps.stub.StubMapsModule;
import com.android.incallui.speakeasy.StubSpeakEasyModule;
import com.android.voicemail.impl.VoicemailModule;
import dagger.Component;
import javax.inject.Singleton;

/** Root component for the AOSP Dialer application. */
@Singleton
@Component(
    modules = {
      ActiveCallsModule.class,
      CallLogModule.class,
      CallLogConfigModule.class,
      CommandLineModule.class,
      ContactsModule.class,
      ContextModule.class,
      DialerExecutorModule.class,
      GlidePhotoManagerModule.class,
      PhoneLookupModule.class,
      PhoneNumberGeoUtilModule.class,
      PreCallModule.class,
      PreferredSimModule.class,
      PromotionModule.class,
      SharedPrefConfigProviderModule.class,
      SimulatorModule.class,
      StubSimulatorEnrichedCallModule.class,
      StorageModule.class,
      StubCallLocationModule.class,
      StubDuoModule.class,
      StubEnrichedCallModule.class,
      StubBubbleModule.class,
      StubMetricsModule.class,
      StubFeedbackModule.class,
      StubMapsModule.class,
      StubSimSuggestionModule.class,
      StubSpamModule.class,
      StubSpeakEasyModule.class,
      SystemStrictModeModule.class,
      AospThemeModule.class,
      VoicemailModule.class,
    })
public interface AospDialerRootComponent extends BaseDialerRootComponent {}
