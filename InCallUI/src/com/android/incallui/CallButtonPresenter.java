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

import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_ADD_CALL;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_AUDIO;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_DIALPAD;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_DOWNGRADE_TO_AUDIO;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_HOLD;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_MERGE;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_MUTE;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_PAUSE_VIDEO;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_RECORD_CALL;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_SWAP;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_SWITCH_CAMERA;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_TRANSFER_ASSURED;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_TRANSFER_BLIND;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_TRANSFER_CONSULTATIVE;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_UPGRADE_TO_VIDEO;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_RXTX_VIDEO_CALL;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_RX_VIDEO_CALL;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_VO_VIDEO_CALL;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_ADD_PARTICIPANT;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telecom.CallAudioState;
import android.telecom.InCallService.VideoCall;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.VideoProfile;
import android.widget.Toast;

import com.android.contacts.common.compat.CallSdkCompat;
import com.android.contacts.common.compat.SdkVersionOverride;
import com.android.dialer.util.PresenceHelper;
import com.android.dialer.compat.UserManagerCompat;
import com.android.incallui.AudioModeProvider.AudioModeListener;
import com.android.incallui.InCallCameraManager.Listener;
import com.android.incallui.InCallPresenter.CanAddCallListener;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;

import org.codeaurora.ims.utils.QtiImsExtUtils;

/**
 * Logic for call buttons.
 */
