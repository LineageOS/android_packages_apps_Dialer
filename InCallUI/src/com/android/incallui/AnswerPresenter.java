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

import com.google.common.base.Preconditions;

import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.services.telephony.common.Call;

/**
 * Presenter for the Incoming call widget.
 */
public class AnswerPresenter extends Presenter<AnswerPresenter.AnswerUi>
        implements InCallStateListener {

    private Call mCall;

    @Override
    public void onUiReady(AnswerUi ui) {
        super.onUiReady(ui);
    }

    @Override
    public void onStateChange(InCallState state, CallList callList) {
        if (state == InCallState.INCOMING) {
            getUi().showAnswerUi(true);
            mCall = callList.getIncomingCall();

            Logger.d(this, "Showing incoming with: " + mCall);
        } else {
            getUi().showAnswerUi(false);
            mCall = null;
        }
    }

    public void onAnswer() {
        Preconditions.checkNotNull(mCall);
        Logger.d(this, "onAnswer " + mCall.getCallId());

        CallCommandClient.getInstance().answerCall(mCall.getCallId());
    }

    public void onDecline() {
        Preconditions.checkNotNull(mCall);
        Logger.d(this, "onDecline " + mCall.getCallId());

        CallCommandClient.getInstance().rejectCall(mCall.getCallId());
    }

    public void onText() {
    }

    interface AnswerUi extends Ui {
        public void showAnswerUi(boolean show);
    }
}
