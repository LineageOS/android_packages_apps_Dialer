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

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Trace;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.os.UserManagerCompat;
import android.telecom.CallAudioState;
import android.telecom.PhoneAccountHandle;
import com.android.contacts.common.compat.CallCompat;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.DialerImpression.Type;
import com.android.dialer.logging.Logger;
import com.android.dialer.telecom.TelecomUtil;
import com.android.incallui.InCallCameraManager.Listener;
import com.android.incallui.InCallPresenter.CanAddCallListener;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.audiomode.AudioModeProvider;
import com.android.incallui.audiomode.AudioModeProvider.AudioModeListener;
import com.android.incallui.call.CallList;
import com.android.incallui.call.CallRecorder;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.DialerCall.CameraDirection;
import com.android.incallui.call.TelecomAdapter;
import com.android.incallui.incall.protocol.InCallButtonIds;
import com.android.incallui.incall.protocol.InCallButtonUi;
import com.android.incallui.incall.protocol.InCallButtonUiDelegate;
import com.android.incallui.multisim.SwapSimWorker;
import com.android.incallui.videotech.utils.VideoUtils;

/** Logic for call buttons. */
public class CallButtonPresenter
    implements InCallStateListener,
        AudioModeListener,
        IncomingCallListener,
        InCallDetailsListener,
        CanAddCallListener,
        Listener,
        InCallButtonUiDelegate {

  private static final String KEY_AUTOMATICALLY_MUTED = "incall_key_automatically_muted";
  private static final String KEY_PREVIOUS_MUTE_STATE = "incall_key_previous_mute_state";

  private static final String KEY_RECORDING_WARNING_PRESENTED = "recording_warning_presented";

  private final Context context;
  private InCallButtonUi inCallButtonUi;
  private DialerCall call;
  private boolean automaticallyMuted = false;
  private boolean previousMuteState = false;
  private boolean isInCallButtonUiReady;
  private PhoneAccountHandle otherAccount;

  private CallRecorder.RecordingProgressListener recordingProgressListener =
      new CallRecorder.RecordingProgressListener() {
    @Override
    public void onStartRecording() {
      inCallButtonUi.setCallRecordingState(true);
      inCallButtonUi.setCallRecordingDuration(0);
    }

    @Override
    public void onStopRecording() {
      inCallButtonUi.setCallRecordingState(false);
    }

    @Override
    public void onRecordingTimeProgress(final long elapsedTimeMs) {
      inCallButtonUi.setCallRecordingDuration(elapsedTimeMs);
    }
  };

  public CallButtonPresenter(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public void onInCallButtonUiReady(InCallButtonUi ui) {
    Assert.checkState(!isInCallButtonUiReady);
    inCallButtonUi = ui;
    AudioModeProvider.getInstance().addListener(this);

    // register for call state changes last
    final InCallPresenter inCallPresenter = InCallPresenter.getInstance();
    inCallPresenter.addListener(this);
    inCallPresenter.addIncomingCallListener(this);
    inCallPresenter.addDetailsListener(this);
    inCallPresenter.addCanAddCallListener(this);
    inCallPresenter.getInCallCameraManager().addCameraSelectionListener(this);

    CallRecorder recorder = CallRecorder.getInstance();
    recorder.addRecordingProgressListener(recordingProgressListener);

    // Update the buttons state immediately for the current call
    onStateChange(InCallState.NO_CALLS, inCallPresenter.getInCallState(), CallList.getInstance());
    isInCallButtonUiReady = true;
  }

  @Override
  public void onInCallButtonUiUnready() {
    Assert.checkState(isInCallButtonUiReady);
    inCallButtonUi = null;
    InCallPresenter.getInstance().removeListener(this);
    AudioModeProvider.getInstance().removeListener(this);
    InCallPresenter.getInstance().removeIncomingCallListener(this);
    InCallPresenter.getInstance().removeDetailsListener(this);
    InCallPresenter.getInstance().getInCallCameraManager().removeCameraSelectionListener(this);
    InCallPresenter.getInstance().removeCanAddCallListener(this);

    CallRecorder recorder = CallRecorder.getInstance();
    recorder.removeRecordingProgressListener(recordingProgressListener);

    isInCallButtonUiReady = false;
  }

  @Override
  public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
    Trace.beginSection("CallButtonPresenter.onStateChange");
    if (newState == InCallState.OUTGOING) {
      call = callList.getOutgoingCall();
    } else if (newState == InCallState.INCALL) {
      call = callList.getActiveOrBackgroundCall();

      // When connected to voice mail, automatically shows the dialpad.
      // (On previous releases we showed it when in-call shows up, before waiting for
      // OUTGOING.  We may want to do that once we start showing "Voice mail" label on
      // the dialpad too.)
      if (oldState == InCallState.OUTGOING && call != null) {
        if (call.isVoiceMailNumber() && getActivity() != null) {
          getActivity().showDialpadFragment(true /* show */, true /* animate */);
        }
      }
    } else if (newState == InCallState.INCOMING) {
      if (getActivity() != null) {
        getActivity().showDialpadFragment(false /* show */, true /* animate */);
      }
      call = callList.getIncomingCall();
    } else {
      call = null;
    }
    updateUi(newState, call);
    Trace.endSection();
  }

  /**
   * Updates the user interface in response to a change in the details of a call. Currently handles
   * changes to the call buttons in response to a change in the details for a call. This is
   * important to ensure changes to the active call are reflected in the available buttons.
   *
   * @param call The active call.
   * @param details The call details.
   */
  @Override
  public void onDetailsChanged(DialerCall call, android.telecom.Call.Details details) {
    // Only update if the changes are for the currently active call
    if (inCallButtonUi != null && call != null && call.equals(this.call)) {
      updateButtonsState(call);
    }
  }

  @Override
  public void onIncomingCall(InCallState oldState, InCallState newState, DialerCall call) {
    onStateChange(oldState, newState, CallList.getInstance());
  }

  @Override
  public void onCanAddCallChanged(boolean canAddCall) {
    if (inCallButtonUi != null && call != null) {
      updateButtonsState(call);
    }
  }

  @Override
  public void onAudioStateChanged(CallAudioState audioState) {
    if (inCallButtonUi != null) {
      inCallButtonUi.setAudioState(audioState);
    }
  }

  @Override
  public CallAudioState getCurrentAudioState() {
    return AudioModeProvider.getInstance().getAudioState();
  }

  @Override
  public void setAudioRoute(int route) {
    LogUtil.i(
        "CallButtonPresenter.setAudioRoute",
        "sending new audio route: " + CallAudioState.audioRouteToString(route));
    TelecomAdapter.getInstance().setAudioRoute(route);
  }

  /** Function assumes that bluetooth is not supported. */
  @Override
  public void toggleSpeakerphone() {
    // This function should not be called if bluetooth is available.
    CallAudioState audioState = getCurrentAudioState();
    if (0 != (CallAudioState.ROUTE_BLUETOOTH & audioState.getSupportedRouteMask())) {
      // It's clear the UI is wrong, so update the supported mode once again.
      LogUtil.e(
          "CallButtonPresenter", "toggling speakerphone not allowed when bluetooth supported.");
      inCallButtonUi.setAudioState(audioState);
      return;
    }

    int newRoute;
    if (audioState.getRoute() == CallAudioState.ROUTE_SPEAKER) {
      newRoute = CallAudioState.ROUTE_WIRED_OR_EARPIECE;
      Logger.get(context)
          .logCallImpression(
              DialerImpression.Type.IN_CALL_SCREEN_TURN_ON_WIRED_OR_EARPIECE,
              call.getUniqueCallId(),
              call.getTimeAddedMs());
    } else {
      newRoute = CallAudioState.ROUTE_SPEAKER;
      Logger.get(context)
          .logCallImpression(
              DialerImpression.Type.IN_CALL_SCREEN_TURN_ON_SPEAKERPHONE,
              call.getUniqueCallId(),
              call.getTimeAddedMs());
    }

    setAudioRoute(newRoute);
  }

  @Override
  public void muteClicked(boolean checked, boolean clickedByUser) {
    LogUtil.i(
        "CallButtonPresenter", "turning on mute: %s, clicked by user: %s", checked, clickedByUser);
    if (clickedByUser) {
      Logger.get(context)
          .logCallImpression(
              checked
                  ? DialerImpression.Type.IN_CALL_SCREEN_TURN_ON_MUTE
                  : DialerImpression.Type.IN_CALL_SCREEN_TURN_OFF_MUTE,
              call.getUniqueCallId(),
              call.getTimeAddedMs());
    }
    TelecomAdapter.getInstance().mute(checked);
  }

  @Override
  public void holdClicked(boolean checked) {
    if (call == null) {
      return;
    }
    if (checked) {
      LogUtil.i("CallButtonPresenter", "putting the call on hold: " + call);
      call.hold();
    } else {
      LogUtil.i("CallButtonPresenter", "removing the call from hold: " + call);
      call.unhold();
    }
  }

  @Override
  public void swapClicked() {
    if (call == null) {
      return;
    }

    LogUtil.i("CallButtonPresenter", "swapping the call: " + call);
    TelecomAdapter.getInstance().swap(call.getId());
  }

  @Override
  public void mergeClicked() {
    Logger.get(context)
        .logCallImpression(
            DialerImpression.Type.IN_CALL_MERGE_BUTTON_PRESSED,
            call.getUniqueCallId(),
            call.getTimeAddedMs());
    TelecomAdapter.getInstance().merge(call.getId());
  }

  @Override
  public void addCallClicked() {
    Logger.get(context)
        .logCallImpression(
            DialerImpression.Type.IN_CALL_ADD_CALL_BUTTON_PRESSED,
            call.getUniqueCallId(),
            call.getTimeAddedMs());
    // Automatically mute the current call
    automaticallyMuted = true;
    previousMuteState = AudioModeProvider.getInstance().getAudioState().isMuted();
    // Simulate a click on the mute button
    muteClicked(true /* checked */, false /* clickedByUser */);
    TelecomAdapter.getInstance().addCall();
  }

  @Override
  public void showDialpadClicked(boolean checked) {
    Logger.get(context)
        .logCallImpression(
            DialerImpression.Type.IN_CALL_SHOW_DIALPAD_BUTTON_PRESSED,
            call.getUniqueCallId(),
            call.getTimeAddedMs());
    LogUtil.v("CallButtonPresenter", "show dialpad " + String.valueOf(checked));
    getActivity().showDialpadFragment(checked /* show */, true /* animate */);
  }

  @Override
  public void callRecordClicked(boolean checked) {
    CallRecorder recorder = CallRecorder.getInstance();
    if (checked) {
      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
      boolean warningPresented = prefs.getBoolean(KEY_RECORDING_WARNING_PRESENTED, false);
      if (!warningPresented) {
        new AlertDialog.Builder(getActivity())
            .setTitle(R.string.recording_warning_title)
            .setMessage(R.string.recording_warning_text)
            .setPositiveButton(R.string.onscreenCallRecordText, (dialog, which) -> {
              prefs.edit()
                  .putBoolean(KEY_RECORDING_WARNING_PRESENTED, true)
                  .apply();
              startCallRecordingOrAskForPermission();
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
    }
  }

  private void startCallRecordingOrAskForPermission() {
    if (hasAllPermissions(CallRecorder.REQUIRED_PERMISSIONS)) {
      CallRecorder recorder = CallRecorder.getInstance();
      recorder.startRecording(call.getNumber(), call.getCreationTimeMillis());
    } else {
      inCallButtonUi.requestCallRecordingPermissions(CallRecorder.REQUIRED_PERMISSIONS);
    }
  }

  private boolean hasAllPermissions(String[] permissions) {
    for (String p : permissions) {
      if (context.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void changeToVideoClicked() {
    LogUtil.enterBlock("CallButtonPresenter.changeToVideoClicked");
    Logger.get(context)
        .logCallImpression(
            DialerImpression.Type.VIDEO_CALL_UPGRADE_REQUESTED,
            call.getUniqueCallId(),
            call.getTimeAddedMs());
    call.getVideoTech().upgradeToVideo(context);
  }

  @Override
  public void onEndCallClicked() {
    LogUtil.i("CallButtonPresenter.onEndCallClicked", "call: " + call);
    if (call != null) {
      call.disconnect();
    }
  }

  @Override
  public void showAudioRouteSelector() {
    inCallButtonUi.showAudioRouteSelector();
  }

  @Override
  public void swapSimClicked() {
    LogUtil.enterBlock("CallButtonPresenter.swapSimClicked");
    Logger.get(getContext()).logImpression(Type.DUAL_SIM_CHANGE_SIM_PRESSED);
    SwapSimWorker worker =
        new SwapSimWorker(
            getContext(),
            call,
            InCallPresenter.getInstance().getCallList(),
            otherAccount,
            InCallPresenter.getInstance().acquireInCallUiLock("swapSim"));
    DialerExecutorComponent.get(getContext())
        .dialerExecutorFactory()
        .createNonUiTaskBuilder(worker)
        .build()
        .executeParallel(null);
  }

  /**
   * Switches the camera between the front-facing and back-facing camera.
   *
   * @param useFrontFacingCamera True if we should switch to using the front-facing camera, or false
   *     if we should switch to using the back-facing camera.
   */
  @Override
  public void switchCameraClicked(boolean useFrontFacingCamera) {
    updateCamera(useFrontFacingCamera);
  }

  @Override
  public void toggleCameraClicked() {
    LogUtil.i("CallButtonPresenter.toggleCameraClicked", "");
    if (call == null) {
      return;
    }
    Logger.get(context)
        .logCallImpression(
            DialerImpression.Type.IN_CALL_SCREEN_SWAP_CAMERA,
            call.getUniqueCallId(),
            call.getTimeAddedMs());
    switchCameraClicked(
        !InCallPresenter.getInstance().getInCallCameraManager().isUsingFrontFacingCamera());
  }

  /**
   * Stop or start client's video transmission.
   *
   * @param pause True if pausing the local user's video, or false if starting the local user's
   *     video.
   */
  @Override
  public void pauseVideoClicked(boolean pause) {
    LogUtil.i("CallButtonPresenter.pauseVideoClicked", "%s", pause ? "pause" : "unpause");

    Logger.get(context)
        .logCallImpression(
            pause
                ? DialerImpression.Type.IN_CALL_SCREEN_TURN_OFF_VIDEO
                : DialerImpression.Type.IN_CALL_SCREEN_TURN_ON_VIDEO,
            call.getUniqueCallId(),
            call.getTimeAddedMs());

    if (pause) {
      call.getVideoTech().setCamera(null);
      call.getVideoTech().stopTransmission();
    } else {
      updateCamera(
          InCallPresenter.getInstance().getInCallCameraManager().isUsingFrontFacingCamera());
      call.getVideoTech().resumeTransmission(context);
    }

    inCallButtonUi.setVideoPaused(pause);
    inCallButtonUi.enableButton(InCallButtonIds.BUTTON_PAUSE_VIDEO, false);
  }

  private void updateCamera(boolean useFrontFacingCamera) {
    InCallCameraManager cameraManager = InCallPresenter.getInstance().getInCallCameraManager();
    cameraManager.setUseFrontFacingCamera(useFrontFacingCamera);

    String cameraId = cameraManager.getActiveCameraId();
    if (cameraId != null) {
      final int cameraDir =
          cameraManager.isUsingFrontFacingCamera()
              ? CameraDirection.CAMERA_DIRECTION_FRONT_FACING
              : CameraDirection.CAMERA_DIRECTION_BACK_FACING;
      call.setCameraDir(cameraDir);
      call.getVideoTech().setCamera(cameraId);
    }
  }

  private void updateUi(InCallState state, DialerCall call) {
    LogUtil.v("CallButtonPresenter", "updating call UI for call: %s", call);

    if (inCallButtonUi == null) {
      return;
    }

    if (call != null) {
      inCallButtonUi.updateInCallButtonUiColors(
          InCallPresenter.getInstance().getThemeColorManager().getSecondaryColor());
    }

    final boolean isEnabled =
        state.isConnectingOrConnected() && !state.isIncoming() && call != null;
    inCallButtonUi.setEnabled(isEnabled);

    if (call == null) {
      return;
    }

    updateButtonsState(call);
  }

  /**
   * Updates the buttons applicable for the UI.
   *
   * @param call The active call.
   */
  @SuppressWarnings("MissingPermission")
  private void updateButtonsState(DialerCall call) {
    LogUtil.v("CallButtonPresenter.updateButtonsState", "");
    final boolean isVideo = call.isVideoCall();

    // Common functionality (audio, hold, etc).
    // Show either HOLD or SWAP, but not both. If neither HOLD or SWAP is available:
    //     (1) If the device normally can hold, show HOLD in a disabled state.
    //     (2) If the device doesn't have the concept of hold/swap, remove the button.
    final boolean showSwap = call.can(android.telecom.Call.Details.CAPABILITY_SWAP_CONFERENCE);
    final boolean showHold =
        !showSwap
            && call.can(android.telecom.Call.Details.CAPABILITY_SUPPORT_HOLD)
            && call.can(android.telecom.Call.Details.CAPABILITY_HOLD);
    final boolean isCallOnHold = call.getState() == DialerCall.State.ONHOLD;

    final boolean showAddCall =
        TelecomAdapter.getInstance().canAddCall() && UserManagerCompat.isUserUnlocked(context);
    final boolean showMerge = call.can(android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE);
    final boolean showUpgradeToVideo = !isVideo && (hasVideoCallCapabilities(call));
    final boolean showDowngradeToAudio = isVideo && isDowngradeToAudioSupported(call);
    final boolean showMute = call.can(android.telecom.Call.Details.CAPABILITY_MUTE);

    final boolean hasCameraPermission =
        isVideo && VideoUtils.hasCameraPermissionAndShownPrivacyToast(context);
    // Disabling local video doesn't seem to work when dialing. See a bug.
    final boolean showPauseVideo =
        isVideo
            && call.getState() != DialerCall.State.DIALING
            && call.getState() != DialerCall.State.CONNECTING;

    final CallRecorder recorder = CallRecorder.getInstance();
    final boolean showCallRecordOption = recorder.isEnabled()
        && !isVideo && call.getState() == DialerCall.State.ACTIVE;

    otherAccount = TelecomUtil.getOtherAccount(getContext(), call.getAccountHandle());
    boolean showSwapSim =
        otherAccount != null
            && !call.isVoiceMailNumber()
            && DialerCall.State.isDialing(call.getState())
            // Most devices cannot make calls on 2 SIMs at the same time.
            && InCallPresenter.getInstance().getCallList().getAllCalls().size() == 1;

    inCallButtonUi.showButton(InCallButtonIds.BUTTON_AUDIO, true);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_SWAP, showSwap);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_HOLD, showHold);
    inCallButtonUi.setHold(isCallOnHold);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_MUTE, showMute);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_SWAP_SIM, showSwapSim);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_ADD_CALL, true);
    inCallButtonUi.enableButton(InCallButtonIds.BUTTON_ADD_CALL, showAddCall);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_UPGRADE_TO_VIDEO, showUpgradeToVideo);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_DOWNGRADE_TO_AUDIO, showDowngradeToAudio);
    inCallButtonUi.showButton(
        InCallButtonIds.BUTTON_SWITCH_CAMERA,
        isVideo && hasCameraPermission && call.getVideoTech().isTransmitting());
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_PAUSE_VIDEO, showPauseVideo);
    if (isVideo) {
      inCallButtonUi.setVideoPaused(!call.getVideoTech().isTransmitting() || !hasCameraPermission);
    }
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_DIALPAD, true);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_MERGE, showMerge);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_RECORD_CALL, showCallRecordOption);

    inCallButtonUi.updateButtonStates();
  }

  private boolean hasVideoCallCapabilities(DialerCall call) {
    return call.getVideoTech().isAvailable(context, call.getAccountHandle());
  }

  /**
   * Determines if downgrading from a video call to an audio-only call is supported. In order to
   * support downgrade to audio, the SDK version must be >= N and the call should NOT have the
   * {@link android.telecom.Call.Details#CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO}.
   *
   * @param call The call.
   * @return {@code true} if downgrading to an audio-only call from a video call is supported.
   */
  private boolean isDowngradeToAudioSupported(DialerCall call) {
    // TODO(a bug): If there is an RCS video share session, return true here
    return !call.can(CallCompat.Details.CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO);
  }

  @Override
  public void refreshMuteState() {
    // Restore the previous mute state
    if (automaticallyMuted
        && AudioModeProvider.getInstance().getAudioState().isMuted() != previousMuteState) {
      if (inCallButtonUi == null) {
        return;
      }
      muteClicked(previousMuteState, false /* clickedByUser */);
    }
    automaticallyMuted = false;
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    outState.putBoolean(KEY_AUTOMATICALLY_MUTED, automaticallyMuted);
    outState.putBoolean(KEY_PREVIOUS_MUTE_STATE, previousMuteState);
  }

  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    automaticallyMuted = savedInstanceState.getBoolean(KEY_AUTOMATICALLY_MUTED, automaticallyMuted);
    previousMuteState = savedInstanceState.getBoolean(KEY_PREVIOUS_MUTE_STATE, previousMuteState);
  }

  @Override
  public void onCameraPermissionGranted() {
    if (call != null) {
      updateButtonsState(call);
    }
  }

  @Override
  public void onActiveCameraSelectionChanged(boolean isUsingFrontFacingCamera) {
    if (inCallButtonUi == null) {
      return;
    }
    inCallButtonUi.setCameraSwitched(!isUsingFrontFacingCamera);
  }

  @Override
  public Context getContext() {
    return context;
  }

  private InCallActivity getActivity() {
    if (inCallButtonUi != null) {
      Fragment fragment = inCallButtonUi.getInCallButtonUiFragment();
      if (fragment != null) {
        return (InCallActivity) fragment.getActivity();
      }
    }
    return null;
  }
}
