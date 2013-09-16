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

import com.android.incallui.AudioModeProvider.AudioModeListener;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.services.telephony.common.AudioMode;
import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.Call.Capabilities;

import android.telephony.PhoneNumberUtils;

/**
 * Logic for call buttons.
 */
public class CallButtonPresenter extends Presenter<CallButtonPresenter.CallButtonUi>
        implements InCallStateListener, AudioModeListener {

    private Call mCall;
    private ProximitySensor mProximitySensor;
    private boolean mAutomaticallyMuted = false;
    private boolean mPreviousMuteState = false;

    private InCallState mPreviousState = null;

    public CallButtonPresenter() {
    }

    @Override
    public void onUiReady(CallButtonUi ui) {
        super.onUiReady(ui);

        mProximitySensor = InCallPresenter.getInstance().getProximitySensor();
        AudioModeProvider.getInstance().addListener(this);

        // register for call state changes last
        InCallPresenter.getInstance().addListener(this);
    }

    @Override
    public void onUiUnready(CallButtonUi ui) {
        super.onUiUnready(ui);

        InCallPresenter.getInstance().removeListener(this);
        AudioModeProvider.getInstance().removeListener(this);

        mProximitySensor = null;
    }

    @Override
    public void onStateChange(InCallState state, CallList callList) {
        if (state == InCallState.OUTGOING) {
            mCall = callList.getOutgoingCall();
        } else if (state == InCallState.INCALL) {
            mCall = callList.getActiveOrBackgroundCall();

            // When connected to voice mail, automatically shows the dialpad.
            // (On previous releases we showed it when in-call shows up, before waiting for
            // OUTGOING.  We may want to do that once we start showing "Voice mail" label on
            // the dialpad too.)
            if (mPreviousState == InCallState.OUTGOING
                    && PhoneNumberUtils.isVoiceMailNumber(mCall.getNumber())) {
                getUi().displayDialpad(true);
            }
        } else {
            mCall = null;
        }

        updateUi(state, mCall);

        mPreviousState = state;
    }

    @Override
    public void onAudioMode(int mode) {
        if (getUi() != null) {
            getUi().setAudio(mode);
        }
    }

    @Override
    public void onSupportedAudioMode(int mask) {
        if (getUi() != null) {
            getUi().setSupportedAudio(mask);
        }
    }

    @Override
    public void onMute(boolean muted) {
        if (getUi() != null) {
            getUi().setMute(muted);
        }
    }

    public int getAudioMode() {
        return AudioModeProvider.getInstance().getAudioMode();
    }

    public int getSupportedAudio() {
        return AudioModeProvider.getInstance().getSupportedModes();
    }

    public void setAudioMode(int mode) {

        // TODO: Set a intermediate state in this presenter until we get
        // an update for onAudioMode().  This will make UI response immediate
        // if it turns out to be slow

        Log.d(this, "Sending new Audio Mode: " + AudioMode.toString(mode));
        CallCommandClient.getInstance().setAudioMode(mode);
    }

    /**
     * Function assumes that bluetooth is not supported.
     */
    public void toggleSpeakerphone() {
        // this function should not be called if bluetooth is available
        if (0 != (AudioMode.BLUETOOTH & getSupportedAudio())) {

            // It's clear the UI is wrong, so update the supported mode once again.
            Log.e(this, "toggling speakerphone not allowed when bluetooth supported.");
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

    public void manageConferenceButtonClicked() {
        getUi().displayManageConferencePanel(true);
    }

    public void muteClicked(boolean checked) {
        Log.d(this, "turning on mute: " + checked);
        CallCommandClient.getInstance().mute(checked);
    }

    public void holdClicked(boolean checked) {
        if (mCall == null) {
            return;
        }

        Log.d(this, "holding: " + mCall.getCallId());

        // TODO(klp): use appropriate hold callId.
        CallCommandClient.getInstance().hold(mCall.getCallId(), checked);
        getUi().setHold(checked);
    }

    public void mergeClicked() {
        CallCommandClient.getInstance().merge();
    }

    public void addCallClicked() {
        // Automatically mute the current call
        mAutomaticallyMuted = true;
        mPreviousMuteState = AudioModeProvider.getInstance().getMute();
        // Simulate a click on the mute button
        getUi().setMute(true);
        muteClicked(true);

        CallCommandClient.getInstance().addCall();
    }

    public void swapClicked() {
        CallCommandClient.getInstance().swap();
    }

    public void showDialpadClicked(boolean checked) {
        Log.v(this, "Show dialpad " + String.valueOf(checked));
        getUi().displayDialpad(checked);
        mProximitySensor.onDialpadVisible(checked);
    }

    private void updateUi(InCallState state, Call call) {
        final CallButtonUi ui = getUi();
        if (ui == null) {
            return;
        }

        final boolean isVisible = state.isConnectingOrConnected() &&
                !state.isIncoming() && call != null;

        ui.setVisible(isVisible);

        Log.d(this, "Updating call UI for call: ", call);

        if (isVisible) {
            Log.v(this, "Show hold ", call.can(Capabilities.SUPPORT_HOLD));
            Log.v(this, "Enable hold", call.can(Capabilities.HOLD));
            Log.v(this, "Show merge ", call.can(Capabilities.MERGE_CALLS));
            Log.v(this, "Show swap ", call.can(Capabilities.SWAP_CALLS));
            Log.v(this, "Show add call ", call.can(Capabilities.ADD_CALL));
            Log.v(this, "Show mute ", call.can(Capabilities.MUTE));

            final boolean canMerge = call.can(Capabilities.MERGE_CALLS);
            final boolean canAdd = call.can(Capabilities.ADD_CALL);

            if (canMerge) {
                ui.showMerge(true);
                ui.showAddCall(false);
            } else {
                ui.showMerge(false);
                ui.showAddCall(true);
                ui.enableAddCall(canAdd);
            }

            ui.showHold(call.can(Capabilities.SUPPORT_HOLD));
            ui.setHold(call.getState() == Call.State.ONHOLD);
            ui.enableHold(call.can(Capabilities.HOLD));

            ui.showSwap(call.can(Capabilities.SWAP_CALLS));

            // Restore the previous mute state
            if (mAutomaticallyMuted &&
                    AudioModeProvider.getInstance().getMute() != mPreviousMuteState) {
                ui.setMute(mPreviousMuteState);
                mAutomaticallyMuted = false;
            }
            ui.enableMute(call.can(Capabilities.MUTE));

            // Finally, update the "extra button row": It's displayed above the
            // "End" button, but only if necessary.  Also, it's never displayed
            // while the dialpad is visible (since it would overlap.)
            //
            // The row contains two buttons:
            //
            // - "Manage conference" (used only on GSM devices)
            // - "Merge" button (used only on CDMA devices)
            // TODO(klp) Add cdma merge button
            final boolean showCdmaMerge = false;
//                    (phoneType == PhoneConstants.PHONE_TYPE_CDMA) && inCallControlState.canMerge;
            final boolean showExtraButtonRow =
                    (showCdmaMerge || call.isConferenceCall()) && !getUi().isDialpadVisible();
            if (showExtraButtonRow) {
                // Need to set up mCdmaMergeButton and mManageConferenceButton if this is the first
                // time they're visible.
                // TODO(klp) add cdma merge button
//                if (mCdmaMergeButton == null) {
//                    setupExtraButtons();
//                }
//                mCdmaMergeButton.setVisibility(showCdmaMerge ? View.VISIBLE : View.GONE);
                if (call.isConferenceCall()) {
                    getUi().showManageConferenceCallButton();
                }
            } else {
                getUi().hideExtraRow();
            }
        }
    }

    public interface CallButtonUi extends Ui {
        void setVisible(boolean on);
        void setMute(boolean on);
        void enableMute(boolean enabled);
        void setHold(boolean on);
        void showHold(boolean show);
        void enableHold(boolean enabled);
        void showMerge(boolean show);
        void showSwap(boolean show);
        void showAddCall(boolean show);
        void enableAddCall(boolean enabled);
        void displayDialpad(boolean on);
        boolean isDialpadVisible();
        void setAudio(int mode);
        void setSupportedAudio(int mask);
        void showManageConferenceCallButton();
        void showCDMAMergeButton();
        void hideExtraRow();
        void displayManageConferencePanel(boolean on);
    }
}
