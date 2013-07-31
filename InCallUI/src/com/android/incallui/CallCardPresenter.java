/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.services.telephony.common.Call;

/**
 * Presenter for the Call Card Fragment.
 * This class listens for changes to CallList and passes it along to the fragment.
 */
public class CallCardPresenter extends Presenter<CallCardPresenter.CallCardUi>
        implements CallList.Listener {

    @Override
    public void onUiReady(CallCardUi ui) {
        super.onUiReady(ui);

        CallList.getInstance().addListener(this);
    }

    @Override
    public void onCallListChange(CallList callList) {
        final CallCardUi ui = getUi();

        // Populate the primary call card based on the incoming call or the active call.
        final Call call = callList.getIncomingOrActive();
        if (call != null) {
            ui.setNumber(call.getNumber());
        } else {
            // When there is no longer an incoming/active call, we need to reset everything
            // so that no data survives for the next call.
            ui.setNumber("");
        }

        // secondary call card info only comes from the background call (if any exist)
        final Call backgroundCall = callList.getBackgroundCall();
        if (backgroundCall != null) {
            ui.setSecondaryCallInfo(true, backgroundCall.getNumber());
        } else {
            ui.setSecondaryCallInfo(false, null);
        }
    }

    public interface CallCardUi extends Ui {
        // TODO(klp): Consider passing in the Call object directly in these methods.

        public void setNumber(String number);
        public void setName(String name);
        public void setSecondaryCallInfo(boolean show, String number);
    }
}
