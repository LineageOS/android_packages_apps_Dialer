/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.telecom.VideoProfile;

import com.android.contacts.common.compat.CompatUtils;

import com.google.common.base.Preconditions;

public class VideoUtils {

    public static boolean isVideoCall(Call call) {
        return call != null && isVideoCall(call.getVideoState());
    }

    public static boolean isVideoCall(int videoState) {
        if (!CompatUtils.isVideoCompatible()) {
            return false;
        }

        return VideoProfile.isTransmissionEnabled(videoState)
                || VideoProfile.isReceptionEnabled(videoState);
    }

    public static boolean isBidirectionalVideoCall(Call call) {
        if (!CompatUtils.isVideoCompatible()) {
            return false;
        }

        return VideoProfile.isBidirectional(call.getVideoState());
    }

    public static boolean isIncomingVideoCall(Call call) {
        if (!VideoUtils.isVideoCall(call)) {
            return false;
        }
        final int state = call.getState();
        return (state == Call.State.INCOMING) || (state == Call.State.CALL_WAITING);
    }

    public static boolean isActiveVideoCall(Call call) {
        return VideoUtils.isVideoCall(call) && call.getState() == Call.State.ACTIVE;
    }

    public static boolean isOutgoingVideoCall(Call call) {
        if (!VideoUtils.isVideoCall(call)) {
            return false;
        }
        final int state = call.getState();
        return Call.State.isDialing(state) || state == Call.State.CONNECTING
                || state == Call.State.SELECT_PHONE_ACCOUNT;
    }

    public static boolean isAudioCall(Call call) {
        if (!CompatUtils.isVideoCompatible()) {
            return true;
        }

        return call != null && VideoProfile.isAudioOnly(call.getVideoState());
    }

    // TODO (ims-vt) Check if special handling is needed for CONF calls.
    public static boolean canVideoPause(Call call) {
        return isVideoCall(call) && call.getState() == Call.State.ACTIVE;
    }

    public static VideoProfile makeVideoPauseProfile(Call call) {
        Preconditions.checkNotNull(call);
        Preconditions.checkState(!VideoProfile.isAudioOnly(call.getVideoState()));
        return new VideoProfile(getPausedVideoState(call.getVideoState()));
    }

    public static VideoProfile makeVideoUnPauseProfile(Call call) {
        Preconditions.checkNotNull(call);
        return new VideoProfile(getUnPausedVideoState(call.getVideoState()));
    }

    public static int getUnPausedVideoState(int videoState) {
        return videoState & (~VideoProfile.STATE_PAUSED);
    }

    public static int getPausedVideoState(int videoState) {
        return videoState | VideoProfile.STATE_PAUSED;
    }

}
