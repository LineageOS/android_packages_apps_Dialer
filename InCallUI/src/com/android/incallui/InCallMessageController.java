/* Copyright (c) 2015, 2016, The Linux Foundation. All rights reserved.
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

import android.content.Context;
import android.content.res.Resources;

import org.codeaurora.ims.QtiCallConstants;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallVideoCallCallbackNotifier.VideoEventListener;


/**
 * This class listens to incoming events for the listener classes it implements. It should
 * handle all UI notification to be shown to the user for any indication that is required to be
 * shown like call substate indication, video quality indication, etc.
 * For e.g., this class implements {@class InCallSubstateListener} and when call substate changes,
 * {@class CallSubstateNotifier} notifies it through the onCallSubstateChanged callback.
 */
public class InCallMessageController implements InCallSubstateListener, VideoEventListener {

    private static InCallMessageController sInCallMessageController;
    private Context mContext;

    /**
     * Private constructor. Must use getInstance() to get this singleton.
     */
    private InCallMessageController() {
    }

    /**
     * Handles set up of the {@class InCallSubstateListener}. Instantiates the context needed by
     * the class and adds a listener to listen to call substate changes.
     */
    public void setUp(Context context) {
        mContext = context;
        CallSubstateNotifier.getInstance().addListener(this);
        InCallVideoCallCallbackNotifier.getInstance().addVideoEventListener(this);
    }

    /**
     * Handles tear down of the {@class InCallSubstateListener}. Sets the context to null and
     * unregisters it's call substate listener.
     */
    public void tearDown() {
        mContext = null;
        CallSubstateNotifier.getInstance().removeListener(this);
        InCallVideoCallCallbackNotifier.getInstance().removeVideoEventListener(this);
    }

    /**
     * This method returns a singleton instance of {@class InCallMessageController}
     */
    public static synchronized InCallMessageController getInstance() {
        if (sInCallMessageController == null) {
            sInCallMessageController = new InCallMessageController();
        }
        return sInCallMessageController;
    }

    /**
     * This method overrides onCallSubstateChanged method of {@interface InCallSubstateListener}
     * We are notified when call substate changes and display a toast message on the UI.
     */
    @Override
    public void onCallSubstateChanged(final Call call, final int callSubstate) {
        Log.d(this, "onCallSubstateChanged - Call : " + call + " call substate changed to " +
                callSubstate);

        if (mContext == null) {
            Log.e(this, "onCallSubstateChanged - Context is null. Return");
            return;
        }

        String callSubstateChangedText = "";

        if (QtiCallUtils.isEnabled(
                QtiCallConstants.CALL_SUBSTATE_AUDIO_CONNECTED_SUSPENDED, callSubstate)) {
            callSubstateChangedText +=
                    mContext.getResources().getString(
                    R.string.call_substate_connected_suspended_audio);
        }

        if (QtiCallUtils.isEnabled(
                QtiCallConstants.CALL_SUBSTATE_VIDEO_CONNECTED_SUSPENDED, callSubstate)) {
            callSubstateChangedText +=
                    mContext.getResources().getString(
                    R.string.call_substate_connected_suspended_video);
        }

        if (QtiCallUtils.isEnabled(QtiCallConstants.CALL_SUBSTATE_AVP_RETRY, callSubstate)) {
            callSubstateChangedText +=
                    mContext.getResources().getString(R.string.call_substate_avp_retry);
        }

        if (QtiCallUtils.isNotEnabled(QtiCallConstants.CALL_SUBSTATE_ALL, callSubstate)) {
            callSubstateChangedText =
                    mContext.getResources().getString(R.string.call_substate_call_resumed);
        }

        if (!callSubstateChangedText.isEmpty()) {
            String callSubstateLabelText = mContext.getResources().getString(
                    R.string.call_substate_label);
            QtiCallUtils.displayToast(mContext, callSubstateLabelText + callSubstateChangedText);
        }
    }

    /**
     * This method overrides onVideoQualityChanged method of {@interface VideoEventListener}
     * We are notified when video quality of the call changed and display a message on the UI.
     */
    @Override
    public void onVideoQualityChanged(final Call call, final int videoQuality) {
        Log.d(this, "Call : " + call + " onVideoQualityChanged. Video quality changed to " +
                videoQuality);

        if (mContext == null) {
            Log.e(this, "onVideoQualityChanged - Context is null. Return");
            return;
        }
        final Resources resources = mContext.getResources();
        if (resources.getBoolean(R.bool.config_display_video_quality_toast)) {
            final String videoQualityChangedText = resources.getString(
                    R.string.video_quality_changed) + resources.getString(
                    QtiCallUtils.getVideoQualityResourceId(videoQuality));
            QtiCallUtils.displayToast(mContext, videoQualityChangedText);
        }
    }

    /**
     * This method overrides onCallSessionEvent method of {@interface VideoEventListener}
     * We are notified when a new call session event is sent and display a message on the UI.
     */
    @Override
    public void onCallSessionEvent(final int event) {
        Log.d(this, "onCallSessionEvent: event = " + event);

        if (mContext == null) {
            Log.e(this, "onCallSessionEvent - Context is null. Return");
            return;
        }
        final Resources resources = mContext.getResources();
        if (resources.getBoolean(R.bool.config_call_session_event_toast)) {
            QtiCallUtils.displayToast(mContext, QtiCallUtils.getCallSessionResourceId(event));
        }
    }

    /**
     * This method overrides onCallDataUsageChange method of {@interface VideoEventListener}
     *  We are notified when data usage is changed and display a message on the UI.
     */
    @Override
    public void onCallDataUsageChange(final long dataUsage) {
        Log.d(this, "onCallDataUsageChange: dataUsage = " + dataUsage);
        final Resources resources = mContext.getResources();
        if (resources.getBoolean(R.bool.config_display_data_usage_toast)) {
            final String dataUsageChangedText = mContext.getResources().getString(
                    R.string.data_usage_label) + dataUsage;
            QtiCallUtils.displayToast(mContext, dataUsageChangedText);
        }
    }

    /**
     * This method overrides onPeerPauseStateChanged method of {@interface VideoEventListener}
     * Added for completeness. No implementation yet.
     */
    @Override
    public void onPeerPauseStateChanged(final Call call, final boolean paused) {
        //no-op
    }
}
