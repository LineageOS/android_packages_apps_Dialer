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

package com.android.incallui;

import android.telecom.Connection;
import android.telecom.Connection.VideoProvider;
import android.telecom.InCallService.VideoCall;
import android.telecom.VideoProfile;
import android.telecom.VideoProfile.CameraCapabilities;

/**
 * Implements the InCallUI VideoCall Callback.
 */
public class InCallVideoCallCallback extends VideoCall.Callback {

    /**
     * The call associated with this {@link InCallVideoCallCallback}.
     */
    private Call mCall;

    /**
     * Creates an instance of the call video client, specifying the call it is related to.
     *
     * @param call The call.
     */
    public InCallVideoCallCallback(Call call) {
        mCall = call;
    }

    /**
     * Handles an incoming session modification request.
     *
     * @param videoProfile The requested video call profile.
     */
    @Override
    public void onSessionModifyRequestReceived(VideoProfile videoProfile) {
        Log.d(this, " onSessionModifyRequestReceived videoProfile=" + videoProfile);
        int previousVideoState = VideoUtils.getUnPausedVideoState(mCall.getVideoState());
        int newVideoState = VideoUtils.getUnPausedVideoState(videoProfile.getVideoState());

        boolean wasVideoCall = VideoUtils.isVideoCall(previousVideoState);
        boolean isVideoCall = VideoUtils.isVideoCall(newVideoState);

        if (wasVideoCall && !isVideoCall) {
            Log.v(this, " onSessionModifyRequestReceived Call downgraded to " + newVideoState);
        } else if (previousVideoState != newVideoState) {
            InCallVideoCallCallbackNotifier.getInstance().upgradeToVideoRequest(mCall,
                 newVideoState);
        }
    }

    /**
     * Handles a session modification response.
     *
     * @param status Status of the session modify request. Valid values are
     *            {@link Connection.VideoProvider#SESSION_MODIFY_REQUEST_SUCCESS},
     *            {@link Connection.VideoProvider#SESSION_MODIFY_REQUEST_FAIL},
     *            {@link Connection.VideoProvider#SESSION_MODIFY_REQUEST_INVALID}
     * @param requestedProfile
     * @param responseProfile The actual profile changes made by the peer device.
     */
    @Override
    public void onSessionModifyResponseReceived(int status, VideoProfile requestedProfile,
            VideoProfile responseProfile) {
        Log.d(this, "onSessionModifyResponseReceived status=" + status + " requestedProfile="
                + requestedProfile + " responseProfile=" + responseProfile);
        if (status != VideoProvider.SESSION_MODIFY_REQUEST_SUCCESS) {
            // Report the reason the upgrade failed as the new session modification state.
            if (status == VideoProvider.SESSION_MODIFY_REQUEST_TIMED_OUT) {
                mCall.setSessionModificationState(
                        Call.SessionModificationState.UPGRADE_TO_VIDEO_REQUEST_TIMED_OUT);
            } else {
                if (status == VideoProvider.SESSION_MODIFY_REQUEST_REJECTED_BY_REMOTE) {
                    mCall.setSessionModificationState(
                            Call.SessionModificationState.REQUEST_REJECTED);
                } else {
                    mCall.setSessionModificationState(
                            Call.SessionModificationState.REQUEST_FAILED);
                }
            }
            InCallVideoCallCallbackNotifier.getInstance().upgradeToVideoFail(status, mCall);
        }

        // Finally clear the outstanding request.
        mCall.setSessionModificationState(Call.SessionModificationState.NO_REQUEST);
    }

    /**
     * Handles a call session event.
     *
     * @param event The event.
     */
    @Override
    public void onCallSessionEvent(int event) {
        InCallVideoCallCallbackNotifier.getInstance().callSessionEvent(event);
    }

    /**
     * Handles a change to the peer video dimensions.
     *
     * @param width  The updated peer video width.
     * @param height The updated peer video height.
     */
    @Override
    public void onPeerDimensionsChanged(int width, int height) {
        InCallVideoCallCallbackNotifier.getInstance().peerDimensionsChanged(mCall, width, height);
    }

    /**
     * Handles a change to the video quality of the call.
     *
     * @param videoQuality The updated video call quality.
     */
    @Override
    public void onVideoQualityChanged(int videoQuality) {
        InCallVideoCallCallbackNotifier.getInstance().videoQualityChanged(mCall, videoQuality);
    }

    /**
     * Handles a change to the call data usage.  No implementation as the in-call UI does not
     * display data usage.
     *
     * @param dataUsage The updated data usage.
     */
    @Override
    public void onCallDataUsageChanged(long dataUsage) {
        Log.d(this, "onCallDataUsageChanged: dataUsage = " + dataUsage);
        InCallVideoCallCallbackNotifier.getInstance().callDataUsageChanged(dataUsage);
    }

    /**
     * Handles changes to the camera capabilities.  No implementation as the in-call UI does not
     * make use of camera capabilities.
     *
     * @param cameraCapabilities The changed camera capabilities.
     */
    @Override
    public void onCameraCapabilitiesChanged(CameraCapabilities cameraCapabilities) {
        if (cameraCapabilities != null) {
            InCallVideoCallCallbackNotifier.getInstance().cameraDimensionsChanged(
                    mCall, cameraCapabilities.getWidth(), cameraCapabilities.getHeight());
        }
    }
}
