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

import static com.android.incallui.CallButtonFragment.Buttons.*;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.telecom.AudioState;
import android.telecom.InCallService.VideoCall;
import android.telecom.VideoProfile;

import com.android.incallui.AudioModeProvider.AudioModeListener;
import com.android.incallui.InCallCameraManager.Listener;
import com.android.incallui.InCallPresenter.CanAddCallListener;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.InCallPresenter.InCallDetailsListener;

import java.util.Objects;

/**
 * Logic for call buttons.
 */
public class CallButtonPresenter extends Presenter<CallButtonPresenter.CallButtonUi>
        implements InCallStateListener, AudioModeListener, IncomingCallListener,
        InCallDetailsListener, CanAddCallListener, Listener {

    private static final String KEY_AUTOMATICALLY_MUTED = "incall_key_automatically_muted";
    private static final String KEY_PREVIOUS_MUTE_STATE = "incall_key_previous_mute_state";

    private Call mCall;
    private boolean mAutomaticallyMuted = false;
    private boolean mPreviousMuteState = false;
    private static final int BUTTON_THRESOLD_TO_DISPLAY_OVERFLOW_MENU = 5;

    public CallButtonPresenter() {
    }

    @Override
    public void onUiReady(CallButtonUi ui) {
        super.onUiReady(ui);

        AudioModeProvider.getInstance().addListener(this);

        // register for call state changes last
        InCallPresenter.getInstance().addListener(this);
        InCallPresenter.getInstance().addIncomingCallListener(this);
        InCallPresenter.getInstance().addDetailsListener(this);
        InCallPresenter.getInstance().addCanAddCallListener(this);
        InCallPresenter.getInstance().getInCallCameraManager().addCameraSelectionListener(this);
    }

    @Override
    public void onUiUnready(CallButtonUi ui) {
        super.onUiUnready(ui);

        InCallPresenter.getInstance().removeListener(this);
        AudioModeProvider.getInstance().removeListener(this);
        InCallPresenter.getInstance().removeIncomingCallListener(this);
        InCallPresenter.getInstance().removeDetailsListener(this);
        InCallPresenter.getInstance().getInCallCameraManager().removeCameraSelectionListener(this);
    }

    @Override
    public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
        CallButtonUi ui = getUi();

        if (newState == InCallState.OUTGOING) {
            mCall = callList.getOutgoingCall();
        } else if (newState == InCallState.INCALL) {
            mCall = callList.getActiveOrBackgroundCall();

            // When connected to voice mail, automatically shows the dialpad.
            // (On previous releases we showed it when in-call shows up, before waiting for
            // OUTGOING.  We may want to do that once we start showing "Voice mail" label on
            // the dialpad too.)
            if (ui != null) {
                if (oldState == InCallState.OUTGOING && mCall != null) {
                    if (CallerInfoUtils.isVoiceMailNumber(ui.getContext(), mCall)) {
                        ui.displayDialpad(true /* show */, true /* animate */);
                    }
                }
            }
        } else if (newState == InCallState.INCOMING) {
            if (ui != null) {
                ui.displayDialpad(false /* show */, true /* animate */);
            }
            mCall = null;
        } else {
            mCall = null;
        }
        updateUi(newState, mCall);
    }

    /**
     * Updates the user interface in response to a change in the details of a call.
     * Currently handles changes to the call buttons in response to a change in the details for a
     * call.  This is important to ensure changes to the active call are reflected in the available
     * buttons.
     *
     * @param call The active call.
     * @param details The call details.
     */
    @Override
    public void onDetailsChanged(Call call, android.telecom.Call.Details details) {
        if (getUi() != null && Objects.equals(call, mCall)) {
            updateCallButtons(call, getUi().getContext());
        }
    }

    @Override
    public void onIncomingCall(InCallState oldState, InCallState newState, Call call) {
        onStateChange(oldState, newState, CallList.getInstance());
    }

    @Override
    public void onCanAddCallChanged(boolean canAddCall) {
        if (getUi() != null && mCall != null) {
            updateCallButtons(mCall, getUi().getContext());
        }
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

        Log.d(this, "Sending new Audio Mode: " + AudioState.audioRouteToString(mode));
        TelecomAdapter.getInstance().setAudioRoute(mode);
    }

    /**
     * Function assumes that bluetooth is not supported.
     */
    public void toggleSpeakerphone() {
        // this function should not be called if bluetooth is available
        if (0 != (AudioState.ROUTE_BLUETOOTH & getSupportedAudio())) {

            // It's clear the UI is wrong, so update the supported mode once again.
            Log.e(this, "toggling speakerphone not allowed when bluetooth supported.");
            getUi().setSupportedAudio(getSupportedAudio());
            return;
        }

        int newMode = AudioState.ROUTE_SPEAKER;

        // if speakerphone is already on, change to wired/earpiece
        if (getAudioMode() == AudioState.ROUTE_SPEAKER) {
            newMode = AudioState.ROUTE_WIRED_OR_EARPIECE;
        }

        setAudioMode(newMode);
    }

    public void muteClicked(boolean checked) {
        Log.d(this, "turning on mute: " + checked);
        TelecomAdapter.getInstance().mute(checked);
    }

    public void holdClicked(boolean checked) {
        if (mCall == null) {
            return;
        }
        if (checked) {
            Log.i(this, "Putting the call on hold: " + mCall);
            TelecomAdapter.getInstance().holdCall(mCall.getId());
        } else {
            Log.i(this, "Removing the call from hold: " + mCall);
            TelecomAdapter.getInstance().unholdCall(mCall.getId());
        }
    }

    public void swapClicked() {
        if (mCall == null) {
            return;
        }

        Log.i(this, "Swapping the call: " + mCall);
        TelecomAdapter.getInstance().swap(mCall.getId());
    }

    public void mergeClicked() {
        TelecomAdapter.getInstance().merge(mCall.getId());
    }

    public void addCallClicked() {
        // Automatically mute the current call
        mAutomaticallyMuted = true;
        mPreviousMuteState = AudioModeProvider.getInstance().getMute();
        // Simulate a click on the mute button
        muteClicked(true);
        TelecomAdapter.getInstance().addCall();
    }

    public void changeToVoiceClicked() {
        VideoCall videoCall = mCall.getVideoCall();
        if (videoCall == null) {
            return;
        }

        VideoProfile videoProfile = new VideoProfile(
                VideoProfile.VideoState.AUDIO_ONLY, VideoProfile.QUALITY_DEFAULT);
        videoCall.sendSessionModifyRequest(videoProfile);
    }

    public void showDialpadClicked(boolean checked) {
        Log.v(this, "Show dialpad " + String.valueOf(checked));
        getUi().displayDialpad(checked /* show */, true /* animate */);
    }

    public void displayModifyCallOptions() {
        getUi().displayModifyCallOptions();
    }

    public int getCurrentVideoState() {
        return mCall.getVideoState();
    }

    public void changeToVideoClicked(VideoProfile videoProfile) {
        VideoCall videoCall = mCall.getVideoCall();
        if (videoCall == null) {
            return;
        }

        videoCall.sendSessionModifyRequest(videoProfile);
        mCall.setSessionModificationState(Call.SessionModificationState.WAITING_FOR_RESPONSE);
    }

    /**
     * Switches the camera between the front-facing and back-facing camera.
     * @param useFrontFacingCamera True if we should switch to using the front-facing camera, or
     *     false if we should switch to using the back-facing camera.
     */
    public void switchCameraClicked(boolean useFrontFacingCamera) {
        InCallCameraManager cameraManager = InCallPresenter.getInstance().getInCallCameraManager();
        cameraManager.setUseFrontFacingCamera(useFrontFacingCamera);

        VideoCall videoCall = mCall.getVideoCall();
        if (videoCall == null) {
            return;
        }

        String cameraId = cameraManager.getActiveCameraId();
        if (cameraId != null) {
            final int cameraDir = cameraManager.isUsingFrontFacingCamera()
                    ? Call.VideoSettings.CAMERA_DIRECTION_FRONT_FACING
                    : Call.VideoSettings.CAMERA_DIRECTION_BACK_FACING;
            mCall.getVideoSettings().setCameraDir(cameraDir);
            videoCall.setCamera(cameraId);
            videoCall.requestCameraCapabilities();
        }
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
            VideoProfile videoProfile = new VideoProfile(
                    mCall.getVideoState() | VideoProfile.VideoState.PAUSED);
            videoCall.sendSessionModifyRequest(videoProfile);
        } else {
            InCallCameraManager cameraManager = InCallPresenter.getInstance().
                    getInCallCameraManager();
            videoCall.setCamera(cameraManager.getActiveCameraId());
            VideoProfile videoProfile = new VideoProfile(
                    mCall.getVideoState() & ~VideoProfile.VideoState.PAUSED);
            videoCall.sendSessionModifyRequest(videoProfile);
        }
        getUi().setVideoPaused(pause);
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

        updateCallButtons(call, ui.getContext());

        ui.enableButton(BUTTON_MUTE, call.can(android.telecom.Call.Details.CAPABILITY_MUTE));
    }

    private static int toInteger(boolean b) {
        return b ? 1 : 0;
    }

    /**
     * Updates the buttons applicable for the UI.
     *
     * @param call The active call.
     * @param context The context.
     */
    private void updateCallButtons(Call call, Context context) {
        if (CallUtils.isVideoCall(call)) {
            updateVideoCallButtons(call);
        }
        updateVoiceCallButtons(call);
    }

    private void updateVideoCallButtons(Call call) {
        Log.v(this, "Showing buttons for video call.");
        final CallButtonUi ui = getUi();

        // Show all video-call-related buttons.
        ui.showButton(BUTTON_SWITCH_CAMERA, true);
        ui.showButton(BUTTON_PAUSE_VIDEO, true);

        final boolean supportHold = call.can(android.telecom.Call.Details.CAPABILITY_SUPPORT_HOLD);
        final boolean enableHoldOption = call.can(android.telecom.Call.Details.CAPABILITY_HOLD);
        ui.showButton(BUTTON_HOLD, supportHold);
        ui.enableButton(BUTTON_HOLD, enableHoldOption);
        ui.setHold(call.getState() == Call.State.ONHOLD);
    }

    private void updateVoiceCallButtons(Call call) {
        Log.v(this, "Showing buttons for voice call.");
        final CallButtonUi ui = getUi();

        // Hide all video-call-related buttons.
        ui.showButton(BUTTON_DOWNGRADE_TO_VOICE, false);
        ui.showButton(BUTTON_SWITCH_CAMERA, false);
        ui.showButton(BUTTON_PAUSE_VIDEO, false);

        // Show all voice-call-related buttons.
        ui.showButton(BUTTON_AUDIO, true);
        ui.showButton(BUTTON_DIALPAD,  true);

        Log.v(this, "Show hold ", call.can(android.telecom.Call.Details.CAPABILITY_SUPPORT_HOLD));
        Log.v(this, "Enable hold", call.can(android.telecom.Call.Details.CAPABILITY_HOLD));
        Log.v(this, "Show merge ", call.can(
                android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE));
        Log.v(this, "Show swap ", call.can(
                android.telecom.Call.Details.CAPABILITY_SWAP_CONFERENCE));
        Log.v(this, "Show add call ", TelecomAdapter.getInstance().canAddCall());
        Log.v(this, "Show mute ", call.can(android.telecom.Call.Details.CAPABILITY_MUTE));
        Log.v(this, "Show video call local:",
                        call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL)
                        + " remote: "
                        + call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_REMOTE));

        final boolean canAdd = TelecomAdapter.getInstance().canAddCall();
        final boolean enableHoldOption = call.can(android.telecom.Call.Details.CAPABILITY_HOLD);
        final boolean supportHold = call.can(android.telecom.Call.Details.CAPABILITY_SUPPORT_HOLD);
        final boolean isCallOnHold = call.getState() == Call.State.ONHOLD;

        boolean canVideoCall = call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL)
                && call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_REMOTE);
        ui.showButton(BUTTON_UPGRADE_TO_VIDEO, canVideoCall);

        final boolean showMergeOption = call.can(
                android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE);
        final boolean showAddCallOption = canAdd;
        final boolean showManageVideoCallConferenceOption = call.can(
                android.telecom.Call.Details.CAPABILITY_MANAGE_CONFERENCE)
                && CallUtils.isVideoCall(call);

        // Show either HOLD or SWAP, but not both. If neither HOLD or SWAP is available:
        //     (1) If the device normally can hold, show HOLD in a disabled state.
        //     (2) If the device doesn't have the concept of hold/swap, remove the button.
        final boolean showSwapOption = call.can(
                android.telecom.Call.Details.CAPABILITY_SWAP_CONFERENCE);
        final boolean showHoldOption = !showSwapOption && (enableHoldOption || supportHold);

        ui.setHold(isCallOnHold);
        //Initialize buttonCount = 2. Because speaker and dialpad these two always show in Call UI.
        int buttonCount = 2;
        buttonCount += toInteger(canVideoCall);
        buttonCount += toInteger(showAddCallOption);
        buttonCount += toInteger(showMergeOption);
        buttonCount += toInteger(showHoldOption);
        buttonCount += toInteger(showSwapOption);
        buttonCount += toInteger(call.can(android.telecom.Call.Details.CAPABILITY_MUTE));
        buttonCount += toInteger(showManageVideoCallConferenceOption);

        Log.v(this, "show ManageVideoCallConference: " + showManageVideoCallConferenceOption);
        Log.v(this, "No of InCall buttons: " + buttonCount + " canVideoCall: " + canVideoCall);

        // Show overflow menu if number of buttons is greater than 5.
        final boolean showOverflowMenu =
                buttonCount > BUTTON_THRESOLD_TO_DISPLAY_OVERFLOW_MENU;
        final boolean isVideoOverflowScenario = canVideoCall && showOverflowMenu;
        final boolean isOverflowScenario = !canVideoCall && showOverflowMenu;

        if (isVideoOverflowScenario) {
            ui.showButton(BUTTON_HOLD, false);
            ui.showButton(BUTTON_SWAP, false);
            ui.showButton(BUTTON_ADD_CALL, false);
            ui.showButton(BUTTON_MERGE, false);
            ui.showButton(BUTTON_MANAGE_VIDEO_CONFERENCE, false);

            ui.configureOverflowMenu(
                    showMergeOption,
                    showAddCallOption /* showAddMenuOption */,
                    showHoldOption && enableHoldOption /* showHoldMenuOption */,
                    showSwapOption,
                    showManageVideoCallConferenceOption);
            ui.showButton(BUTTON_OVERFLOW, true);
        } else {
            if (isOverflowScenario) {
                ui.showButton(BUTTON_ADD_CALL, false);
                ui.showButton(BUTTON_MERGE, false);
                ui.showButton(BUTTON_MANAGE_VIDEO_CONFERENCE, false);

                ui.configureOverflowMenu(
                        showMergeOption,
                        showAddCallOption /* showAddMenuOption */,
                        false /* showHoldMenuOption */,
                        false /* showSwapMenuOption */,
                        showManageVideoCallConferenceOption);
            } else {
                ui.showButton(BUTTON_MERGE, showMergeOption);
                ui.showButton(BUTTON_ADD_CALL, showAddCallOption);
                ui.showButton(BUTTON_MANAGE_VIDEO_CONFERENCE, showManageVideoCallConferenceOption);
            }

            ui.showButton(BUTTON_OVERFLOW, isOverflowScenario);
            ui.showButton(BUTTON_HOLD, showHoldOption);
            ui.enableButton(BUTTON_HOLD, enableHoldOption);
            ui.showButton(BUTTON_SWAP, showSwapOption);
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

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_AUTOMATICALLY_MUTED, mAutomaticallyMuted);
        outState.putBoolean(KEY_PREVIOUS_MUTE_STATE, mPreviousMuteState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        mAutomaticallyMuted =
                savedInstanceState.getBoolean(KEY_AUTOMATICALLY_MUTED, mAutomaticallyMuted);
        mPreviousMuteState =
                savedInstanceState.getBoolean(KEY_PREVIOUS_MUTE_STATE, mPreviousMuteState);
        super.onRestoreInstanceState(savedInstanceState);
    }

    public interface CallButtonUi extends Ui {
        void showButton(int buttonId, boolean show);
        void enableButton(int buttonId, boolean enable);
        void setEnabled(boolean on);
        void setMute(boolean on);
        void setHold(boolean on);
        void setCameraSwitched(boolean isBackFacingCamera);
        void setVideoPaused(boolean isPaused);
        void setAudio(int mode);
        void setSupportedAudio(int mask);
        void displayDialpad(boolean on, boolean animate);
        void displayModifyCallOptions();
        boolean isDialpadVisible();
        void configureOverflowMenu(boolean showMergeMenuOption, boolean showAddMenuOption,
                boolean showHoldMenuOption, boolean showSwapMenuOption,
                boolean showManageConferenceVideoCallOption);
        Context getContext();
    }

    @Override
    public void onActiveCameraSelectionChanged(boolean isUsingFrontFacingCamera) {
        if (getUi() == null) {
            return;
        }
        getUi().setCameraSwitched(!isUsingFrontFacingCamera);
    }
}
