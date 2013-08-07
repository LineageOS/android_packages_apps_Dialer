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

import com.android.incallui.AudioModeProvider.AudioModeListener;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.services.telephony.common.AudioMode;
import com.android.services.telephony.common.Call;

/**
 * Logic for call buttons.
 */
public class CallButtonPresenter extends Presenter<CallButtonPresenter.CallButtonUi>
        implements InCallStateListener, AudioModeListener {

    private Call mCall;
    private final AudioModeProvider mAudioModeProvider;

    public CallButtonPresenter(AudioModeProvider audioModeProvider) {

        // AudioModeProvider works effectively as a pass through. However, if we
        // had this presenter listen for changes directly, it would have to live forever
        // or risk missing important updates.
        mAudioModeProvider = audioModeProvider;
        mAudioModeProvider.addListener(this);
    }

    @Override
    public void onUiReady(CallButtonUi ui) {
        super.onUiReady(ui);
    }

    @Override
    public void onUiUnready(CallButtonUi ui) {
        mAudioModeProvider.removeListener(this);
    }

    @Override
    public void onStateChange(InCallState state, CallList callList) {
        final boolean isVisible = state.isConnectingOrConnected() &&
                !state.isIncoming();

        getUi().setVisible(isVisible);

        if (state == InCallState.OUTGOING) {
            mCall = callList.getOutgoingCall();
        } else if (state == InCallState.INCALL) {
            mCall = callList.getActiveOrBackgroundCall();
        } else {
            mCall = null;
        }
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
        return mAudioModeProvider.getAudioMode();
    }

    public int getSupportedAudio() {
        return mAudioModeProvider.getSupportedModes();
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
        if (0 != (AudioMode.BLUETOOTH & mAudioModeProvider.getSupportedModes())) {

            // It's clear the UI is off, so update the supported mode once again.
            Logger.e(this, "toggling speakerphone not allowed when bluetooth supported.");
            getUi().setSupportedAudio(mAudioModeProvider.getSupportedModes());
            return;
        }

        int newMode = AudioMode.SPEAKER;

        // if speakerphone is already on, change to wired/earpiece
        if (mAudioModeProvider.getAudioMode() == AudioMode.SPEAKER) {
            newMode = AudioMode.WIRED_OR_EARPIECE;
        }

        setAudioMode(newMode);
    }

    public void endCallClicked() {
        Preconditions.checkNotNull(mCall);

        // TODO(klp): hook up call id.
        CallCommandClient.getInstance().disconnectCall(mCall.getCallId());
    }

    public void muteClicked(boolean checked) {
        Logger.d(this, "turning on mute: " + checked);

        CallCommandClient.getInstance().mute(checked);
        getUi().setMute(checked);
    }

    public void holdClicked(boolean checked) {
        Preconditions.checkNotNull(mCall);

        Logger.d(this, "holding: " + mCall.getCallId());

        // TODO(klp): use appropriate hold callId.
        CallCommandClient.getInstance().hold(mCall.getCallId(), checked);
        getUi().setHold(checked);
    }

    public void showDialpadClicked(boolean checked) {
        Logger.v(this, "Show dialpad " + String.valueOf(checked));
        getUi().displayDialpad(checked);
    }

    public interface CallButtonUi extends Ui {
        void setVisible(boolean on);
        void setMute(boolean on);
        void setHold(boolean on);
        void displayDialpad(boolean on);
        void setAudio(int mode);
        void setSupportedAudio(int mask);
    }
}
