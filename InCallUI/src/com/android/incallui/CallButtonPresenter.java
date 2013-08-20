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

import android.media.AudioManager;

import com.android.incallui.AudioModeProvider.AudioModeListener;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.services.telephony.common.AudioMode;
import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.Call.Capabilities;

/**
 * Logic for call buttons.
 */
public class CallButtonPresenter extends Presenter<CallButtonPresenter.CallButtonUi>
        implements InCallStateListener, AudioModeListener {

    private Call mCall;
    private AudioModeProvider mAudioModeProvider;

    public CallButtonPresenter() {
    }

    @Override
    public void onUiReady(CallButtonUi ui) {
        super.onUiReady(ui);
        if (mAudioModeProvider != null) {
            mAudioModeProvider.addListener(this);
        }
    }

    @Override
    public void onUiUnready(CallButtonUi ui) {
        if (mAudioModeProvider != null) {
            mAudioModeProvider.removeListener(this);
        }
    }

    @Override
    public void onStateChange(InCallState state, CallList callList) {
        if (state == InCallState.OUTGOING) {
            mCall = callList.getOutgoingCall();
        } else if (state == InCallState.INCALL) {
            mCall = callList.getActiveOrBackgroundCall();
        } else {
            mCall = null;
        }

        updateUi(state, mCall);
    }

    @Override
    public void onAudioMode(int mode) {
        getUi().setAudio(mode);
    }

    @Override
    public void onSupportedAudioMode(int mask) {
        getUi().setSupportedAudio(mask);
    }

    public int getAudioMode() {
        if (mAudioModeProvider != null) {
            return mAudioModeProvider.getAudioMode();
        }
        return AudioMode.EARPIECE;
    }

    public int getSupportedAudio() {
        if (mAudioModeProvider != null) {
            return mAudioModeProvider.getSupportedModes();
        }

        return 0;
    }

    public void setAudioMode(int mode) {

        // TODO: Set a intermediate state in this presenter until we get
        // an update for onAudioMode().  This will make UI response immediate
        // if it turns out to be slow

        Logger.d(this, "Sending new Audio Mode: " + AudioMode.toString(mode));
        CallCommandClient.getInstance().setAudioMode(mode);
    }

    /**
     * Function assumes that bluetooth is not supported.
     */
    public void toggleSpeakerphone() {
        // this function should not be called if bluetooth is available
        if (0 != (AudioMode.BLUETOOTH & getSupportedAudio())) {

            // It's clear the UI is wrong, so update the supported mode once again.
            Logger.e(this, "toggling speakerphone not allowed when bluetooth supported.");
            getUi().setSupportedAudio(getSupportedAudio());
            return;
        }

        int newMode = AudioMode.SPEAKER;

        // if speakerphone is already on, change to wired/earpiece
        if (getAudioMode() == AudioMode.SPEAKER) {
            newMode = AudioMode.WIRED_OR_EARPIECE;
        }

        setAudioMode(newMode);
    }

    public void endCallClicked() {
        if (mCall == null) {
            return;
        }

        // TODO(klp): hook up call id.
        CallCommandClient.getInstance().disconnectCall(mCall.getCallId());
    }

    public void muteClicked(boolean checked) {
        Logger.d(this, "turning on mute: " + checked);

        CallCommandClient.getInstance().mute(checked);
        getUi().setMute(checked);
    }

    public void holdClicked(boolean checked) {
        if (mCall == null) {
            return;
        }

        Logger.d(this, "holding: " + mCall.getCallId());

        // TODO(klp): use appropriate hold callId.
        CallCommandClient.getInstance().hold(mCall.getCallId(), checked);
        getUi().setHold(checked);
    }

    public void mergeClicked() {
        CallCommandClient.getInstance().merge();
    }

    public void addCallClicked() {
        CallCommandClient.getInstance().addCall();
    }

    public void swapClicked() {
        CallCommandClient.getInstance().swap();
    }

    public void showDialpadClicked(boolean checked) {
        Logger.v(this, "Show dialpad " + String.valueOf(checked));
        getUi().displayDialpad(checked);
    }

    private void updateUi(InCallState state, Call call) {
        final CallButtonUi ui = getUi();
        if (ui == null) {
            return;
        }

        final boolean isVisible = state.isConnectingOrConnected() &&
                !state.isIncoming();

        ui.setVisible(isVisible);

        Logger.d(this, "Updating call UI for call: ", call);

        if (isVisible && call != null) {
            Logger.v(this, "Show hold ", call.can(Capabilities.HOLD));
            Logger.v(this, "Show merge ", call.can(Capabilities.MERGE_CALLS));
            Logger.v(this, "Show swap ", call.can(Capabilities.SWAP_CALLS));
            Logger.v(this, "Show add call ", call.can(Capabilities.ADD_CALL));

            ui.setHold(call.getState() == Call.State.ONHOLD);

            ui.showHold(call.can(Capabilities.HOLD));
            ui.showMerge(call.can(Capabilities.MERGE_CALLS));
            ui.showSwap(call.can(Capabilities.SWAP_CALLS));
            ui.showAddCall(call.can(Capabilities.ADD_CALL));
        }
    }

    public void setAudioModeProvider(AudioModeProvider audioModeProvider) {
        // AudioModeProvider works effectively as a pass through. However, if we
        // had this presenter listen for changes directly, it would have to live forever
        // or risk missing important updates.
        mAudioModeProvider = audioModeProvider;
        mAudioModeProvider.addListener(this);
    }

    public interface CallButtonUi extends Ui {
        void setVisible(boolean on);
        void setMute(boolean on);
        void setHold(boolean on);
        void showHold(boolean show);
        void showMerge(boolean show);
        void showSwap(boolean show);
        void showAddCall(boolean show);
        void displayDialpad(boolean on);
        void setAudio(int mode);
        void setSupportedAudio(int mask);
    }
}
