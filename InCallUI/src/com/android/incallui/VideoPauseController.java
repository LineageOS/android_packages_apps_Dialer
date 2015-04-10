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

import android.os.SystemProperties;
import android.telecom.VideoProfile;
import com.android.incallui.Call.State;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.InCallVideoCallListenerNotifier.SessionModificationListener;
import com.android.internal.util.Preconditions;

/**
 * The class is responsible for generating video pause/resume request.
 */
class VideoPauseController implements InCallStateListener, IncomingCallListener,
        SessionModificationListener {
    private static final String TAG = "VideoCallPauseController:";

    private class CallContext {
        public CallContext(Call call) {
            Preconditions.checkNotNull(call);
            update(call);
        }

        public void update(Call call) {
            mCall = Preconditions.checkNotNull(call);
            mState = call.getState();
            mId = call.getId();
            mVideoState = call.getVideoState();
        }

        public int getState() {
            return mState;
        }

        public String getId() {
            return mId;
        }

        public int getVideoState() {
            return mVideoState;
        }

        public String toString() {
            return String.format("CallContext {CallId=%s, State=%s, VideoState=",
                    mId, mState, mVideoState);
        }

        public Call getCall() {
            return mCall;
        }

        private int mState = State.INVALID;
        private String mId;
        private int mVideoState;
        private Call mCall;
    }

    private InCallPresenter mInCallPresenter;
    private static VideoPauseController sVideoPauseController;

    private CallContext mPrimaryCallContext = null; // Context of primary call, if any.
    private boolean mIsInBackground = false; // True if UI is not visible, false otherwise.
    private int mVideoPauseMode = VIDEO_PAUSE_MODE_DISABLED;

    /**
     * Stores current video pause mode.
     * 0 - Video Pause is disabled.
     * 1 - Video Pause is enabled.
     */
    private static final String PROPERTY_VIDEO_PAUSE_MODE = "persist.radio.videopause.mode";
    private static int VIDEO_PAUSE_MODE_DISABLED = 0;
    private static int VIDEO_PAUSE_MODE_ENABLED = 1;

    private VideoPauseController() {
        mVideoPauseMode = SystemProperties.getInt(PROPERTY_VIDEO_PAUSE_MODE,
                VIDEO_PAUSE_MODE_DISABLED);
        if (mVideoPauseMode != VIDEO_PAUSE_MODE_ENABLED) { // Validate the mode before using.
            mVideoPauseMode = VIDEO_PAUSE_MODE_DISABLED;
        }
    }

    /*package*/
    static synchronized VideoPauseController getInstance() {
        if (sVideoPauseController == null) {
            sVideoPauseController = new VideoPauseController();
        }
        return sVideoPauseController;
    }

    public void setUp(InCallPresenter inCallPresenter) {
        if (!isVideoPausedEnabled()) {
            return;
        }

        log("setUp...");
        mInCallPresenter = Preconditions.checkNotNull(inCallPresenter);
        mInCallPresenter.addListener(this);
        mInCallPresenter.addIncomingCallListener(this);
        InCallVideoCallListenerNotifier.getInstance().addSessionModificationListener(this);
    }

    public void tearDown() {
        if (!isVideoPausedEnabled()) {
            return;
        }

        log("tearDown...");
        InCallVideoCallListenerNotifier.getInstance().removeSessionModificationListener(this);
        mInCallPresenter.removeListener(this);
        mInCallPresenter.removeIncomingCallListener(this);
        clear();
    }

    private void clear() {
        mInCallPresenter = null;
        mPrimaryCallContext = null;
        mIsInBackground = false;
    }

    /**
     * The function gets called when call state changes.
     * @param state Phone state.
     * @param callList List of current call.
     */
    @Override
    public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
        log("onStateChange, OldState=" + oldState + " NewState=" + newState);

        Call call = null;
        if (newState == InCallState.INCOMING) {
            call = callList.getIncomingCall();
        } else if (newState == InCallState.WAITING_FOR_ACCOUNT) {
            call = callList.getWaitingForAccountCall();
        } else if (newState == InCallState.PENDING_OUTGOING) {
            call = callList.getPendingOutgoingCall();
        } else if (newState == InCallState.OUTGOING) {
            call = callList.getOutgoingCall();
        } else {
            call = callList.getActiveCall();
        }

        boolean hasPrimaryCallChanged = !areSame(call, mPrimaryCallContext);
        boolean canVideoPause = CallUtils.canVideoPause(call);
        log("onStateChange, hasPrimaryCallChanged=" + hasPrimaryCallChanged);
        log("onStateChange, canVideoPause=" + canVideoPause);
        log("onStateChange, IsInBackground=" + mIsInBackground);

        if (hasPrimaryCallChanged) {
            onPrimaryCallChanged(call);
            return;
        }

        if (isOutgoing(mPrimaryCallContext) && canVideoPause && mIsInBackground) {
            // Bring UI to foreground if outgoing request becomes active while UI is in
            // background.
            bringToForeground();
        } else if (!isVideoCall(mPrimaryCallContext) && canVideoPause && mIsInBackground) {
            // Bring UI to foreground if VoLTE call becomes active while UI is in
            // background.
            bringToForeground();
        }

        updatePrimaryCallContext(call);
    }

    private void onPrimaryCallChanged(Call call) {
        log("onPrimaryCallChanged: New call = " + call);
        log("onPrimaryCallChanged: Old call = " + mPrimaryCallContext);
        log("onPrimaryCallChanged, IsInBackground=" + mIsInBackground);

        Preconditions.checkState(!areSame(call, mPrimaryCallContext));
        final boolean canVideoPause = CallUtils.canVideoPause(call);

        if (isWaitingCall(mPrimaryCallContext) && canVideoPause && !mIsInBackground) {
            // Send resume request for the active call, if user rejects incoming
            // call and UI is in foreground.
            sendRequest(call, true);
        } else if (isWaitingCall(call) && canVideoPause(mPrimaryCallContext)) {
            // Send pause request if there is an active video call, and we just received a new
            // incoming call.
            sendRequest(mPrimaryCallContext.getCall(), false);
        } else if (isOutgoing(mPrimaryCallContext) && canVideoPause && !mIsInBackground) {
            // Send resume request for the active call, if user ends outgoing call
            // and UI is in foreground.
            sendRequest(call, true);
        }

        updatePrimaryCallContext(call);
    }

    /**
     * The function gets called when InCallUI receives a new incoming call.
     */
    @Override
    public void onIncomingCall(InCallState oldState, InCallState newState, Call call) {
        log("onIncomingCall, OldState=" + oldState + " NewState=" + newState + " Call=" + call);

        if (areSame(call, mPrimaryCallContext)) {
            return;
        }

        onPrimaryCallChanged(call);
    }

    private void updatePrimaryCallContext(Call call) {
        if (call == null) {
            mPrimaryCallContext = null;
        } else if (mPrimaryCallContext != null) {
            mPrimaryCallContext.update(call);
        } else {
            mPrimaryCallContext = new CallContext(call);
        }
    }

    /**
     * Called when UI goes in/out of the foreground.
     * @param showing true if UI is in the foreground, false otherwise.
     */
    public void onUiShowing(boolean showing) {
        if (!isVideoPausedEnabled() || mInCallPresenter == null) {
            return;
        }

        final boolean notify = mInCallPresenter.getInCallState() == InCallState.INCALL;
        if (showing) {
            onResume(notify);
        } else {
            onPause(notify);
        }
    }

    @Override
    public void onUpgradeToVideoRequest(Call call, int videoState) {
    }

    @Override
    public void onUpgradeToVideoSuccess(Call call) {
    }

    @Override
    public void onUpgradeToVideoFail(int status, Call call) {
        // TODO (ims-vt) Automatically bring in call ui to foreground.
    }

    @Override
    public void onDowngradeToAudio(Call call) {
    }

    /**
     * Called when UI becomes visible. This will send resume request for current video call, if any.
     */
    private void onResume(boolean notify) {
        log("onResume: notify=" + notify);

        mIsInBackground = false;
        if (canVideoPause(mPrimaryCallContext) && notify) {
            sendRequest(mPrimaryCallContext.getCall(), true);
        } else {
            log("onResume. Ignoring...");
        }
    }

    /**
     * Called when UI becomes invisible. This will send pause request for current video call, if any.
     */
    private void onPause(boolean notify) {
        log("onPause: notify=" + notify);

        mIsInBackground = true;
        if (canVideoPause(mPrimaryCallContext) && notify) {
            sendRequest(mPrimaryCallContext.getCall(), false);
        } else {
            log("onPause, Ignoring...");
        }
    }

    private void bringToForeground() {
        if (mInCallPresenter != null) {
            log("Bringing UI to foreground");
            mInCallPresenter.bringToForeground(false);
        } else {
            loge("InCallPresenter is null. Cannot bring UI to foreground");
        }
    }

    /**
     * Sends Pause/Resume request.
     * @param call Call to be paused/resumed.
     * @param resume If true resume request will be sent, otherwise pause request.
     */
    private void sendRequest(Call call, boolean resume) {
        if (resume) {
            log("sending resume request, call=" + call);
            call.getVideoCall().sendSessionModifyRequest(CallUtils.makeVideoUnPauseProfile(call));
        } else {
            log("sending pause request, call=" + call);
            call.getVideoCall().sendSessionModifyRequest(CallUtils.makeVideoPauseProfile(call));
        }
    }

    private boolean isVideoPausedEnabled() {
        return mVideoPauseMode != VIDEO_PAUSE_MODE_DISABLED;
    }

    private static boolean areSame(Call call, CallContext callContext) {
        if (call == null && callContext == null) {
            return true;
        } else if (call == null || callContext == null) {
            return false;
        }
        return call.getId().equals(callContext.getId());
    }

    private static boolean areSame(CallContext callContext, Call call) {
        return areSame(call, callContext);
    }

    private static boolean canVideoPause(CallContext callContext) {
        return isVideoCall(callContext) && callContext.getState() == Call.State.ACTIVE;
    }

    private static boolean isVideoCall(CallContext callContext) {
        return callContext != null && VideoProfile.VideoState.isVideo(callContext.getVideoState());
    }

    /**
     * Returns true if call is in incoming/waiting state, false otherwise.
     */
    private static boolean isWaitingCall(CallContext call) {
        return call != null && (call.getState() == Call.State.CALL_WAITING
                || call.getState() == Call.State.INCOMING);
    }

    private static boolean isWaitingCall(Call call) {
        return call != null && (call.getState() == Call.State.CALL_WAITING
                || call.getState() == Call.State.INCOMING);
    }

    /**
     * Returns true if the call is outgoing, false otherwise
     */
    private static boolean isOutgoing(CallContext call) {
        return call != null && Call.State.isDialing(call.getState());
    }

    /**
     * Returns true if the call is on hold, false otherwise
     */
    private static boolean isHolding(CallContext call) {
        return call != null && call.getState() == Call.State.ONHOLD;
    }

    private void log(String msg) {
        Log.d(this, TAG + msg);
    }

    private void loge(String msg) {
        Log.e(this, TAG + msg);
    }
}
