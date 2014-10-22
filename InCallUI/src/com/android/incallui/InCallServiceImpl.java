/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.incallui;

import android.telecom.InCallService;
import android.telecom.Phone;

/**
 * Used to receive updates about calls from the Telecomm component.  This service is bound to
 * Telecomm while there exist calls which potentially require UI. This includes ringing (incoming),
 * dialing (outgoing), and active calls. When the last call is disconnected, Telecomm will unbind to
 * the service triggering InCallActivity (via CallList) to finish soon after.
 */
public class InCallServiceImpl extends InCallService {

    @Override
    public void onPhoneCreated(Phone phone) {
        Log.v(this, "onPhoneCreated");
        CallList.getInstance().setPhone(phone);
        AudioModeProvider.getInstance().setPhone(phone);
        TelecomAdapter.getInstance().setPhone(phone);
        InCallPresenter.getInstance().setPhone(phone);
        InCallPresenter.getInstance().setUp(
                getApplicationContext(),
                CallList.getInstance(),
                AudioModeProvider.getInstance());
        TelecomAdapter.getInstance().setContext(InCallServiceImpl.this);
    }

    @Override
    public void onPhoneDestroyed(Phone phone) {
        Log.v(this, "onPhoneDestroyed");
        // Tear down the InCall system
        CallList.getInstance().clearPhone();
        AudioModeProvider.getInstance().clearPhone();
        TelecomAdapter.getInstance().clearPhone();
        TelecomAdapter.getInstance().setContext(null);
        CallList.getInstance().clearOnDisconnect();
        InCallPresenter.getInstance().tearDown();
    }
}
