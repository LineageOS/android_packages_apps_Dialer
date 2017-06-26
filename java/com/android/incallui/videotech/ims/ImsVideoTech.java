/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.incallui.videotech.ims;

import android.content.Context;
import android.os.Build;
import android.support.annotation.Nullable;
import android.telecom.Call;
import android.telecom.Call.Details;
import android.telecom.VideoProfile;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.LoggingBindings;
import com.android.dialer.util.CallUtil;
import com.android.incallui.video.protocol.VideoCallScreen;
import com.android.incallui.video.protocol.VideoCallScreenDelegate;
import com.android.incallui.videotech.VideoTech;
import com.android.incallui.videotech.utils.SessionModificationState;

/** ViLTE implementation */
public class ImsVideoTech implements VideoTech {
  private final LoggingBindings logger;
  private final Call call;
  private final VideoTechListener listener;
  private ImsVideoCallCallback callback;
  private @SessionModificationState int sessionModificationState =
      SessionModificationState.NO_REQUEST;
  private int previousVideoState = VideoProfile.STATE_AUDIO_ONLY;
  private boolean paused = false;

  // Hold onto a flag of whether or not stopTransmission was called but resumeTransmission has not
  // been. This is needed because there is time between calling stopTransmission and
  // call.getDetails().getVideoState() reflecting the change. During that time, pause() and
  // unpause() will send the incorrect VideoProfile.
  private boolean transmissionStopped = false;

  public ImsVideoTech(LoggingBindings logger, VideoTechListener listener, Call call) {
    this.logger = logger;
    this.listener = listener;
    this.call = call;
  }

  @Override
  public boolean isAvailable(Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return false;
    }

    if (call.getVideoCall() == null) {
      return false;
    }

    // We are already in an IMS video call
    if (VideoProfile.isVideo(call.getDetails().getVideoState())) {
      return true;
    }

    // The user has disabled IMS video calling in system settings
    if (!CallUtil.isVideoEnabled(context)) {
      return false;
    }

    // The current call doesn't support transmitting video
    if (!call.getDetails().can(Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL_TX)) {
      return false;
    }

    // The current call remote device doesn't support receiving video
    if (!call.getDetails().can(Call.Details.CAPABILITY_SUPPORTS_VT_REMOTE_RX)) {
      return false;
    }

