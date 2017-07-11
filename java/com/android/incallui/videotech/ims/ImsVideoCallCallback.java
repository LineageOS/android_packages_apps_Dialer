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

import android.os.Handler;
import android.telecom.Call;
import android.telecom.Connection;
import android.telecom.Connection.VideoProvider;
import android.telecom.InCallService.VideoCall;
import android.telecom.VideoProfile;
import android.telecom.VideoProfile.CameraCapabilities;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.LoggingBindings;
import com.android.incallui.videotech.VideoTech.VideoTechListener;
import com.android.incallui.videotech.utils.SessionModificationState;

/** Receives IMS video call state updates. */
public class ImsVideoCallCallback extends VideoCall.Callback {
  private static final int CLEAR_FAILED_REQUEST_TIMEOUT_MILLIS = 4000;
  private final Handler handler = new Handler();
  private final LoggingBindings logger;
  private final Call call;
  private final ImsVideoTech videoTech;
  private final VideoTechListener listener;
  private int requestedVideoState = VideoProfile.STATE_AUDIO_ONLY;

  ImsVideoCallCallback(
      final LoggingBindings logger,
      final Call call,
      ImsVideoTech videoTech,
      VideoTechListener listener) {
    this.logger = logger;
    this.call = call;
    this.videoTech = videoTech;
    this.listener = listener;
  }

  @Override
  public void onSessionModifyRequestReceived(VideoProfile videoProfile) {
    LogUtil.i(
        "ImsVideoCallCallback.onSessionModifyRequestReceived", "videoProfile: " + videoProfile);

    int previousVideoState = ImsVideoTech.getUnpausedVideoState(call.getDetails().getVideoState());
    int newVideoState = ImsVideoTech.getUnpausedVideoState(videoProfile.getVideoState());

    boolean wasVideoCall = VideoProfile.isVideo(previousVideoState);
    boolean isVideoCall = VideoProfile.isVideo(newVideoState);

    if (wasVideoCall && !isVideoCall) {
      LogUtil.i(
          "ImsVideoTech.onSessionModifyRequestReceived", "call downgraded to %d", newVideoState);
    } else if (previousVideoState != newVideoState) {
      requestedVideoState = newVideoState;
      videoTech.setSessionModificationState(
          SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST);
      listener.onVideoUpgradeRequestReceived();
      logger.logImpression(DialerImpression.Type.IMS_VIDEO_REQUEST_RECEIVED);
    }
  }

  /**
   * @param status Status of the session modify request. Valid values are {@link
   *     Connection.VideoProvider#SESSION_MODIFY_REQUEST_SUCCESS}, {@link
   *     Connection.VideoProvider#SESSION_MODIFY_REQUEST_FAIL}, {@link
   *     Connection.VideoProvider#SESSION_MODIFY_REQUEST_INVALID}
   * @param responseProfile The actual profile changes made by the peer device.
   */
  @Override
  public void onSessionModifyResponseReceived(
      int status, VideoProfile requestedProfile, VideoProfile responseProfile) {
    LogUtil.i(
        "ImsVideoCallCallback.onSessionModifyResponseReceived",
        "status: %d, requestedProfile: %s, responseProfile: %s, session modification state: %d",
        status,
        requestedProfile,
        responseProfile,
        videoTech.getSessionModificationState());

    if (videoTech.getSessionModificationState()
        == SessionModificationState.WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE) {
      handler.removeCallbacksAndMessages(null); // Clear everything

      final int newSessionModificationState = getSessionModificationStateFromTelecomStatus(status);
      if (status == VideoProvider.SESSION_MODIFY_REQUEST_SUCCESS) {
        // Telecom manages audio route for us
        listener.onUpgradedToVideo(false /* switchToSpeaker */);
      } else {
        // This will update the video UI to display the error message.
        videoTech.setSessionModificationState(newSessionModificationState);
      }

      // Wait for 4 seconds and then clean the session modification state. This allows the video UI
      // to stay up so that the user can read the error message.
      //
      // If the other person accepted the upgrade request then this will keep the video UI up until
      // the call's video state change. Without this we would switch to the voice call and then
      // switch back to video UI.
      handler.postDelayed(
          () -> {
            if (videoTech.getSessionModificationState() == newSessionModificationState) {
              LogUtil.i("ImsVideoCallCallback.onSessionModifyResponseReceived", "clearing state");
              videoTech.setSessionModificationState(SessionModificationState.NO_REQUEST);
            } else {
              LogUtil.i(
                  "ImsVideoCallCallback.onSessionModifyResponseReceived",
                  "session modification state has changed, not clearing state");
            }
          },
          CLEAR_FAILED_REQUEST_TIMEOUT_MILLIS);
    } else if (videoTech.getSessionModificationState()
        == SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
      videoTech.setSessionModificationState(SessionModificationState.NO_REQUEST);
    } else if (videoTech.getSessionModificationState()
        == SessionModificationState.WAITING_FOR_RESPONSE) {
      videoTech.setSessionModificationState(getSessionModificationStateFromTelecomStatus(status));
    } else {
      LogUtil.i(
          "ImsVideoCallCallback.onSessionModifyResponseReceived",
          "call is not waiting for response, doing nothing");
    }
  }

