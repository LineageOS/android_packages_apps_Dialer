/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.incallui.call;

import android.os.Handler;
import android.support.annotation.Nullable;
import android.telecom.Connection;
import android.telecom.Connection.VideoProvider;
import android.telecom.InCallService.VideoCall;
import android.telecom.VideoProfile;
import android.telecom.VideoProfile.CameraCapabilities;
import com.android.dialer.common.LogUtil;
import com.android.incallui.call.DialerCall.SessionModificationState;

/** Implements the InCallUI VideoCall Callback. */
public class InCallVideoCallCallback extends VideoCall.Callback implements Runnable {

  private static final int CLEAR_FAILED_REQUEST_TIMEOUT_MILLIS = 4000;

  private final DialerCall call;
  @Nullable private Handler handler;
  @SessionModificationState private int newSessionModificationState;

  public InCallVideoCallCallback(DialerCall call) {
    this.call = call;
  }

  @Override
  public void onSessionModifyRequestReceived(VideoProfile videoProfile) {
    LogUtil.i(
        "InCallVideoCallCallback.onSessionModifyRequestReceived", "videoProfile: " + videoProfile);
    int previousVideoState = VideoUtils.getUnPausedVideoState(call.getVideoState());
    int newVideoState = VideoUtils.getUnPausedVideoState(videoProfile.getVideoState());

    boolean wasVideoCall = VideoUtils.isVideoCall(previousVideoState);
    boolean isVideoCall = VideoUtils.isVideoCall(newVideoState);

    if (wasVideoCall && !isVideoCall) {
      LogUtil.v(
          "InCallVideoCallCallback.onSessionModifyRequestReceived",
          "call downgraded to " + newVideoState);
    } else if (previousVideoState != newVideoState) {
      InCallVideoCallCallbackNotifier.getInstance().upgradeToVideoRequest(call, newVideoState);
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
        "InCallVideoCallCallback.onSessionModifyResponseReceived",
        "status: %d, "
            + "requestedProfile: %s, responseProfile: %s, current session modification state: %d",
        status,
        requestedProfile,
        responseProfile,
        call.getSessionModificationState());

    if (call.getSessionModificationState()
        == DialerCall.SESSION_MODIFICATION_STATE_WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE) {
      if (handler == null) {
        handler = new Handler();
      } else {
        handler.removeCallbacks(this);
      }

      newSessionModificationState = getDialerSessionModifyStateTelecomStatus(status);
      if (status != VideoProvider.SESSION_MODIFY_REQUEST_SUCCESS) {
        // This will update the video UI to display the error message.
        call.setSessionModificationState(newSessionModificationState);
      }

      // Wait for 4 seconds and then clean the session modification state. This allows the video UI
      // to stay up so that the user can read the error message.
      //
      // If the other person accepted the upgrade request then this will keep the video UI up until
      // the call's video state change. Without this we would switch to the voice call and then
      // switch back to video UI.
      handler.postDelayed(this, CLEAR_FAILED_REQUEST_TIMEOUT_MILLIS);
    } else if (call.getSessionModificationState()
        == DialerCall.SESSION_MODIFICATION_STATE_RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
      call.setSessionModificationState(DialerCall.SESSION_MODIFICATION_STATE_NO_REQUEST);
    } else if (call.getSessionModificationState()
        == DialerCall.SESSION_MODIFICATION_STATE_WAITING_FOR_RESPONSE) {
      call.setSessionModificationState(getDialerSessionModifyStateTelecomStatus(status));
    } else {
      LogUtil.i(
          "InCallVideoCallCallback.onSessionModifyResponseReceived",
          "call is not waiting for " + "response, doing nothing");
    }
  }

  @SessionModificationState
  private int getDialerSessionModifyStateTelecomStatus(int telecomStatus) {
    switch (telecomStatus) {
      case VideoProvider.SESSION_MODIFY_REQUEST_SUCCESS:
        return DialerCall.SESSION_MODIFICATION_STATE_NO_REQUEST;
      case VideoProvider.SESSION_MODIFY_REQUEST_FAIL:
      case VideoProvider.SESSION_MODIFY_REQUEST_INVALID:
        // Check if it's already video call, which means the request is not video upgrade request.
        if (VideoUtils.isVideoCall(call.getVideoState())) {
          return DialerCall.SESSION_MODIFICATION_STATE_REQUEST_FAILED;
        } else {
          return DialerCall.SESSION_MODIFICATION_STATE_UPGRADE_TO_VIDEO_REQUEST_FAILED;
        }
      case VideoProvider.SESSION_MODIFY_REQUEST_TIMED_OUT:
        return DialerCall.SESSION_MODIFICATION_STATE_UPGRADE_TO_VIDEO_REQUEST_TIMED_OUT;
      case VideoProvider.SESSION_MODIFY_REQUEST_REJECTED_BY_REMOTE:
        return DialerCall.SESSION_MODIFICATION_STATE_REQUEST_REJECTED;
      default:
        LogUtil.e(
            "InCallVideoCallCallback.getDialerSessionModifyStateTelecomStatus",
            "unknown status: %d",
            telecomStatus);
        return DialerCall.SESSION_MODIFICATION_STATE_REQUEST_FAILED;
    }
  }

  @Override
  public void onCallSessionEvent(int event) {
    InCallVideoCallCallbackNotifier.getInstance().callSessionEvent(event);
  }

  @Override
  public void onPeerDimensionsChanged(int width, int height) {
    InCallVideoCallCallbackNotifier.getInstance().peerDimensionsChanged(call, width, height);
  }

  @Override
  public void onVideoQualityChanged(int videoQuality) {
    InCallVideoCallCallbackNotifier.getInstance().videoQualityChanged(call, videoQuality);
  }

  /**
   * Handles a change to the call data usage. No implementation as the in-call UI does not display
   * data usage.
   *
   * @param dataUsage The updated data usage.
   */
  @Override
  public void onCallDataUsageChanged(long dataUsage) {
    LogUtil.v("InCallVideoCallCallback.onCallDataUsageChanged", "dataUsage = " + dataUsage);
    InCallVideoCallCallbackNotifier.getInstance().callDataUsageChanged(dataUsage);
  }

  /**
   * Handles changes to the camera capabilities. No implementation as the in-call UI does not make
   * use of camera capabilities.
   *
   * @param cameraCapabilities The changed camera capabilities.
   */
  @Override
  public void onCameraCapabilitiesChanged(CameraCapabilities cameraCapabilities) {
    if (cameraCapabilities != null) {
      InCallVideoCallCallbackNotifier.getInstance()
          .cameraDimensionsChanged(
              call, cameraCapabilities.getWidth(), cameraCapabilities.getHeight());
    }
  }

  /**
   * Called 4 seconds after the remote user responds to the video upgrade request. We use this to
   * clear the session modify state.
   */
  @Override
  public void run() {
    if (call.getSessionModificationState() == newSessionModificationState) {
      LogUtil.i("InCallVideoCallCallback.onSessionModifyResponseReceived", "clearing state");
      call.setSessionModificationState(DialerCall.SESSION_MODIFICATION_STATE_NO_REQUEST);
    } else {
      LogUtil.i(
          "InCallVideoCallCallback.onSessionModifyResponseReceived",
          "session modification state has changed, not clearing state");
    }
  }
}
