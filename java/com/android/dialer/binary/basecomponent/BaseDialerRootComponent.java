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

package com.android.dialer.binary.basecomponent;

import com.android.dialer.calllog.CallLogComponent;
import com.android.dialer.calllog.database.CallLogDatabaseComponent;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.configprovider.ConfigProviderComponent;
import com.android.dialer.enrichedcall.EnrichedCallComponent;
import com.android.dialer.lightbringer.LightbringerComponent;
import com.android.dialer.main.MainComponent;
import com.android.dialer.simulator.SimulatorComponent;
import com.android.incallui.calllocation.CallLocationComponent;
import com.android.incallui.maps.MapsComponent;
import com.android.voicemail.VoicemailComponent;

/**
 * Base class for the core application-wide component. All variants of the Dialer app should extend
 * from this component.
 */
public interface BaseDialerRootComponent
    extends CallLocationComponent.HasComponent,
        CallLogComponent.HasComponent,
        CallLogDatabaseComponent.HasComponent,
        ConfigProviderComponent.HasComponent,
        DialerExecutorComponent.HasComponent,
        MainComponent.HasComponent,
        EnrichedCallComponent.HasComponent,
        MapsComponent.HasComponent,
        SimulatorComponent.HasComponent,
        VoicemailComponent.HasComponent,
        LightbringerComponent.HasComponent {}
