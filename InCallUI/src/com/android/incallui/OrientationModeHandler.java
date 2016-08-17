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

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.InCallUiListener;
import org.codeaurora.ims.QtiCallConstants;

/**
 * This class listens to incoming events from the {@class InCallDetailsListener}.
 * When call details change, this class is notified and we parse the extras from the details to
 * figure out if orientation mode has changed and if changed, we call setRequestedOrientation
 * on the activity to set the orientation mode for the device.
 *
 */
public class OrientationModeHandler implements InCallDetailsListener, InCallUiListener {

    private static OrientationModeHandler sOrientationModeHandler;

    private PrimaryCallTracker mPrimaryCallTracker;

    private int mOrientationMode = QtiCallConstants.ORIENTATION_MODE_UNSPECIFIED;

    /**
     * Returns a singleton instance of {@class OrientationModeHandler}
     */
    public static synchronized OrientationModeHandler getInstance() {
        if (sOrientationModeHandler == null) {
            sOrientationModeHandler = new OrientationModeHandler();
        }
        return sOrientationModeHandler;
    }

    /**
     * Private constructor. Must use getInstance() to get this singleton.
     */
    private OrientationModeHandler() {
    }

    /**
     * Handles set up of the {@class OrientationModeHandler}. Registers primary call tracker to
     * listen to call state changes and registers this class to listen to call details changes.
     */
    public void setUp() {
        mPrimaryCallTracker = new PrimaryCallTracker();
        InCallPresenter.getInstance().addListener(mPrimaryCallTracker);
        InCallPresenter.getInstance().addDetailsListener(this);
        InCallPresenter.getInstance().addInCallUiListener(this);
    }

    /**
     * Handles tear down of the {@class OrientationModeHandler}. Unregisters primary call tracker
     * from listening to call state changes and unregisters this class from listening to call
     * details changes.
     */
    public void tearDown() {
        InCallPresenter.getInstance().removeListener(mPrimaryCallTracker);
        InCallPresenter.getInstance().removeDetailsListener(this);
        InCallPresenter.getInstance().removeInCallUiListener(this);
        mOrientationMode = QtiCallConstants.ORIENTATION_MODE_UNSPECIFIED;
        mPrimaryCallTracker = null;
    }

    /**
     * Overrides onDetailsChanged method of {@class InCallDetailsListener}. We are
     * notified when call details change and extract the orientation mode from the
     * extras, detect if the mode has changed and set the orientation mode for the device.
     */
    @Override
    public void onDetailsChanged(Call call, android.telecom.Call.Details details) {
        Log.d(this, "onDetailsChanged: - call: " + call + "details: " + details);
        mayBeUpdateOrientationMode(call, details);
    }

    /**
     * This API conveys if incall experience is showing or not.
     *
     * @param showing TRUE if incall experience is showing else FALSE
     */
    @Override
    public void onUiShowing(boolean showing) {
        Call call = mPrimaryCallTracker.getPrimaryCall();
        Log.d(this, "onUiShowing showing: " + showing + " call = " + call);

        if (!showing || call == null) {
            return;
        }

        mayBeUpdateOrientationMode(call, call.getTelecomCall().getDetails());
    }

    private void mayBeUpdateOrientationMode(Call call, android.telecom.Call.Details details) {
        final Bundle extras =  (call != null && details != null) ? details.getExtras() : null;
        final int orientationMode = (extras != null) ? extras.getInt(
                QtiCallConstants.ORIENTATION_MODE_EXTRA_KEY,
                QtiCallConstants.ORIENTATION_MODE_UNSPECIFIED) :
                QtiCallConstants.ORIENTATION_MODE_UNSPECIFIED;

        Log.d(this, "mayBeUpdateOrientationMode : orientationMode: " + orientationMode +
                " mOrientationMode : " + mOrientationMode);
        if (InCallPresenter.getInstance().getActivity() == null) {
            Log.w(this, "mayBeUpdateOrientationMode : InCallActivity is null");
            return;
        }

        if (orientationMode != mOrientationMode && orientationMode !=
                QtiCallConstants.ORIENTATION_MODE_UNSPECIFIED) {
            mOrientationMode = orientationMode;
            onOrientationModeChanged(call, mOrientationMode);
        }
    }

    /**
     * Handles any orientation mode changes in the call.
     *
     * @param call The call for which orientation mode changed.
     * @param orientationMode The new orientation mode of the device
     */
    private void onOrientationModeChanged(Call call, int orientationMode) {
        Log.d(this, "onOrientationModeChanged: Call : " + call + " orientation mode = " +
                orientationMode);

        if (!mPrimaryCallTracker.isPrimaryCall(call)) {
            Log.e(this, "Can't set requested orientation on a non-primary call");
            return;
        }

        InCallPresenter.getInstance().setInCallAllowsOrientationChange(
                QtiCallUtils.toUiOrientationMode(orientationMode));
    }

    /**
     * Returns the current orientation mode based on the receipt of DISPLAY_MODE_EVT from lower
     * layers and whether the call is a video call or not. If we have a video call and we
     * did receive a valid orientation mode, return the corresponding
     * {@link ActivityInfo#ScreenOrientation} else return
     * ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR. If we are in a voice call, return
     * ActivityInfo.SCREEN_ORIENTATION_NOSENSOR.
     *
     * @param call The current call.
     */
    public int getOrientation(Call call) {
        if (VideoUtils.isVideoCall(call)) {
            return (mOrientationMode == QtiCallConstants.ORIENTATION_MODE_UNSPECIFIED) ?
                    ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR :
                    QtiCallUtils.toUiOrientationMode(mOrientationMode);
        } else {
            return ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
        }
    }

    /**
     * Returns the current orientation mode.
     * @see #getOrientation(Call)
     */
    public int getCurrentOrientation() {
        return QtiCallUtils.toUiOrientationMode(mOrientationMode);
    }
}
