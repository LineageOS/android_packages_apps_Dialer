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
 * limitations under the License.
 */

package com.android.incallui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telecom.VideoProfile;

/**
 * Accepts broadcast Intents which will be prepared by {@link StatusBarNotifier} and thus
 * sent from the notification manager.
 * This should be visible from outside, but shouldn't be exported.
 */
public class NotificationBroadcastReceiver extends BroadcastReceiver {

    /**
     * Intent Action used for hanging up the current call from Notification bar. This will
     * choose first ringing call, first active call, or first background call (typically in
     * STATE_HOLDING state).
     */
    public static final String ACTION_DECLINE_INCOMING_CALL =
            "com.android.incallui.ACTION_DECLINE_INCOMING_CALL";
    public static final String ACTION_HANG_UP_ONGOING_CALL =
            "com.android.incallui.ACTION_HANG_UP_ONGOING_CALL";
    public static final String ACTION_ANSWER_VIDEO_INCOMING_CALL =
            "com.android.incallui.ACTION_ANSWER_VIDEO_INCOMING_CALL";
    public static final String ACTION_ANSWER_VOICE_INCOMING_CALL =
            "com.android.incallui.ACTION_ANSWER_VOICE_INCOMING_CALL";
    public static final String ACTION_ACCEPT_VIDEO_UPGRADE_REQUEST =
            "com.android.incallui.ACTION_ACCEPT_VIDEO_UPGRADE_REQUEST";
    public static final String ACTION_DECLINE_VIDEO_UPGRADE_REQUEST =
            "com.android.incallui.ACTION_DECLINE_VIDEO_UPGRADE_REQUEST";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        Log.i(this, "Broadcast from Notification: " + action);

        // TODO: Commands of this nature should exist in the CallList.
        if (action.equals(ACTION_ANSWER_VIDEO_INCOMING_CALL)) {
            InCallPresenter.getInstance().answerIncomingCall(
                    context, VideoProfile.STATE_BIDIRECTIONAL);
        } else if (action.equals(ACTION_ANSWER_VOICE_INCOMING_CALL)) {
            InCallPresenter.getInstance().answerIncomingCall(
                    context, VideoProfile.STATE_AUDIO_ONLY);
        } else if (action.equals(ACTION_DECLINE_INCOMING_CALL)) {
            InCallPresenter.getInstance().declineIncomingCall(context);
        } else if (action.equals(ACTION_HANG_UP_ONGOING_CALL)) {
            InCallPresenter.getInstance().hangUpOngoingCall(context);
        } else if (action.equals(ACTION_ACCEPT_VIDEO_UPGRADE_REQUEST)) {
            //TODO: Change calltype after adding support for TX and RX
            InCallPresenter.getInstance().acceptUpgradeRequest(
                    VideoProfile.STATE_BIDIRECTIONAL, context);
        } else if (action.equals(ACTION_DECLINE_VIDEO_UPGRADE_REQUEST)) {
            InCallPresenter.getInstance().declineUpgradeRequest(context);
        }
    }

}
