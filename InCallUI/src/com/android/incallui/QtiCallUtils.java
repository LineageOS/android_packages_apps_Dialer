/**
 * Copyright (c) 2015, 2016 The Linux Foundation. All rights reserved.
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
import android.telecom.Connection.VideoProvider;
import android.widget.Toast;
import android.content.Context;
import android.content.res.Resources;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.telecom.InCallService.VideoCall;

import java.util.ArrayList;

/**
 * This class contains Qti specific utiltity functions.
 */
public class QtiCallUtils {

    private static final int INVALID_INDEX = -1;

    private static String LOG_TAG = "QtiCallUtils";

    /**
     * Private constructor for QtiCallUtils as we don't want to instantiate this class
     */
    private QtiCallUtils() {
    }

    /**
     * This utility method checks to see if bits in the mask are enabled in the value
     * Returns true if all bits in {@code mask} are set in {@code value}
     */
    public static boolean isEnabled(final int mask, final int value) {
        return (mask & value) == mask;
    }

    /**
     * This utility method checks to see if none of the bits in the mask are enabled in the value
     * Returns true if none of the bits in {@code mask} are set in {@code value}
     */
    public static boolean isNotEnabled(final int mask, final int value) {
        return (mask & value) == 0;
    }

    /**
     * Method to get the video quality display string resource id given the video quality
     */
    public static int getVideoQualityResourceId(int videoQuality) {
        switch (videoQuality) {
            case VideoProfile.QUALITY_HIGH:
                return R.string.video_quality_high;
            case VideoProfile.QUALITY_MEDIUM:
                return R.string.video_quality_medium;
            case VideoProfile.QUALITY_LOW:
                return R.string.video_quality_low;
            default:
                return R.string.video_quality_unknown;
        }
    }

    /**
     * Returns the call session resource id given the call session event
     */
    public static int getCallSessionResourceId(int event) {
        switch (event) {
            case VideoProvider.SESSION_EVENT_RX_PAUSE:
                return R.string.player_stopped;
            case VideoProvider.SESSION_EVENT_RX_RESUME:
                return R.string.player_started;
            case VideoProvider.SESSION_EVENT_CAMERA_FAILURE:
                return R.string.camera_not_ready;
            case VideoProvider.SESSION_EVENT_CAMERA_READY:
                return R.string.camera_ready;
            default:
                return R.string.unknown_call_session_event;
        }
    }

    /**
     * Displays the message as a Toast on the UI
     */
    public static void displayToast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    /**
     * Displays the string corresponding to the resourceId as a Toast on the UI
     */
    public static void displayToast(Context context, int resourceId) {
        displayToast(context, context.getResources().getString(resourceId));
    }