    return true;
  }

  @Override
  public boolean isTransmittingOrReceiving() {
    return VideoProfile.isVideo(call.getDetails().getVideoState());
  }

  @Override
  public boolean isSelfManagedCamera() {
    // Return false to indicate that the answer UI shouldn't open the camera itself.
    // For IMS Video the modem is responsible for opening the camera.
    return false;
  }

  @Override
  public boolean shouldUseSurfaceView() {
    return false;
  }

  @Override
  public VideoCallScreenDelegate createVideoCallScreenDelegate(
      Context context, VideoCallScreen videoCallScreen) {
    // TODO move creating VideoCallPresenter here
    throw Assert.createUnsupportedOperationFailException();
  }

  @Override
  public void onCallStateChanged(Context context, int newState) {
    if (!isAvailable(context)) {
      return;
    }

    if (callback == null) {
      callback = new ImsVideoCallCallback(logger, call, this, listener);
      call.getVideoCall().registerCallback(callback);
    }

    if (getSessionModificationState()
            == SessionModificationState.WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE
        && isTransmittingOrReceiving()) {
      // We don't clear the session modification state right away when we find out the video upgrade
      // request was accepted to avoid having the UI switch from video to voice to video.
      // Once the underlying telecom call updates to video mode it's safe to clear the state.
      LogUtil.i(
          "ImsVideoTech.onCallStateChanged",
          "upgraded to video, clearing session modification state");
      setSessionModificationState(SessionModificationState.NO_REQUEST);
    }

    // Determines if a received upgrade to video request should be cancelled. This can happen if
    // another InCall UI responds to the upgrade to video request.
    int newVideoState = call.getDetails().getVideoState();
    if (newVideoState != previousVideoState
        && sessionModificationState == SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
      LogUtil.i("ImsVideoTech.onCallStateChanged", "cancelling upgrade notification");
      setSessionModificationState(SessionModificationState.NO_REQUEST);
    }
    previousVideoState = newVideoState;
  }

  @Override
  public void onRemovedFromCallList() {}

  @Override
  public int getSessionModificationState() {
    return sessionModificationState;
  }

  void setSessionModificationState(@SessionModificationState int state) {
    if (state != sessionModificationState) {
      LogUtil.i(
          "ImsVideoTech.setSessionModificationState", "%d -> %d", sessionModificationState, state);
      sessionModificationState = state;
      listener.onSessionModificationStateChanged();
    }
  }

  @Override
  public void upgradeToVideo() {
    LogUtil.enterBlock("ImsVideoTech.upgradeToVideo");

    int unpausedVideoState = getUnpausedVideoState(call.getDetails().getVideoState());
    call.getVideoCall()
        .sendSessionModifyRequest(
            new VideoProfile(unpausedVideoState | VideoProfile.STATE_BIDIRECTIONAL));
    setSessionModificationState(SessionModificationState.WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE);
    logger.logImpression(DialerImpression.Type.IMS_VIDEO_UPGRADE_REQUESTED);
  }

  @Override
  public void acceptVideoRequest() {
    int requestedVideoState = callback.getRequestedVideoState();
    Assert.checkArgument(requestedVideoState != VideoProfile.STATE_AUDIO_ONLY);
    LogUtil.i("ImsVideoTech.acceptUpgradeRequest", "videoState: " + requestedVideoState);
    call.getVideoCall().sendSessionModifyResponse(new VideoProfile(requestedVideoState));
    setSessionModificationState(SessionModificationState.NO_REQUEST);
    // Telecom manages audio route for us
    listener.onUpgradedToVideo(false /* switchToSpeaker */);
    logger.logImpression(DialerImpression.Type.IMS_VIDEO_REQUEST_ACCEPTED);
  }

  @Override
  public void acceptVideoRequestAsAudio() {
    LogUtil.enterBlock("ImsVideoTech.acceptVideoRequestAsAudio");
    call.getVideoCall().sendSessionModifyResponse(new VideoProfile(VideoProfile.STATE_AUDIO_ONLY));
    setSessionModificationState(SessionModificationState.NO_REQUEST);
    logger.logImpression(DialerImpression.Type.IMS_VIDEO_REQUEST_ACCEPTED_AS_AUDIO);
  }

  @Override
  public void declineVideoRequest() {
    LogUtil.enterBlock("ImsVideoTech.declineUpgradeRequest");
    call.getVideoCall()
        .sendSessionModifyResponse(new VideoProfile(call.getDetails().getVideoState()));
    setSessionModificationState(SessionModificationState.NO_REQUEST);
    logger.logImpression(DialerImpression.Type.IMS_VIDEO_REQUEST_DECLINED);
  }

  @Override
  public boolean isTransmitting() {
    return VideoProfile.isTransmissionEnabled(call.getDetails().getVideoState());
  }

  @Override
  public void stopTransmission() {
    LogUtil.enterBlock("ImsVideoTech.stopTransmission");

    transmissionStopped = true;

    int unpausedVideoState = getUnpausedVideoState(call.getDetails().getVideoState());
    call.getVideoCall()
        .sendSessionModifyRequest(
            new VideoProfile(unpausedVideoState & ~VideoProfile.STATE_TX_ENABLED));
  }

  @Override
  public void resumeTransmission() {
    LogUtil.enterBlock("ImsVideoTech.resumeTransmission");

    transmissionStopped = false;

    int unpausedVideoState = getUnpausedVideoState(call.getDetails().getVideoState());
    call.getVideoCall()
        .sendSessionModifyRequest(
            new VideoProfile(unpausedVideoState | VideoProfile.STATE_TX_ENABLED));
    setSessionModificationState(SessionModificationState.WAITING_FOR_RESPONSE);
  }

  @Override
  public void pause() {
    if (canPause() && !paused) {
      LogUtil.i("ImsVideoTech.pause", "sending pause request");
      paused = true;
      int pausedVideoState = call.getDetails().getVideoState() | VideoProfile.STATE_PAUSED;
      if (transmissionStopped && VideoProfile.isTransmissionEnabled(pausedVideoState)) {
        LogUtil.i("ImsVideoTech.pause", "overriding TX to false due to user request");
        pausedVideoState &= ~VideoProfile.STATE_TX_ENABLED;
      }
      call.getVideoCall().sendSessionModifyRequest(new VideoProfile(pausedVideoState));
    } else {
      LogUtil.i(
          "ImsVideoTech.pause",
          "not sending request: canPause: %b, paused: %b",
          canPause(),
          paused);
    }
  }

  @Override
  public void unpause() {
    if (canPause() && paused) {
      LogUtil.i("ImsVideoTech.unpause", "sending unpause request");
      paused = false;
      int unpausedVideoState = getUnpausedVideoState(call.getDetails().getVideoState());
      if (transmissionStopped && VideoProfile.isTransmissionEnabled(unpausedVideoState)) {
        LogUtil.i("ImsVideoTech.unpause", "overriding TX to false due to user request");
        unpausedVideoState &= ~VideoProfile.STATE_TX_ENABLED;
      }
      call.getVideoCall().sendSessionModifyRequest(new VideoProfile(unpausedVideoState));
    } else {
      LogUtil.i(
          "ImsVideoTech.unpause",
          "not sending request: canPause: %b, paused: %b",
          canPause(),
          paused);
    }
  }

  @Override
  public void setCamera(@Nullable String cameraId) {
    call.getVideoCall().setCamera(cameraId);
    call.getVideoCall().requestCameraCapabilities();
  }

  @Override
  public void setDeviceOrientation(int rotation) {
    call.getVideoCall().setDeviceOrientation(rotation);
  }

  private boolean canPause() {
    return call.getDetails().can(Details.CAPABILITY_CAN_PAUSE_VIDEO)
        && call.getState() == Call.STATE_ACTIVE
        && isTransmittingOrReceiving();
  }

  static int getUnpausedVideoState(int videoState) {
    return videoState & (~VideoProfile.STATE_PAUSED);
  }
}
