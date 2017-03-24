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
import android.telecom.Call;
import android.telecom.Call.Details;
import android.telecom.VideoProfile;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.incallui.video.protocol.VideoCallScreen;
import com.android.incallui.video.protocol.VideoCallScreenDelegate;
import com.android.incallui.videotech.VideoTech;
import com.android.incallui.videotech.utils.SessionModificationState;

/** ViLTE implementation */
public class ImsVideoTech implements VideoTech {
  private final Call call;
  private final VideoTechListener listener;
  private ImsVideoCallCallback callback;
  private @SessionModificationState int sessionModificationState =
      SessionModificationState.NO_REQUEST;
  private int previousVideoState = VideoProfile.STATE_AUDIO_ONLY;

  public ImsVideoTech(VideoTechListener listener, Call call) {
    this.listener = listener;
    this.call = call;
  }

  @Override
  public boolean isAvailable() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return false;
    }

    boolean hasCapabilities =
        call.getDetails().can(Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL_TX)
            && call.getDetails().can(Call.Details.CAPABILITY_SUPPORTS_VT_REMOTE_RX);

    return call.getVideoCall() != null
        && (hasCapabilities || VideoProfile.isVideo(call.getDetails().getVideoState()));
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
  public void onCallStateChanged(int newState) {
    if (!isAvailable()) {
      return;
    }

    if (callback == null) {
      callback = new ImsVideoCallCallback(call, this, listener);
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
  }

  @Override
  public void acceptVideoRequest() {
    int requestedVideoState = callback.getRequestedVideoState();
    Assert.checkArgument(requestedVideoState != VideoProfile.STATE_AUDIO_ONLY);
    LogUtil.i("ImsVideoTech.acceptUpgradeRequest", "videoState: " + requestedVideoState);
    call.getVideoCall().sendSessionModifyResponse(new VideoProfile(requestedVideoState));
    setSessionModificationState(SessionModificationState.NO_REQUEST);
  }

  @Override
  public void acceptVideoRequestAsAudio() {
    LogUtil.enterBlock("ImsVideoTech.acceptVideoRequestAsAudio");
    call.getVideoCall().sendSessionModifyResponse(new VideoProfile(VideoProfile.STATE_AUDIO_ONLY));
    setSessionModificationState(SessionModificationState.NO_REQUEST);
  }

  @Override
  public void declineVideoRequest() {
    LogUtil.enterBlock("ImsVideoTech.declineUpgradeRequest");
    call.getVideoCall()
        .sendSessionModifyResponse(new VideoProfile(call.getDetails().getVideoState()));
    setSessionModificationState(SessionModificationState.NO_REQUEST);
  }

  @Override
  public boolean isTransmitting() {
    return VideoProfile.isTransmissionEnabled(call.getDetails().getVideoState());
  }

  @Override
  public void stopTransmission() {
    LogUtil.enterBlock("ImsVideoTech.stopTransmission");

    int unpausedVideoState = getUnpausedVideoState(call.getDetails().getVideoState());
    call.getVideoCall()
        .sendSessionModifyRequest(
            new VideoProfile(unpausedVideoState & ~VideoProfile.STATE_TX_ENABLED));
  }

  @Override
  public void resumeTransmission() {
    LogUtil.enterBlock("ImsVideoTech.resumeTransmission");

    int unpausedVideoState = getUnpausedVideoState(call.getDetails().getVideoState());
    call.getVideoCall()
        .sendSessionModifyRequest(
            new VideoProfile(unpausedVideoState | VideoProfile.STATE_TX_ENABLED));
    setSessionModificationState(SessionModificationState.WAITING_FOR_RESPONSE);
  }

  @Override
  public void pause() {
    if (canPause()) {
      LogUtil.i("ImsVideoTech.pause", "sending pause request");
      int pausedVideoState = call.getDetails().getVideoState() | VideoProfile.STATE_PAUSED;
      call.getVideoCall().sendSessionModifyRequest(new VideoProfile(pausedVideoState));
    } else {
      LogUtil.i("ImsVideoTech.pause", "not sending request: canPause: %b", canPause());
    }
  }

  @Override
  public void unpause() {
    if (canPause()) {
      LogUtil.i("ImsVideoTech.unpause", "sending unpause request");
      int unpausedVideoState = getUnpausedVideoState(call.getDetails().getVideoState());
      call.getVideoCall().sendSessionModifyRequest(new VideoProfile(unpausedVideoState));
    } else {
      LogUtil.i("ImsVideoTech.unpause", "not sending request: canPause: %b", canPause());
    }
  }

  @Override
  public void setCamera(String cameraId) {
    call.getVideoCall().setCamera(cameraId);
    call.getVideoCall().requestCameraCapabilities();
  }

  @Override
  public void setDeviceOrientation(int rotation) {
    call.getVideoCall().setDeviceOrientation(rotation);
  }

  private boolean canPause() {
    return call.getDetails().can(Details.CAPABILITY_CAN_PAUSE_VIDEO)
        && call.getState() == Call.STATE_ACTIVE;
  }

  static int getUnpausedVideoState(int videoState) {
    return videoState & (~VideoProfile.STATE_PAUSED);
  }
}