    /**
     * The function is called when Modify Call button gets pressed. The function creates and
     * displays modify call options.
     */
    public static void displayModifyCallOptions(final Call call, final Context context) {
        if (call == null) {
            Log.d(LOG_TAG, "Can't display modify call options. Call is null");
            return;
        }

        final ArrayList<CharSequence> items = new ArrayList<CharSequence>();
        final ArrayList<Integer> itemToCallType = new ArrayList<Integer>();
        final Resources res = context.getResources();
        // Prepare the string array and mapping.
        items.add(res.getText(R.string.modify_call_option_voice));
        itemToCallType.add(VideoProfile.STATE_AUDIO_ONLY);

        items.add(res.getText(R.string.modify_call_option_vt_rx));
        itemToCallType.add(VideoProfile.STATE_RX_ENABLED);

        items.add(res.getText(R.string.modify_call_option_vt_tx));
        itemToCallType.add(VideoProfile.STATE_TX_ENABLED);

        items.add(res.getText(R.string.modify_call_option_vt));
        itemToCallType.add(VideoProfile.STATE_BIDIRECTIONAL);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.modify_call_option_title);
        final AlertDialog alert;

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                Toast.makeText(context, items.get(item), Toast.LENGTH_SHORT).show();
                final int selCallType = itemToCallType.get(item);
                Log.v(this, "Videocall: ModifyCall: upgrade/downgrade to "
                        + callTypeToString(selCallType));
                VideoProfile videoProfile = new VideoProfile(selCallType);
                changeToVideoClicked(call, videoProfile);
                dialog.dismiss();
            }
        };
        final int currUnpausedVideoState = VideoUtils.getUnPausedVideoState(call.getVideoState());
        final int index = itemToCallType.indexOf(currUnpausedVideoState);
        if (index == INVALID_INDEX) {
            return;
        }
        builder.setSingleChoiceItems(items.toArray(new CharSequence[0]), index, listener);
        alert = builder.create();
        alert.show();
    }

    /**
     * Converts the call type to string
     */
    public static String callTypeToString(int callType) {
        switch (callType) {
            case VideoProfile.STATE_BIDIRECTIONAL:
                return "VT";
            case VideoProfile.STATE_TX_ENABLED:
                return "VT_TX";
            case VideoProfile.STATE_RX_ENABLED:
                return "VT_RX";
        }
        return "";
    }

    /**
     * Sends a session modify request to the telephony framework
     */
    private static void changeToVideoClicked(Call call, VideoProfile videoProfile) {
        VideoCall videoCall = call.getVideoCall();
        if (videoCall == null) {
            return;
        }
        videoCall.sendSessionModifyRequest(videoProfile);
        call.setSessionModificationState(Call.SessionModificationState.WAITING_FOR_RESPONSE);
    }

    /**
     * Checks the boolean flag in config file to figure out if we are going to use Qti extension or
     * not
     */
    public static boolean useExt(Context context) {
        if (context == null) {
            Log.w(context, "Context is null...");
        }
        return context != null && context.getResources().getBoolean(R.bool.video_call_use_ext);
    }

    /**
     * Returns user options for accepting an incoming video call based on Qti extension flag
     */
    public static int getIncomingCallAnswerOptions(Context context, boolean withSms) {
        if (!useExt(context)) {
            return withSms ? AnswerFragment.TARGET_SET_FOR_VIDEO_WITH_SMS :
                    AnswerFragment.TARGET_SET_FOR_VIDEO_WITHOUT_SMS;
        } else {
            return withSms ? AnswerFragment.TARGET_SET_FOR_QTI_VIDEO_WITH_SMS :
                    AnswerFragment.TARGET_SET_FOR_QTI_VIDEO_WITHOUT_SMS;
        }
    }

    /**
     * Returns the session modification user options based on session modify request video states
     * (current video state and modify request video state)
     */
    public static int getSessionModificationOptions(Context context, int currentVideoState,
            int modifyToVideoState) {
        if (!useExt(context)) {
            return AnswerFragment.TARGET_SET_FOR_VIDEO_ACCEPT_REJECT_REQUEST;
        }

        if (showVideoUpgradeOptions(currentVideoState, modifyToVideoState)) {
            return AnswerFragment.TARGET_SET_FOR_QTI_VIDEO_ACCEPT_REJECT_REQUEST;
        } else if (isEnabled(VideoProfile.STATE_BIDIRECTIONAL, modifyToVideoState)) {
            return AnswerFragment.TARGET_SET_FOR_QTI_BIDIRECTIONAL_VIDEO_ACCEPT_REJECT_REQUEST;
        } else if (isEnabled(VideoProfile.STATE_TX_ENABLED, modifyToVideoState)) {
            return AnswerFragment.TARGET_SET_FOR_QTI_VIDEO_TRANSMIT_ACCEPT_REJECT_REQUEST;
        } else if (isEnabled(VideoProfile.STATE_RX_ENABLED, modifyToVideoState)) {
            return AnswerFragment.TARGET_SET_FOR_QTI_VIDEO_RECEIVE_ACCEPT_REJECT_REQUEST;
        }
        return AnswerFragment.TARGET_SET_FOR_QTI_VIDEO_ACCEPT_REJECT_REQUEST;
    }

    /**
     * Returns true if we are upgrading from Voice to Bidirectional video, false otherwise
     */
    private static boolean showVideoUpgradeOptions(int currentVideoState, int modifyToVideoState) {
        return currentVideoState == VideoProfile.STATE_AUDIO_ONLY &&
                isEnabled(VideoProfile.STATE_BIDIRECTIONAL, modifyToVideoState);
    }
}