public class CallButtonPresenter extends Presenter<CallButtonPresenter.CallButtonUi>
        implements InCallStateListener, AudioModeListener, IncomingCallListener,
        InCallDetailsListener, CanAddCallListener, CallList.ActiveSubChangeListener, Listener {

    private static final String KEY_AUTOMATICALLY_MUTED = "incall_key_automatically_muted";
    private static final String KEY_PREVIOUS_MUTE_STATE = "incall_key_previous_mute_state";
    private static final String KEY_RECORDING_WARNING_PRESENTED = "recording_warning_presented";

    private Call mCall;
    private boolean mAutomaticallyMuted = false;
    private boolean mPreviousMuteState = false;
    private static final int MAX_PARTICIPANTS_LIMIT = 6;
    private boolean mEnhanceEnable = false;

    // NOTE: Capability constant definition has been duplicated to avoid bundling
    // the Dialer with Frameworks.
    private static final int CAPABILITY_ADD_PARTICIPANT = 0x02000000;

    public CallButtonPresenter() {
    }

    @Override
    public void onUiReady(CallButtonUi ui) {
        super.onUiReady(ui);

        mEnhanceEnable = ui.getContext().getResources().getBoolean(
                R.bool.config_enable_enhance_video_call_ui);
        AudioModeProvider.getInstance().addListener(this);

        // register for call state changes last
        final InCallPresenter inCallPresenter = InCallPresenter.getInstance();
        inCallPresenter.addListener(this);
        inCallPresenter.addIncomingCallListener(this);
        inCallPresenter.addDetailsListener(this);
        inCallPresenter.addCanAddCallListener(this);
        inCallPresenter.getInCallCameraManager().addCameraSelectionListener(this);
        CallList.getInstance().addActiveSubChangeListener(this);

        // Update the buttons state immediately for the current call
        onStateChange(InCallState.NO_CALLS, inCallPresenter.getInCallState(),
                CallList.getInstance());
    }

    @Override
    public void onUiUnready(CallButtonUi ui) {
        super.onUiUnready(ui);

        InCallPresenter.getInstance().removeListener(this);
        AudioModeProvider.getInstance().removeListener(this);
        InCallPresenter.getInstance().removeIncomingCallListener(this);
        InCallPresenter.getInstance().removeDetailsListener(this);
        InCallPresenter.getInstance().getInCallCameraManager().removeCameraSelectionListener(this);
        InCallPresenter.getInstance().removeCanAddCallListener(this);
        CallList.getInstance().removeActiveSubChangeListener(this);
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
            mCall = callList.getIncomingCall();
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
        // Only update if the changes are for the currently active call
        if (getUi() != null && call != null && call.equals(mCall)) {
            updateButtonsState(call);
        }
    }

    @Override
    public void onIncomingCall(InCallState oldState, InCallState newState, Call call) {
        onStateChange(oldState, newState, CallList.getInstance());
    }

    @Override
    public void onCanAddCallChanged(boolean canAddCall) {
        if (getUi() != null && mCall != null) {
            updateButtonsState(mCall);
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

            // toggle the visibility of audio button
            getUi().showButton(BUTTON_AUDIO, shouldAudioButtonShow());
            getUi().updateButtonStates();
            getUi().updateColors();
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
        TelecomAdapter.getInstance().setAudioRoute(mode);
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
        if (mCall == null) {
            return;
        }

        if (getUi().getContext().getResources().getBoolean(
                R.bool.add_multi_participants_enabled)){
            int participantsCount = 0;
            if (mCall.isConferenceCall()) {
                participantsCount = mCall.getChildCallIds().size();
            } else {
                Call backgroundCall = CallList.getInstance().getBackgroundCall();
                if (backgroundCall != null && backgroundCall.isConferenceCall()) {
                    participantsCount = backgroundCall.getChildCallIds().size();
                }
            }
            Log.i(this, "Number of participantsCount is " + participantsCount);
            if (participantsCount >= MAX_PARTICIPANTS_LIMIT) {
                Toast.makeText(getUi().getContext(),
                        R.string.too_many_recipients, Toast.LENGTH_SHORT).show();
                return;
            }
         }
        TelecomAdapter.getInstance().merge(mCall.getId());
        InCallAudioManager.getInstance().onMergeClicked();
    }

    public void addParticipantClicked() {
        if (getUi().getContext().getResources().getBoolean(
                R.bool.add_multi_participants_enabled)){
            InCallPresenter.getInstance().sendAddMultiParticipantsIntent();
            return;
        }
        InCallPresenter.getInstance().sendAddParticipantIntent();
    }

    public void addCallClicked() {
        if (!QtiImsExtUtils.isCarrierOneSupported()) {
            // Automatically mute the current call
            mAutomaticallyMuted = true;
            mPreviousMuteState = AudioModeProvider.getInstance().getMute();
            // Simulate a click on the mute button
            muteClicked(true);
        }
        TelecomAdapter.getInstance().addCall();
    }

    public void changeToVoiceClicked() {
        VideoCall videoCall = mCall.getVideoCall();
        if (videoCall == null) {
            return;
        }

        VideoProfile videoProfile = new VideoProfile(VideoProfile.STATE_AUDIO_ONLY);
        videoCall.sendSessionModifyRequest(videoProfile);

        if (QtiCallUtils.useCustomVideoUi(getUi().getContext())) {
            InCallAudioManager.getInstance().onModifyCallClicked(mCall,
                    VideoProfile.STATE_AUDIO_ONLY);
        }
    }

    public void showDialpadClicked(boolean checked) {
        Log.v(this, "Show dialpad " + String.valueOf(checked));
        getUi().displayDialpad(checked /* show */, true /* animate */);
    }

    public void changeToVideoClicked() {
        final Context context = getUi().getContext();
        if (QtiCallUtils.useExt(context)) {
            QtiCallUtils.displayModifyCallOptions(mCall, context);
            return;
        }

        VideoCall videoCall = mCall.getVideoCall();
        if (videoCall == null) {
            return;
        }
        int currVideoState = mCall.getVideoState();
        int currUnpausedVideoState = VideoUtils.getUnPausedVideoState(currVideoState);
        currUnpausedVideoState |= VideoProfile.STATE_BIDIRECTIONAL;

        VideoProfile videoProfile = new VideoProfile(currUnpausedVideoState);
        videoCall.sendSessionModifyRequest(videoProfile);
        mCall.setSessionModificationState(Call.SessionModificationState.WAITING_FOR_RESPONSE);

        if (QtiCallUtils.useCustomVideoUi(context)) {
            InCallAudioManager.getInstance().onModifyCallClicked(mCall,
                    currUnpausedVideoState);
        }
    }

    public void changeToVideo(int videoState) {
        final Context context = getUi().getContext();
        if(mCall == null) {
            return;
        }

        if(VideoProfile.isVideo(videoState) &&
                !PresenceHelper.getVTCapability(mCall.getNumber())) {
            Toast.makeText(context,context.getString(R.string.video_call_cannot_upgrade),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        final VideoProfile videoProfile = new VideoProfile(videoState);
        QtiCallUtils.changeToVideoCall(mCall, videoProfile, context);
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

        final int currUnpausedVideoState = VideoUtils.getUnPausedVideoState(mCall.getVideoState());
        if (pause) {
            videoCall.setCamera(null);
            VideoProfile videoProfile = new VideoProfile(currUnpausedVideoState
                    & ~VideoProfile.STATE_TX_ENABLED);
            videoCall.sendSessionModifyRequest(videoProfile);
        } else {
            InCallCameraManager cameraManager = InCallPresenter.getInstance().
                    getInCallCameraManager();
            videoCall.setCamera(cameraManager.getActiveCameraId());
            VideoProfile videoProfile = new VideoProfile(currUnpausedVideoState
                    | VideoProfile.STATE_TX_ENABLED);
            videoCall.sendSessionModifyRequest(videoProfile);
            mCall.setSessionModificationState(Call.SessionModificationState.WAITING_FOR_RESPONSE);
        }
        getUi().setVideoPaused(pause);
    }

    public void callTransferClicked(int type) {
        String number = null;
        Context mContext = getUi().getContext();
        if (type != QtiImsExtUtils.QTI_IMS_CONSULTATIVE_TRANSFER) {
            /**
             * Since there are no editor options available to provide a number during
             * blind or assured transfer, for now, making use of the existing
             * call deflection editor to provide the required number.
             */
            number = QtiImsExtUtils.getCallDeflectNumber(mContext.getContentResolver());
            if (number == null) {
                 QtiCallUtils.displayToast(mContext, R.string.qti_ims_transfer_num_error);
                return;
            }
        }

        boolean status = mCall.sendCallTransferRequest(type, number);
        if (!status) {
            QtiCallUtils.displayToast(mContext, R.string.qti_ims_transfer_request_error);
        }
    }

    public void callRecordClicked(boolean startRecording) {
        CallRecorder recorder = CallRecorder.getInstance();
        if (startRecording) {
            Context context = getUi().getContext();
            final SharedPreferences prefs = getPrefs(context);
            boolean warningPresented = prefs.getBoolean(KEY_RECORDING_WARNING_PRESENTED, false);
            if (!warningPresented) {
                new AlertDialog.Builder(context)
                        .setTitle(R.string.recording_warning_title)
                        .setMessage(R.string.recording_warning_text)
                        .setPositiveButton(R.string.onscreenCallRecordText,
                                new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                prefs.edit()
                                        .putBoolean(KEY_RECORDING_WARNING_PRESENTED, true)
                                        .apply();
                                startCallRecordingOrAskForPermission();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            } else {
                startCallRecordingOrAskForPermission();
            }
        } else {
            if (recorder.isRecording()) {
                recorder.finishRecording();
            }
            getUi().setCallRecordingState(recorder.isRecording());
        }
    }

    public void startCallRecording() {
        CallRecorder recorder = CallRecorder.getInstance();
        recorder.startRecording(mCall.getNumber(), mCall.getCreateTimeMillis());
        getUi().setCallRecordingState(recorder.isRecording());
    }

    private void startCallRecordingOrAskForPermission() {
        if (hasAllPermissions(CallRecorder.REQUIRED_PERMISSIONS)) {
            startCallRecording();
        } else {
            getUi().requestCallRecordingPermission(CallRecorder.REQUIRED_PERMISSIONS);
        }
    }

    private boolean hasAllPermissions(String[] permissions) {
        Context context = getUi().getContext();
        for (String p : permissions) {
            if (context.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
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

        if (call == null) {
            return;
        }

        updateButtonsState(call);
    }

    /**
     * Checks if audio route is supported on device
     *
     */
    private boolean isAudioRouteSupported(int route) {
        return (getSupportedAudio() & route) > 0;
    }

    /**
     * Counts number of supported routes and disables audio button if there is only one
     * route supported (i.e nothing to choose from)
     */
    private boolean shouldAudioButtonShow() {
        int numSupportedRoutes = 0;

        int routes[] = {
            CallAudioState.ROUTE_BLUETOOTH,
            CallAudioState.ROUTE_WIRED_OR_EARPIECE,
            CallAudioState.ROUTE_SPEAKER
        };

        for (int i = 0; i < routes.length; i++) {
            if (isAudioRouteSupported(routes[i])) {
                numSupportedRoutes++;
            }
        }

        if (numSupportedRoutes == 0) {
            Log.e(this, "numSupportedRoutes = 0");
        }

        return (numSupportedRoutes > 1);
    }

    /**
     * Updates the buttons applicable for the UI.
     *
     * @param call The active call.
     */
    private void updateButtonsState(Call call) {
        Log.v(this, "updateButtonsState");
        final CallButtonUi ui = getUi();
        final boolean isVideo = VideoUtils.isVideoCall(call);

        // Common functionality (audio, hold, etc).
        // Show either HOLD or SWAP, but not both. If neither HOLD or SWAP is available:
        //     (1) If the device normally can hold, show HOLD in a disabled state.
        //     (2) If the device doesn't have the concept of hold/swap, remove the button.
        final boolean showSwap = call.can(
                android.telecom.Call.Details.CAPABILITY_SWAP_CONFERENCE);
        final boolean showHold = !showSwap
                && call.can(android.telecom.Call.Details.CAPABILITY_SUPPORT_HOLD)
                && call.can(android.telecom.Call.Details.CAPABILITY_HOLD);
        final boolean isCallOnHold = call.getState() == Call.State.ONHOLD;

        final boolean showAddCall = TelecomAdapter.getInstance().canAddCall()
                && UserManagerCompat.isUserUnlocked(ui.getContext());
        final boolean showMerge = call.can(
                android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE);
        final boolean useExt = QtiCallUtils.useExt(ui.getContext());
        final boolean useCustomVideoUi =
                QtiCallUtils.useCustomVideoUi(ui.getContext());
        final boolean isCallActive = call.getState() == Call.State.ACTIVE;

        final boolean showUpgradeToVideo =
                /* When useExt is true, show upgrade button for an active/held
                   call if the call has either voice or video capabilities */
                ((isCallActive || isCallOnHold) &&
                ((useExt && QtiCallUtils.hasVoiceOrVideoCapabilities(call)) ||
                /* When useCustomVideoUi is true, show upgrade button for an active/held
                   voice call only if the current call has video capabilities */
                (useCustomVideoUi && !isVideo && hasVideoCallCapabilities(call)))) ||
                /* When useExt and custom UI are false, default to Google behaviour */
                (!isVideo && !useExt && !useCustomVideoUi && hasVideoCallCapabilities(call));

        final boolean showDowngradeToAudio = isVideo && isDowngradeToAudioSupported(call);
        final boolean showMute = call.can(android.telecom.Call.Details.CAPABILITY_MUTE);
        int callTransferCapabilities = call.isEmergencyCall()? 0 : call.getTransferCapabilities();
        boolean showAddParticipant = call.can(CAPABILITY_ADD_PARTICIPANT);
        if (ui.getContext().getResources().getBoolean(
            R.bool.add_participant_only_in_conference)) {
            showAddParticipant = showAddParticipant&&(call.isConferenceCall());
        }

        boolean showRxTx = false;
        boolean showRx = false;
        boolean showVolte = false;

        if (mEnhanceEnable && hasVideoCallCapabilities(call)) {
            boolean isAudioAndVtCap = (VideoProfile.isAudioOnly(mCall.getVideoState()) &&
                    PresenceHelper.getVTCapability(call.getNumber()));
            showRxTx = ((VideoProfile.isReceptionEnabled(mCall.getVideoState()) &&
                    !VideoProfile.isBidirectional(mCall.getVideoState())) || isAudioAndVtCap);
            //"hide me" show be show if call is video call or voice call only, "hide me"
            //is mean that call can upgrade to Rx video call for voice call only.
            showRx = (VideoProfile.isBidirectional(mCall.getVideoState()) || isAudioAndVtCap);
            showVolte = VideoProfile.isVideo(mCall.getVideoState());
            Log.v(this, "updateButtonsState showRxTx = " + showRxTx +
                    " showRx" + showRx + " showVolte = " + showVolte);
        }

        final CallRecorder recorder = CallRecorder.getInstance();
        boolean showCallRecordOption = recorder.isEnabled()
                && !isVideo && call.getState() == Call.State.ACTIVE;

        ui.showButton(BUTTON_AUDIO, shouldAudioButtonShow());
        ui.showButton(BUTTON_SWAP, showSwap);
        ui.showButton(BUTTON_HOLD, showHold);
        ui.setHold(isCallOnHold);
        ui.showButton(BUTTON_MUTE, showMute);
        ui.showButton(BUTTON_ADD_CALL, showAddCall);
        ui.showButton(BUTTON_UPGRADE_TO_VIDEO, showUpgradeToVideo && !mEnhanceEnable);
        ui.showButton(BUTTON_DOWNGRADE_TO_AUDIO, showDowngradeToAudio && !useExt);
        ui.showButton(BUTTON_SWITCH_CAMERA, isVideo);
        ui.showButton(BUTTON_PAUSE_VIDEO, isVideo && !useExt && !useCustomVideoUi &&
                !mEnhanceEnable);
        if (isVideo) {
            getUi().setVideoPaused(!VideoUtils.isTransmissionEnabled(call));
        }
        ui.showButton(BUTTON_DIALPAD, true);
        ui.showButton(BUTTON_MERGE, showMerge);
        ui.showButton(BUTTON_ADD_PARTICIPANT, showAddParticipant && !mEnhanceEnable);
        ui.showButton(BUTTON_RECORD_CALL, showCallRecordOption);

        /* Depending on the transfer capabilities, display the corresponding buttons */
        if ((callTransferCapabilities & QtiImsExtUtils.QTI_IMS_CONSULTATIVE_TRANSFER) != 0) {
            ui.showButton(BUTTON_TRANSFER_BLIND, true);
            ui.showButton(BUTTON_TRANSFER_ASSURED, true);
            ui.showButton(BUTTON_TRANSFER_CONSULTATIVE, true);
        } else if ((callTransferCapabilities & QtiImsExtUtils.QTI_IMS_BLIND_TRANSFER) != 0) {
            ui.showButton(BUTTON_TRANSFER_BLIND, true);
            ui.showButton(BUTTON_TRANSFER_ASSURED, true);
            ui.showButton(BUTTON_TRANSFER_CONSULTATIVE, false);
        } else {
            ui.showButton(BUTTON_TRANSFER_BLIND, false);
            ui.showButton(BUTTON_TRANSFER_ASSURED, false);
            ui.showButton(BUTTON_TRANSFER_CONSULTATIVE, false);
        }
        if (mEnhanceEnable) {
            Log.v(this, "Add three new buttons");
            ui.showButton(BUTTON_RXTX_VIDEO_CALL, showRxTx);
            ui.showButton(BUTTON_RX_VIDEO_CALL, showRx);
            ui.showButton(BUTTON_VO_VIDEO_CALL, showVolte);
        }

        ui.updateButtonStates();
        ui.updateColors();
    }

    private boolean hasVideoCallCapabilities(Call call) {
        if (SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.M) >= Build.VERSION_CODES.M) {
            return call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL_TX)
                    && call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_REMOTE_RX);
        }
        // In L, this single flag represents both video transmitting and receiving capabilities
        return call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL_TX);
    }

    /**
     * Determines if downgrading from a video call to an audio-only call is supported.  In order to
     * support downgrade to audio, the SDK version must be >= N and the call should NOT have the
     * {@link android.telecom.Call.Details#CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO}.
     * @param call The call.
     * @return {@code true} if downgrading to an audio-only call from a video call is supported.
     */
    private boolean isDowngradeToAudioSupported(Call call) {
        return !call.can(CallSdkCompat.Details.CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO);
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
        void setCallRecordingState(boolean isRecording);
        void requestCallRecordingPermission(String[] permissions);
        void displayDialpad(boolean on, boolean animate);
        boolean isDialpadVisible();

        /**
         * Once showButton() has been called on each of the individual buttons in the UI, call
         * this to configure the overflow menu appropriately.
         */
        void updateButtonStates();
        Context getContext();
        public void updateColors();
    }

    @Override
    public void onActiveCameraSelectionChanged(boolean isUsingFrontFacingCamera) {
        if (getUi() == null) {
            return;
        }
        getUi().setCameraSwitched(!isUsingFrontFacingCamera);
    }

    public void onActiveSubChanged(int subId) {
        InCallState state = InCallPresenter.getInstance()
                .getPotentialStateFromCallList(CallList.getInstance());

        onStateChange(null, state, CallList.getInstance());
        Log.d(this, "onActiveSubChanged");
        CallButtonUi ui = getUi();
        if (ui != null) {
            ui.updateColors();
        }
    }
}