  @SessionModificationState
  private int getSessionModificationStateFromTelecomStatus(int telecomStatus) {
    switch (telecomStatus) {
      case VideoProvider.SESSION_MODIFY_REQUEST_SUCCESS:
        return SessionModificationState.NO_REQUEST;
      case VideoProvider.SESSION_MODIFY_REQUEST_FAIL:
      case VideoProvider.SESSION_MODIFY_REQUEST_INVALID:
        // Check if it's already video call, which means the request is not video upgrade request.
        if (VideoProfile.isVideo(call.getDetails().getVideoState())) {
          return SessionModificationState.REQUEST_FAILED;
        } else {
          return SessionModificationState.UPGRADE_TO_VIDEO_REQUEST_FAILED;
        }
      case VideoProvider.SESSION_MODIFY_REQUEST_TIMED_OUT:
        return SessionModificationState.UPGRADE_TO_VIDEO_REQUEST_TIMED_OUT;
      case VideoProvider.SESSION_MODIFY_REQUEST_REJECTED_BY_REMOTE:
        return SessionModificationState.REQUEST_REJECTED;
      default:
        LogUtil.e(
            "ImsVideoCallCallback.getSessionModificationStateFromTelecomStatus",
            "unknown status: %d",
            telecomStatus);
        return SessionModificationState.REQUEST_FAILED;
    }
  }

  // In the vendor code rx_pause and rx_resume get triggered when the video player starts or stops
  // playing the incoming video stream.  For the case where you're resuming a held call, its
  // definitely a good signal to use to know that the video is resuming (though the video state
  // should change to indicate its not paused in this case as well).  However, keep in mind you'll
  // get these signals as well on carriers that don't support the video pause signalling (like TMO)
  // so you want to ensure you don't send sessionModifyRequests with pause/resume based on these
  // signals. Also, its technically possible to have a pause/resume if the video signal degrades.
  @Override
  public void onCallSessionEvent(int event) {
    switch (event) {
      case Connection.VideoProvider.SESSION_EVENT_RX_PAUSE:
        LogUtil.i("ImsVideoCallCallback.onCallSessionEvent", "rx_pause");
        break;
      case Connection.VideoProvider.SESSION_EVENT_RX_RESUME:
        LogUtil.i("ImsVideoCallCallback.onCallSessionEvent", "rx_resume");
        break;
      case Connection.VideoProvider.SESSION_EVENT_CAMERA_FAILURE:
        LogUtil.i("ImsVideoCallCallback.onCallSessionEvent", "camera_failure");
        break;
      case Connection.VideoProvider.SESSION_EVENT_CAMERA_READY:
        LogUtil.i("ImsVideoCallCallback.onCallSessionEvent", "camera_ready");
        break;
      default:
        LogUtil.i("ImsVideoCallCallback.onCallSessionEvent", "unknown event = : " + event);
        break;
    }
  }

  @Override
  public void onPeerDimensionsChanged(int width, int height) {
    listener.onPeerDimensionsChanged(width, height);
  }

  @Override
  public void onVideoQualityChanged(int videoQuality) {
    LogUtil.i("ImsVideoCallCallback.onVideoQualityChanged", "videoQuality: %d", videoQuality);
  }

  @Override
  public void onCallDataUsageChanged(long dataUsage) {
    LogUtil.i("ImsVideoCallCallback.onCallDataUsageChanged", "dataUsage: %d", dataUsage);
  }

  @Override
  public void onCameraCapabilitiesChanged(CameraCapabilities cameraCapabilities) {
    if (cameraCapabilities != null) {
      listener.onCameraDimensionsChanged(
          cameraCapabilities.getWidth(), cameraCapabilities.getHeight());
    }
  }

  int getRequestedVideoState() {
    return requestedVideoState;
  }
}
