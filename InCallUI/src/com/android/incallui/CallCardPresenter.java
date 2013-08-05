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

import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.services.telephony.common.Call;

/**
 * Presenter for the Call Card Fragment.
 * This class listens for changes to InCallState and passes it along to the fragment.
 */
public class CallCardPresenter extends Presenter<CallCardPresenter.CallCardUi>
        implements InCallStateListener {

    @Override
    public void onUiReady(CallCardUi ui) {
        super.onUiReady(ui);
    }

    @Override
    public void onStateChange(InCallState state, CallList callList) {
        final CallCardUi ui = getUi();

        Call primary = null;
        Call secondary = null;

        if (state == InCallState.INCOMING) {
            primary = callList.getIncomingCall();
        } else if (state == InCallState.INCALL) {
            primary = callList.getActiveCall();
            if (primary != null) {
                secondary = callList.getBackgroundCall();
            } else {
                primary = callList.getBackgroundCall();
                secondary = callList.getSecondBackgroundCall();
            }
        }

        Logger.d(this, "Primary call: " + primary);
        Logger.d(this, "Secondary call: " + secondary);

        // Set primary call data
        if (primary != null) {
            ui.setNumber(primary.getNumber());
        } else {
            ui.setNumber("");
        }

        // Set secondary call data
        if (secondary != null) {
            ui.setSecondaryCallInfo(true, secondary.getNumber());
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
