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

import android.media.AudioManager;

import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.services.telephony.common.Call;

/**
 * Logic for call buttons.
 */
public class CallButtonPresenter extends Presenter<CallButtonPresenter.CallButtonUi>
        implements InCallStateListener {

    private AudioManager mAudioManager;
    private Call mCall;

    public void init(AudioManager audioManager) {
        mAudioManager = audioManager;
    }

    @Override
    public void onUiReady(CallButtonUi ui) {
        super.onUiReady(ui);
        getUi().setMute(mAudioManager.isMicrophoneMute());
        getUi().setSpeaker(mAudioManager.isSpeakerphoneOn());
    }

    @Override
    public void onStateChange(InCallState state, CallList callList) {
        getUi().setVisible(state == InCallState.INCALL);

        if (state == InCallState.INCALL) {
            mCall = callList.getActiveOrBackgroundCall();
        } else {
            mCall = null;
        }
    }

    public void endCallClicked() {
        Preconditions.checkNotNull(mCall);

        // TODO(klp): hook up call id.
        CallCommandClient.getInstance().disconnectCall(mCall.getCallId());

        // TODO(klp): Remove once all state is gathered from CallList.
        //            This will be wrong when you disconnect from a call if
        //            the user has another call on hold.
        reset();
    }

    private void reset() {
        getUi().setVisible(false);
        getUi().setMute(false);
        getUi().setSpeaker(false);
        getUi().setHold(false);
    }

    public void muteClicked(boolean checked) {
        Logger.d(this, "turning on mute: " + checked);

        CallCommandClient.getInstance().mute(checked);
        getUi().setMute(checked);
    }

    public void speakerClicked(boolean checked) {
        Logger.d(this, "turning on speaker: " + checked);

        CallCommandClient.getInstance().turnSpeakerOn(checked);
        getUi().setSpeaker(checked);
    }

    public void holdClicked(boolean checked) {
        Preconditions.checkNotNull(mCall);

        Logger.d(this, "holding: " + mCall.getCallId());

        // TODO(klp): use appropriate hold callId.
        CallCommandClient.getInstance().hold(mCall.getCallId(), checked);
        getUi().setHold(checked);
    }

    public interface CallButtonUi extends Ui {
        void setVisible(boolean on);
        void setMute(boolean on);
        void setSpeaker(boolean on);
        void setHold(boolean on);
    }
}
