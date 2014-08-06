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

import android.telecomm.CallAudioState;
import android.telecomm.CallCapabilities;
import android.telecomm.InCallService.VideoCall;
import android.telecomm.VideoCallProfile;

import com.android.contacts.common.util.PhoneNumberHelper;
import com.android.contacts.common.util.TelephonyManagerUtils;
import com.android.incallui.AudioModeProvider.AudioModeListener;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;

import android.telephony.PhoneNumberUtils;

/**
 * Logic for call buttons.
 */
public class CallButtonPresenter extends Presenter<CallButtonPresenter.CallButtonUi>
        implements InCallStateListener, AudioModeListener, IncomingCallListener {

    private Call mCall;
    private boolean mAutomaticallyMuted = false;
    private boolean mPreviousMuteState = false;
    private boolean mShowGenericMerge = false;
    private boolean mShowManageConference = false;
    private InCallState mPreviousState = null;
    private InCallCameraManager mInCallCameraManager;

    public CallButtonPresenter() {
    }

    @Override
    public void onUiReady(CallButtonUi ui) {
        super.onUiReady(ui);

        AudioModeProvider.getInstance().addListener(this);

        // register for call state changes last
        InCallPresenter.getInstance().addListener(this);
        InCallPresenter.getInstance().addIncomingCallListener(this);
        mInCallCameraManager = InCallPresenter.getInstance().getInCallCameraManager();
    }

    @Override
    public void onUiUnready(CallButtonUi ui) {
        super.onUiUnready(ui);

        InCallPresenter.getInstance().removeListener(this);
        AudioModeProvider.getInstance().removeListener(this);
        InCallPresenter.getInstance().removeIncomingCallListener(this);
        mInCallCameraManager = null;
    }

    @Override
    public void onStateChange(InCallState state, CallList callList) {
        CallButtonUi ui = getUi();

        if (state == InCallState.OUTGOING) {
            mCall = callList.getOutgoingCall();
        } else if (state == InCallState.INCALL) {
            mCall = callList.getActiveOrBackgroundCall();

            // When connected to voice mail, automatically shows the dialpad.
            // (On previous releases we showed it when in-call shows up, before waiting for
            // OUTGOING.  We may want to do that once we start showing "Voice mail" label on
            // the dialpad too.)
            if (ui != null) {
                if (mPreviousState == InCallState.OUTGOING && mCall != null
                        && PhoneNumberUtils.isVoiceMailNumber(mCall.getNumber())) {
                    ui.displayDialpad(true /* show */, true /* animate */);
                }
            }
        } else if (state == InCallState.INCOMING) {
            if (ui != null) {
                ui.displayDialpad(false /* show */, true /* animate */);
            }
            mCall = null;
        } else {
            mCall = null;
        }
        updateUi(state, mCall);

        mPreviousState = state;
    }

    @Override
    public void onIncomingCall(InCallState state, Call call) {
        onStateChange(state, CallList.getInstance());
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
        if (getUi() != null && !mAutomaticallyMuted) {
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

        Log.d(this, "Sending new Audio Mode: " + CallAudioState.audioRouteToString(mode));
        TelecommAdapter.getInstance().setAudioRoute(mode);
    }

    /**
     * Function assumes that bluetooth is not supported.
     */
    public void toggleSpeakerphone() {
        // this function should not be called if bluetooth is available
        if (0 != (CallAudioState.ROUTE_BLUETOOTH & getSupportedAudio())) {

            // It's clear the UI is wrong, so update the supported mode once again.
            Log.e(this, "toggling speakerphone not allowed when bluetooth supported.");
            getUi().setSupportedAudio(getSupportedAudio());
            return;
        }

        int newMode = CallAudioState.ROUTE_SPEAKER;

        // if speakerphone is already on, change to wired/earpiece
        if (getAudioMode() == CallAudioState.ROUTE_SPEAKER) {
            newMode = CallAudioState.ROUTE_WIRED_OR_EARPIECE;
        }

        setAudioMode(newMode);
    }

    public void manageConferenceButtonClicked() {
        getUi().displayManageConferencePanel(true);
    }

    public void muteClicked(boolean checked) {
        Log.d(this, "turning on mute: " + checked);
        TelecommAdapter.getInstance().mute(checked);
    }

    public void holdClicked(boolean checked) {
        if (mCall == null) {
            return;
        }
        if (checked) {
            Log.i(this, "Putting the call on hold: " + mCall);
            TelecommAdapter.getInstance().holdCall(mCall.getId());
        } else {
            Log.i(this, "Removing the call from hold: " + mCall);
            TelecommAdapter.getInstance().unholdCall(mCall.getId());
        }
    }

    public void mergeClicked() {
        TelecommAdapter.getInstance().merge(mCall.getId());
    }

    public void addCallClicked() {
        // Automatically mute the current call
        mAutomaticallyMuted = true;
        mPreviousMuteState = AudioModeProvider.getInstance().getMute();
        // Simulate a click on the mute button
        muteClicked(true);

        TelecommAdapter.getInstance().addCall();
    }

    public void changeToVoiceClicked() {
        VideoCall videoCall = mCall.getVideoCall();
        if (videoCall == null) {
            return;
        }

        VideoCallProfile videoCallProfile = new VideoCallProfile(
                VideoCallProfile.VideoState.AUDIO_ONLY, VideoCallProfile.QUALITY_DEFAULT);
        videoCall.sendSessionModifyRequest(videoCallProfile);
    }

    public void swapClicked() {
        TelecommAdapter.getInstance().swap(mCall.getId());
    }

    public void showDialpadClicked(boolean checked) {
        Log.v(this, "Show dialpad " + String.valueOf(checked));
        getUi().displayDialpad(checked /* show */, true /* animate */);
        updateExtraButtonRow();
    }

    public void changeToVideoClicked() {
        VideoCall videoCall = mCall.getVideoCall();
        if (videoCall == null) {
            return;
        }

        VideoCallProfile videoCallProfile =
                new VideoCallProfile(VideoCallProfile.VideoState.BIDIRECTIONAL);
        videoCall.sendSessionModifyRequest(videoCallProfile);

        mCall.setSessionModificationState(Call.SessionModificationState.REQUEST_FAILED);
    }

    /**
     * Switches the camera between the front-facing and back-facing camera.
     * @param useFrontFacingCamera True if we should switch to using the front-facing camera, or
     *     false if we should switch to using the back-facing camera.
     */
    public void switchCameraClicked(boolean useFrontFacingCamera) {
        mInCallCameraManager.setUseFrontFacingCamera(useFrontFacingCamera);

        VideoCall videoCall = mCall.getVideoCall();
        if (videoCall == null) {
            return;
        }

        String cameraId = mInCallCameraManager.getActiveCameraId();
        if (cameraId != null) {
            videoCall.setCamera(cameraId);
        }
        getUi().setSwitchCameraButton(!useFrontFacingCamera);
    }

    /**
     * Stop or start client's video transmission.
     * @param pause True if pausing the local user's video, or false if starting the local user's
     *    video.
     */
    public void pauseVideoClicked(boolean pause) {
        VideoCall videoCall = mCall.getVideoCall();
        if (videoCall == null) {
            return;
        }

        if (pause) {
            videoCall.setCamera(null);
            VideoCallProfile videoCallProfile = new VideoCallProfile(
                    mCall.getVideoState() | VideoCallProfile.VideoState.PAUSED);
            videoCall.sendSessionModifyRequest(videoCallProfile);
        } else {
            videoCall.setCamera(mInCallCameraManager.getActiveCameraId());
            VideoCallProfile videoCallProfile = new VideoCallProfile(
                    mCall.getVideoState() & ~VideoCallProfile.VideoState.PAUSED);
            videoCall.sendSessionModifyRequest(videoCallProfile);
        }
        getUi().setPauseVideoButton(pause);
    }

    private void updateUi(InCallState state, Call call) {
        Log.d(this, "Updating call UI for call: ", call);

        final CallButtonUi ui = getUi();
        if (ui == null) {
            return;
        }

        final boolean isEnabled =
                state.isConnectingOrConnected() &&!state.isIncoming() && call != null;
        ui.setEnabled(isEnabled);

        if (!isEnabled) {
            return;
        }

        if (call.isVideoCall()) {
            updateVideoCallButtons();
        } else {
            updateVoiceCallButtons(call);
        }

        ui.enableMute(call.can(CallCapabilities.MUTE));

        // Finally, update the "extra button row": It's displayed above the "End" button, but only
        // if necessary. It's never displayed while the dialpad is visible since it would overlap.
        //
        // The row contains two buttons:
        //     - "Manage conference" (used only on GSM devices)
        //     - "Merge" button (used only on CDMA devices)
        final boolean canMerge = call.can(CallCapabilities.MERGE_CALLS);
        final boolean isGenericConference = call.can(CallCapabilities.GENERIC_CONFERENCE);
        mShowGenericMerge = isGenericConference && canMerge;
        mShowManageConference = (call.isConferenceCall() && !isGenericConference);
        updateExtraButtonRow();
    }

    private void updateVideoCallButtons() {
        Log.v(this, "Showing buttons for video call.");
        final CallButtonUi ui = getUi();

        // Hide all voice-call-related buttons.
        ui.showAudioButton(false);
        ui.showDialpadButton(false);
        ui.showHoldButton(false);
        ui.showSwapButton(false);
        ui.showChangeToVideoButton(false);
        ui.showAddCallButton(false);
        ui.showMergeButton(false);
        ui.showOverflowButton(false);

        // Show all video-call-related buttons.
        ui.showChangeToVoiceButton(true);
        ui.showSwitchCameraButton(true);
        ui.showPauseVideoButton(true);
    }

    private void updateVoiceCallButtons(Call call) {
        Log.v(this, "Showing buttons for voice call.");
        final CallButtonUi ui = getUi();

        // Hide all video-call-related buttons.
        ui.showChangeToVoiceButton(false);
        ui.showSwitchCameraButton(false);
        ui.showPauseVideoButton(false);

        // Show all voice-call-related buttons.
        ui.showAudioButton(true);
        ui.showDialpadButton(true);

        Log.v(this, "Show hold ", call.can(CallCapabilities.SUPPORT_HOLD));
        Log.v(this, "Enable hold", call.can(CallCapabilities.HOLD));
        Log.v(this, "Show merge ", call.can(CallCapabilities.MERGE_CALLS));
        Log.v(this, "Show swap ", call.can(CallCapabilities.SWAP_CALLS));
        Log.v(this, "Show add call ", call.can(CallCapabilities.ADD_CALL));
        Log.v(this, "Show mute ", call.can(CallCapabilities.MUTE));

        final boolean canMerge = call.can(CallCapabilities.MERGE_CALLS);
        final boolean canAdd = call.can(CallCapabilities.ADD_CALL);
        final boolean isGenericConference = call.can(CallCapabilities.GENERIC_CONFERENCE);
        final boolean canHold = call.can(CallCapabilities.HOLD);
        final boolean canSwap = call.can(CallCapabilities.SWAP_CALLS);
        final boolean supportHold = call.can(CallCapabilities.SUPPORT_HOLD);
        boolean canVideoCall = call.can(CallCapabilities.SUPPORTS_VT_LOCAL)
                && call.can(CallCapabilities.SUPPORTS_VT_REMOTE);

        final boolean showMerge = !isGenericConference && canMerge;

        ui.showChangeToVideoButton(canVideoCall);

        // Show either MERGE or ADD, but not both.
        final boolean showMergeOption = showMerge;
        final boolean showAddCallOption = !showMerge;
        final boolean enableAddCallOption = showAddCallOption && canAdd;
        // Show either HOLD or SWAP, but not both.
        // If neither HOLD or SWAP is available:
        //     (1) If the device normally can hold/swap, show HOLD in a disabled state.
        //     (2) If the device doesn't have the concept of hold/swap, remove the button.
        final boolean showHoldOption = canHold || (!canSwap && supportHold);
        final boolean enableHoldOption = canHold;
        final boolean showSwapOption = !canHold && canSwap;

        ui.setHold(call.getState() == Call.State.ONHOLD);
        if (canVideoCall && (showAddCallOption || showMergeOption)
                && (showHoldOption || showSwapOption)) {
            ui.showHoldButton(false);
            ui.showSwapButton(false);
            ui.showAddCallButton(false);
            ui.showMergeButton(false);

            ui.showOverflowButton(true);
            ui.configureOverflowMenu(
                    showMergeOption,
                    showAddCallOption && enableAddCallOption /* showAddMenuOption */,
                    showHoldOption && enableHoldOption /* showHoldMenuOption */,
                    showSwapOption);
        } else {
            ui.showMergeButton(showMergeOption);
            ui.showAddCallButton(showAddCallOption);
            ui.enableAddCall(enableAddCallOption);

            ui.showHoldButton(showHoldOption);
            ui.enableHold(enableHoldOption);
            ui.showSwapButton(showSwapOption);
        }
    }

    private void updateExtraButtonRow() {
        final boolean showExtraButtonRow = (mShowGenericMerge || mShowManageConference) &&
                !getUi().isDialpadVisible();

        Log.d(this, "isGeneric: " + mShowGenericMerge);
        Log.d(this, "mShowManageConference : " + mShowManageConference);
        Log.d(this, "mShowGenericMerge: " + mShowGenericMerge);
        if (showExtraButtonRow) {
            if (mShowGenericMerge) {
                getUi().showGenericMergeButton();
            } else if (mShowManageConference) {
                getUi().showManageConferenceCallButton();
            }
        } else {
            getUi().hideExtraRow();
        }
    }

    public void refreshMuteState() {
        // Restore the previous mute state
        if (mAutomaticallyMuted &&
                AudioModeProvider.getInstance().getMute() != mPreviousMuteState) {
            if (getUi() == null) {
                return;
            }
            muteClicked(mPreviousMuteState);
        }
        mAutomaticallyMuted = false;
    }

    public interface CallButtonUi extends Ui {
        void setEnabled(boolean on);
        void setMute(boolean on);
        void enableMute(boolean enabled);
        void showAudioButton(boolean show);
        void showChangeToVoiceButton(boolean show);
        void showDialpadButton(boolean show);
        void setHold(boolean on);
        void showHoldButton(boolean show);
        void enableHold(boolean enabled);
        void showSwapButton(boolean show);
        void showChangeToVideoButton(boolean show);
        void showSwitchCameraButton(boolean show);
        void setSwitchCameraButton(boolean isBackFacingCamera);
        void showAddCallButton(boolean show);
        void enableAddCall(boolean enabled);
        void showMergeButton(boolean show);
        void showPauseVideoButton(boolean show);
        void setPauseVideoButton(boolean isPaused);
        void showOverflowButton(boolean show);
        void displayDialpad(boolean on, boolean animate);
        boolean isDialpadVisible();
        void setAudio(int mode);
        void setSupportedAudio(int mask);
        void configureOverflowMenu(boolean showMergeMenuOption, boolean showAddMenuOption,
                boolean showHoldMenuOption, boolean showSwapMenuOption);
        void showManageConferenceCallButton();
        void showGenericMergeButton();
        void hideExtraRow();
        void displayManageConferencePanel(boolean on);
    }
}
