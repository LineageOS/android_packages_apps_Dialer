/* Copyright (c) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.incallui;

import android.telecom.VideoProfile;

import com.google.common.base.Preconditions;

public class CallUtils {

    public static boolean isVideoCall(Call call) {
        return call != null && isVideoCall(call.getVideoState());
    }

    public static boolean isVideoCall(int videoState) {
        return VideoProfile.isTransmissionEnabled(videoState)
                || VideoProfile.isReceptionEnabled(videoState);
    }

    public static boolean isIncomingVideoCall(Call call) {
        if (!CallUtils.isVideoCall(call)) {
            return false;
        }
        final int state = call.getState();
        return (state == Call.State.INCOMING) || (state == Call.State.CALL_WAITING);
    }

    public static boolean isActiveVideoCall(Call call) {
        return CallUtils.isVideoCall(call) && call.getState() == Call.State.ACTIVE;
    }

    public static boolean isOutgoingVideoCall(Call call) {
        if (!CallUtils.isVideoCall(call)) {
            return false;
        }
        final int state = call.getState();
        return Call.State.isDialing(state) || state == Call.State.CONNECTING
                || state == Call.State.SELECT_PHONE_ACCOUNT;
    }

    public static boolean isAudioCall(Call call) {
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
