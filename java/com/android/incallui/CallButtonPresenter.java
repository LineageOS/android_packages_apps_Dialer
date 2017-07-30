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

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.os.UserManagerCompat;
import android.telecom.CallAudioState;
import com.android.contacts.common.compat.CallCompat;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.incallui.InCallCameraManager.Listener;
import com.android.incallui.InCallPresenter.CanAddCallListener;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.audiomode.AudioModeProvider;
import com.android.incallui.audiomode.AudioModeProvider.AudioModeListener;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.DialerCall.CameraDirection;
import com.android.incallui.call.TelecomAdapter;
import com.android.incallui.incall.protocol.InCallButtonIds;
import com.android.incallui.incall.protocol.InCallButtonUi;
import com.android.incallui.incall.protocol.InCallButtonUiDelegate;
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

  private final Context mContext;
  private InCallButtonUi mInCallButtonUi;
  private DialerCall mCall;
  private boolean mAutomaticallyMuted = false;
  private boolean mPreviousMuteState = false;
  private boolean isInCallButtonUiReady;

  public CallButtonPresenter(Context context) {
    mContext = context.getApplicationContext();
  }

  @Override
  public void onInCallButtonUiReady(InCallButtonUi ui) {
    Assert.checkState(!isInCallButtonUiReady);
    mInCallButtonUi = ui;
    AudioModeProvider.getInstance().addListener(this);

    // register for call state changes last
    final InCallPresenter inCallPresenter = InCallPresenter.getInstance();
    inCallPresenter.addListener(this);
    inCallPresenter.addIncomingCallListener(this);
    inCallPresenter.addDetailsListener(this);
    inCallPresenter.addCanAddCallListener(this);
    inCallPresenter.getInCallCameraManager().addCameraSelectionListener(this);

    // Update the buttons state immediately for the current call
    onStateChange(InCallState.NO_CALLS, inCallPresenter.getInCallState(), CallList.getInstance());
    isInCallButtonUiReady = true;
  }

  @Override
  public void onInCallButtonUiUnready() {
    Assert.checkState(isInCallButtonUiReady);
    mInCallButtonUi = null;
    InCallPresenter.getInstance().removeListener(this);
    AudioModeProvider.getInstance().removeListener(this);
    InCallPresenter.getInstance().removeIncomingCallListener(this);
    InCallPresenter.getInstance().removeDetailsListener(this);
    InCallPresenter.getInstance().getInCallCameraManager().removeCameraSelectionListener(this);
    InCallPresenter.getInstance().removeCanAddCallListener(this);
    isInCallButtonUiReady = false;
  }

  @Override
  public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
    if (newState == InCallState.OUTGOING) {
      mCall = callList.getOutgoingCall();
    } else if (newState == InCallState.INCALL) {
      mCall = callList.getActiveOrBackgroundCall();

      // When connected to voice mail, automatically shows the dialpad.
      // (On previous releases we showed it when in-call shows up, before waiting for
      // OUTGOING.  We may want to do that once we start showing "Voice mail" label on
      // the dialpad too.)
      if (oldState == InCallState.OUTGOING && mCall != null) {
        if (CallerInfoUtils.isVoiceMailNumber(mContext, mCall) && getActivity() != null) {
          getActivity().showDialpadFragment(true /* show */, true /* animate */);
        }
      }
    } else if (newState == InCallState.INCOMING) {
      if (getActivity() != null) {
        getActivity().showDialpadFragment(false /* show */, true /* animate */);
      }
      mCall = callList.getIncomingCall();
    } else {
      mCall = null;
    }
    updateUi(newState, mCall);
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
    if (mInCallButtonUi != null && call != null && call.equals(mCall)) {
      updateButtonsState(call);
    }
  }

  @Override
  public void onIncomingCall(InCallState oldState, InCallState newState, DialerCall call) {
    onStateChange(oldState, newState, CallList.getInstance());
  }

  @Override
  public void onCanAddCallChanged(boolean canAddCall) {
    if (mInCallButtonUi != null && mCall != null) {
      updateButtonsState(mCall);
    }
  }

  @Override
  public void onAudioStateChanged(CallAudioState audioState) {
    if (mInCallButtonUi != null) {
      mInCallButtonUi.setAudioState(audioState);
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
      mInCallButtonUi.setAudioState(audioState);
      return;
    }

    int newRoute;
    if (audioState.getRoute() == CallAudioState.ROUTE_SPEAKER) {
      newRoute = CallAudioState.ROUTE_WIRED_OR_EARPIECE;
      Logger.get(mContext)
          .logCallImpression(
              DialerImpression.Type.IN_CALL_SCREEN_TURN_ON_WIRED_OR_EARPIECE,
              mCall.getUniqueCallId(),
              mCall.getTimeAddedMs());
    } else {
      newRoute = CallAudioState.ROUTE_SPEAKER;
      Logger.get(mContext)
          .logCallImpression(
              DialerImpression.Type.IN_CALL_SCREEN_TURN_ON_SPEAKERPHONE,
              mCall.getUniqueCallId(),
              mCall.getTimeAddedMs());
    }

    setAudioRoute(newRoute);
  }

  @Override
  public void muteClicked(boolean checked, boolean clickedByUser) {
    LogUtil.i(
        "CallButtonPresenter", "turning on mute: %s, clicked by user: %s", checked, clickedByUser);
    if (clickedByUser) {
      Logger.get(mContext)
          .logCallImpression(
              checked
                  ? DialerImpression.Type.IN_CALL_SCREEN_TURN_ON_MUTE
                  : DialerImpression.Type.IN_CALL_SCREEN_TURN_OFF_MUTE,
              mCall.getUniqueCallId(),
              mCall.getTimeAddedMs());
    }
    TelecomAdapter.getInstance().mute(checked);
  }

  @Override
  public void holdClicked(boolean checked) {
    if (mCall == null) {
      return;
    }
    if (checked) {
      LogUtil.i("CallButtonPresenter", "putting the call on hold: " + mCall);
      mCall.hold();
    } else {
      LogUtil.i("CallButtonPresenter", "removing the call from hold: " + mCall);
      mCall.unhold();
    }
  }

  @Override
  public void swapClicked() {
    if (mCall == null) {
      return;
    }

    LogUtil.i("CallButtonPresenter", "swapping the call: " + mCall);
    TelecomAdapter.getInstance().swap(mCall.getId());
  }

  @Override
  public void mergeClicked() {
    TelecomAdapter.getInstance().merge(mCall.getId());
  }

  @Override
  public void addCallClicked() {
    // Automatically mute the current call
    mAutomaticallyMuted = true;
    mPreviousMuteState = AudioModeProvider.getInstance().getAudioState().isMuted();
    // Simulate a click on the mute button
    muteClicked(true /* checked */, false /* clickedByUser */);
    TelecomAdapter.getInstance().addCall();
  }

  @Override
  public void showDialpadClicked(boolean checked) {
    LogUtil.v("CallButtonPresenter", "show dialpad " + String.valueOf(checked));
    getActivity().showDialpadFragment(checked /* show */, true /* animate */);
  }

  @Override
  public void changeToVideoClicked() {
    LogUtil.enterBlock("CallButtonPresenter.changeToVideoClicked");
    Logger.get(mContext)
        .logCallImpression(
            DialerImpression.Type.VIDEO_CALL_UPGRADE_REQUESTED,
            mCall.getUniqueCallId(),
            mCall.getTimeAddedMs());
    mCall.getVideoTech().upgradeToVideo();
  }

  @Override
  public void onEndCallClicked() {
    LogUtil.i("CallButtonPresenter.onEndCallClicked", "call: " + mCall);
    if (mCall != null) {
      mCall.disconnect();
    }
  }

  @Override
  public void showAudioRouteSelector() {
    mInCallButtonUi.showAudioRouteSelector();
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
    if (mCall == null) {
      return;
    }
    Logger.get(mContext)
        .logCallImpression(
            DialerImpression.Type.IN_CALL_SCREEN_SWAP_CAMERA,
            mCall.getUniqueCallId(),
            mCall.getTimeAddedMs());
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

    Logger.get(mContext)
        .logCallImpression(
            pause
                ? DialerImpression.Type.IN_CALL_SCREEN_TURN_OFF_VIDEO
                : DialerImpression.Type.IN_CALL_SCREEN_TURN_ON_VIDEO,
            mCall.getUniqueCallId(),
            mCall.getTimeAddedMs());

    if (pause) {
      mCall.getVideoTech().setCamera(null);
      mCall.getVideoTech().stopTransmission();
    } else {
      updateCamera(
          InCallPresenter.getInstance().getInCallCameraManager().isUsingFrontFacingCamera());
      mCall.getVideoTech().resumeTransmission();
    }

    mInCallButtonUi.setVideoPaused(pause);
    mInCallButtonUi.enableButton(InCallButtonIds.BUTTON_PAUSE_VIDEO, false);
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
      mCall.setCameraDir(cameraDir);
      mCall.getVideoTech().setCamera(cameraId);
    }
  }

  private void updateUi(InCallState state, DialerCall call) {
    LogUtil.v("CallButtonPresenter", "updating call UI for call: ", call);

    if (mInCallButtonUi == null) {
      return;
    }

    if (call != null) {
      mInCallButtonUi.updateInCallButtonUiColors();
    }

    final boolean isEnabled =
        state.isConnectingOrConnected() && !state.isIncoming() && call != null;
    mInCallButtonUi.setEnabled(isEnabled);

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
        TelecomAdapter.getInstance().canAddCall() && UserManagerCompat.isUserUnlocked(mContext);
    final boolean showMerge = call.can(android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE);
    final boolean showUpgradeToVideo = !isVideo && (hasVideoCallCapabilities(call));
    final boolean showDowngradeToAudio = isVideo && isDowngradeToAudioSupported(call);
    final boolean showMute = call.can(android.telecom.Call.Details.CAPABILITY_MUTE);

    final boolean hasCameraPermission =
        isVideo && VideoUtils.hasCameraPermissionAndShownPrivacyToast(mContext);
    // Disabling local video doesn't seem to work when dialing. See b/30256571.
    final boolean showPauseVideo =
        isVideo
            && call.getState() != DialerCall.State.DIALING
            && call.getState() != DialerCall.State.CONNECTING;

    mInCallButtonUi.showButton(InCallButtonIds.BUTTON_AUDIO, true);
    mInCallButtonUi.showButton(InCallButtonIds.BUTTON_SWAP, showSwap);
    mInCallButtonUi.showButton(InCallButtonIds.BUTTON_HOLD, showHold);
    mInCallButtonUi.setHold(isCallOnHold);
    mInCallButtonUi.showButton(InCallButtonIds.BUTTON_MUTE, showMute);
    mInCallButtonUi.showButton(InCallButtonIds.BUTTON_ADD_CALL, true);
    mInCallButtonUi.enableButton(InCallButtonIds.BUTTON_ADD_CALL, showAddCall);
    mInCallButtonUi.showButton(InCallButtonIds.BUTTON_UPGRADE_TO_VIDEO, showUpgradeToVideo);
    mInCallButtonUi.showButton(InCallButtonIds.BUTTON_DOWNGRADE_TO_AUDIO, showDowngradeToAudio);
    mInCallButtonUi.showButton(
        InCallButtonIds.BUTTON_SWITCH_CAMERA, isVideo && hasCameraPermission);
    mInCallButtonUi.showButton(InCallButtonIds.BUTTON_PAUSE_VIDEO, showPauseVideo);
    if (isVideo) {
      mInCallButtonUi.setVideoPaused(!call.getVideoTech().isTransmitting() || !hasCameraPermission);
    }
    mInCallButtonUi.showButton(InCallButtonIds.BUTTON_DIALPAD, true);
    mInCallButtonUi.showButton(InCallButtonIds.BUTTON_MERGE, showMerge);

    mInCallButtonUi.updateButtonStates();
  }

  private boolean hasVideoCallCapabilities(DialerCall call) {
    return call.getVideoTech().isAvailable(mContext);
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
    // TODO(b/33676907): If there is an RCS video share session, return true here
    return !call.can(CallCompat.Details.CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO);
  }

  @Override
  public void refreshMuteState() {
    // Restore the previous mute state
    if (mAutomaticallyMuted
        && AudioModeProvider.getInstance().getAudioState().isMuted() != mPreviousMuteState) {
      if (mInCallButtonUi == null) {
        return;
      }
      muteClicked(mPreviousMuteState, false /* clickedByUser */);
    }
    mAutomaticallyMuted = false;
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    outState.putBoolean(KEY_AUTOMATICALLY_MUTED, mAutomaticallyMuted);
    outState.putBoolean(KEY_PREVIOUS_MUTE_STATE, mPreviousMuteState);
  }

  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    mAutomaticallyMuted =
        savedInstanceState.getBoolean(KEY_AUTOMATICALLY_MUTED, mAutomaticallyMuted);
    mPreviousMuteState = savedInstanceState.getBoolean(KEY_PREVIOUS_MUTE_STATE, mPreviousMuteState);
  }

  @Override
  public void onCameraPermissionGranted() {
    if (mCall != null) {
      updateButtonsState(mCall);
    }
  }

  @Override
  public void onActiveCameraSelectionChanged(boolean isUsingFrontFacingCamera) {
    if (mInCallButtonUi == null) {
      return;
    }
    mInCallButtonUi.setCameraSwitched(!isUsingFrontFacingCamera);
  }

  @Override
  public Context getContext() {
    return mContext;
  }

  private InCallActivity getActivity() {
    if (mInCallButtonUi != null) {
      Fragment fragment = mInCallButtonUi.getInCallButtonUiFragment();
      if (fragment != null) {
        return (InCallActivity) fragment.getActivity();
      }
    }
    return null;
  }
}
