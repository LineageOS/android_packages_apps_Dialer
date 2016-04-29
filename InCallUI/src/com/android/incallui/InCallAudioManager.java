/* Copyright (c) 2015, The Linux Foundation. All rights reserved.
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

import android.telecom.CallAudioState;
import android.telecom.VideoProfile;

/**
 * This class implements QTI audio routing policy for upgrade/downgrade/merge call use cases
 * based on the audio policy below.
 *
 * Audio policy for any user initiated action like making a video call, accepting an upgrade
 * request or sending an upgrade/downgrade request is as follows : If any of the above user actions
 * indicate a transition to a video call, we route audio to SPEAKER. If any of the above user
 * actions indicate a voice call, we route to EARPIECE. For all other non-user initiated actions
 * like an incoming downgrade request, we don't change the audio path and respect user's choice.
 * For merge calls, we route to SPEAKER only if one of the calls being merged is a video call.
 *
 * Audio path routing for outgoing calls, incoming calls, add call and call waiting are handled
 * in Telecom layer. Same audio policy is followed for those as well. Any user initiated action
 * indicating a origin/acceptance of video call routes audio to SPEAKER or to EARPIECE for voice
 * calls.
 */
public class InCallAudioManager {

    private static InCallAudioManager sInCallAudioManager;

    private final static String LOG_TAG = "InCallAudioManager";

    /**
     * Private constructor. Must use getInstance() to get this singleton.
     */
    private InCallAudioManager() {
    }

    /**
     * This method returns a singleton instance of {@class InCallAudioManager}
     */
    public static synchronized InCallAudioManager getInstance() {
        if (sInCallAudioManager == null) {
            sInCallAudioManager = new InCallAudioManager();
        }
        return sInCallAudioManager;
    }

    /**
     * Called when user sends an upgrade/downgrade request. Route audio to speaker if the user
     * sends an upgrade request to Video (bidirectional, transmit or receive) otherwise route
     * audio to earpiece if it's a downgrade request.
     */
    public void onModifyCallClicked(final Call call,final int newVideoState) {
        Log.v(this, "onModifyCallClicked: Call = " + call + "new video state = " +
                newVideoState);

        if (!VideoProfile.isVideo(newVideoState)) {
            enableEarpiece();
        } else if (canEnableSpeaker(call.getVideoState(),newVideoState)) {
            enableSpeaker();
        }
    }

    /**
     * Called when user accepts an upgrade request. Route audio to speaker if the user accepts an
     * upgrade request to Video (bidirectional, transmit or receive)
     */
    public void onAcceptUpgradeRequest(final Call call, final int videoState) {
        Log.v(this, "onAcceptUpgradeRequest: Call = " + call + "video state = " +
                videoState);
        if (canEnableSpeaker(call.getVideoState(), videoState)) {
            enableSpeaker();
        }
    }

    /**
     * Called when user accepts an incoming call. Route audio to speaker if the user accepts an
     * incoming call as Video (bidirectional, transmit or receive) or to earpiece is the user
     * accepts the call as Voice
      */
    public void onAnswerIncomingCall(final Call call, final int videoState) {
        Log.v(this, "onAnswerIncomingCall: Call = " + call + "video state = " +
                videoState);
        if (!VideoProfile.isVideo(videoState)) {
            enableEarpiece();
        } else {
            enableSpeaker();
        }
    }

    /**
     * Called when user clicks on merge calls from the UI. Route audio to speaker if one of the
     * calls being merged is a video call.
     */
    public void onMergeClicked() {
        Log.v(this, "onMergeClicked");

        if (VideoUtils.isVideoCall(CallList.getInstance().getBackgroundCall()) ||
                VideoUtils.isVideoCall(CallList.getInstance().getActiveCall())) {
            enableSpeaker();
        }
    }

    /**
     * Determines if the speakerphone should be automatically enabled for the call.  Speakerphone
     * should be enabled if the call is a video call and if the adb property
     * "persist.radio.call.audio.output" is true.
     *
     * @param videoState The video state of the call.
     * @return {@code true} if the speakerphone should be enabled.
     */
    private static boolean canEnableSpeaker(int oldVideoState, int newVideoState) {
        return !VideoProfile.isVideo(oldVideoState) &&
                VideoProfile.isVideo(newVideoState) && isSpeakerEnabledForVideoCalls();
    }

    /**
     * Determines if the speakerphone should be automatically enabled for video calls.
     *
     * @return {@code true} if the speakerphone should automatically be enabled.
     */
    private static boolean isSpeakerEnabledForVideoCalls() {
        // TODO: we can't access adb properties from InCallUI. Need to refactor this.
        return true;
    }

    /**
     * Routes the call to the earpiece if audio is not being already routed to Earpiece and if
     * bluetooth or wired headset is not connected.
     */
    private static void enableEarpiece() {
        final TelecomAdapter telecomAdapter = TelecomAdapter.getInstance();
        if (telecomAdapter == null) {
            Log.e(LOG_TAG, "enableEarpiece: TelecomAdapter is null");
            return;
        }

        final int currentAudioMode = AudioModeProvider.getInstance().getAudioMode();
        Log.v(LOG_TAG, "enableEarpiece: Current audio mode is - " + currentAudioMode);

        /*
         * For MO video calls, we mark that audio needs to be routed to speaker during
         * call setup phase and audio routing then takes place when call state transitions
         * to DIALING. If user decides to modify low battery MO video call to voice call,
         * audio route needs to be changed to earpiece for which we need to unmark speaker
         * audio route selection that was done during video call setup phase. To avoid below
         * API from being a no operation, do not check for CallAudioState.ROUTE_EARPIECE
         */
        if (QtiCallUtils.isNotEnabled(CallAudioState.ROUTE_BLUETOOTH |
                CallAudioState.ROUTE_WIRED_HEADSET,
                currentAudioMode)) {
            Log.v(LOG_TAG, "enableEarpiece: Set audio route to earpiece");
            telecomAdapter.setAudioRoute(CallAudioState.ROUTE_EARPIECE);
        }
    }

    /**
     * Routes the call to the speaker if audio is not being already routed to Speaker and if
     * bluetooth or wired headset is not connected.
     */
    private static void enableSpeaker() {
        final TelecomAdapter telecomAdapter = TelecomAdapter.getInstance();
        if (telecomAdapter == null) {
            Log.e(LOG_TAG, "enableSpeaker: TelecomAdapter is null");
            return;
        }

        final int currentAudioMode = AudioModeProvider.getInstance().getAudioMode();
        Log.v(LOG_TAG, "enableSpeaker: Current audio mode is - " + currentAudioMode);

        if(QtiCallUtils.isNotEnabled(CallAudioState.ROUTE_SPEAKER |
                CallAudioState.ROUTE_BLUETOOTH | CallAudioState.ROUTE_WIRED_HEADSET,
                currentAudioMode)) {
            Log.v(LOG_TAG, "enableSpeaker: Set audio route to speaker");
            telecomAdapter.setAudioRoute(CallAudioState.ROUTE_SPEAKER);
        }
    }
}
