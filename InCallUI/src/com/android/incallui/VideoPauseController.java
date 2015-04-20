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
import com.android.incallui.Call.State;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.InCallVideoCallCallbackNotifier.SessionModificationListener;
import com.google.common.base.Preconditions;

/**
 * This class is responsible for generating video pause/resume requests when the InCall UI is sent
 * to the background and subsequently brought back to the foreground.
 */
class VideoPauseController implements InCallStateListener, IncomingCallListener,
        SessionModificationListener {
    private static final String TAG = "VideoPauseController:";

    /**
     * Keeps track of the current active/foreground call.
     */
    private class CallContext {
        public CallContext(Call call) {
            Preconditions.checkNotNull(call);
            update(call);
        }

        public void update(Call call) {
            mCall = Preconditions.checkNotNull(call);
            mState = call.getState();
            mVideoState = call.getVideoState();
        }

        public int getState() {
            return mState;
        }

        public int getVideoState() {
            return mVideoState;
        }

        public String toString() {
            return String.format("CallContext {CallId=%s, State=%s, VideoState=%d}",
                    mCall.getId(), mState, mVideoState);
        }

        public Call getCall() {
            return mCall;
        }

        private int mState = State.INVALID;
        private int mVideoState;
        private Call mCall;
    }

    private InCallPresenter mInCallPresenter;
    private static VideoPauseController sVideoPauseController;

    /**
     * The current call context, if applicable.
     */
    private CallContext mPrimaryCallContext = null;

    /**
     * Tracks whether the application is in the background. {@code True} if the application is in
     * the background, {@code false} otherwise.
     */
    private boolean mIsInBackground = false;

    /**
     * Singleton accessor for the {@link VideoPauseController}.
     * @return Singleton instance of the {@link VideoPauseController}.
     */
    /*package*/
    static synchronized VideoPauseController getInstance() {
        if (sVideoPauseController == null) {
            sVideoPauseController = new VideoPauseController();
        }
        return sVideoPauseController;
    }

    /**
     * Configures the {@link VideoPauseController} to listen to call events.  Configured via the
     * {@link com.android.incallui.InCallPresenter}.
     *
     * @param inCallPresenter The {@link com.android.incallui.InCallPresenter}.
     */
    public void setUp(InCallPresenter inCallPresenter) {
        log("setUp");
        mInCallPresenter = Preconditions.checkNotNull(inCallPresenter);
        mInCallPresenter.addListener(this);
        mInCallPresenter.addIncomingCallListener(this);
        InCallVideoCallCallbackNotifier.getInstance().addSessionModificationListener(this);
    }

    /**
     * Cleans up the {@link VideoPauseController} by removing all listeners and clearing its
     * internal state.  Called from {@link com.android.incallui.InCallPresenter}.
     */
    public void tearDown() {
        log("tearDown...");
        InCallVideoCallCallbackNotifier.getInstance().removeSessionModificationListener(this);
        mInCallPresenter.removeListener(this);
        mInCallPresenter.removeIncomingCallListener(this);
        clear();
    }

    /**
     * Clears the internal state for the {@link VideoPauseController}.
     */
    private void clear() {
        mInCallPresenter = null;
        mPrimaryCallContext = null;
        mIsInBackground = false;
    }

    /**
     * Handles changes in the {@link InCallState}.  Triggers pause and resumption of video for the
     * current foreground call.
     *
     * @param oldState The previous {@link InCallState}.
     * @param newState The current {@link InCallState}.
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

        if (isDialing(mPrimaryCallContext) && canVideoPause && mIsInBackground) {
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

    /**
     * Handles a change to the primary call.
     * <p>
     * Reject incoming or hangup dialing call: Where the previous call was an incoming call or a
     * call in dialing state, resume the new primary call.
     * Call swap: Where the new primary call is incoming, pause video on the previous primary call.
     *
     * @param call The new primary call.
     */
    private void onPrimaryCallChanged(Call call) {
        log("onPrimaryCallChanged: New call = " + call);
        log("onPrimaryCallChanged: Old call = " + mPrimaryCallContext);
        log("onPrimaryCallChanged, IsInBackground=" + mIsInBackground);

        Preconditions.checkState(!areSame(call, mPrimaryCallContext));
        final boolean canVideoPause = CallUtils.canVideoPause(call);

        if ((isIncomingCall(mPrimaryCallContext) || isDialing(mPrimaryCallContext))
                && canVideoPause && !mIsInBackground) {
            // Send resume request for the active call, if user rejects incoming call or ends
            // dialing call and UI is in the foreground.
            sendRequest(call, true);
        } else if (isIncomingCall(call) && canVideoPause(mPrimaryCallContext)) {
            // Send pause request if there is an active video call, and we just received a new
            // incoming call.
            sendRequest(mPrimaryCallContext.getCall(), false);
        }

        updatePrimaryCallContext(call);
    }

    /**
     * Handles new incoming calls by triggering a change in the primary call.
     *
     * @param oldState the old {@link InCallState}.
     * @param newState the new {@link InCallState}.
     * @param call the incoming call.
     */
    @Override
    public void onIncomingCall(InCallState oldState, InCallState newState, Call call) {
        log("onIncomingCall, OldState=" + oldState + " NewState=" + newState + " Call=" + call);

        if (areSame(call, mPrimaryCallContext)) {
            return;
        }

        onPrimaryCallChanged(call);
    }

    /**
     * Caches a reference to the primary call and stores its previous state.
     *
     * @param call The new primary call.
     */
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
        // Only send pause/unpause requests if we are in the INCALL state.
        if (mInCallPresenter == null || mInCallPresenter.getInCallState() != InCallState.INCALL) {
            return;
        }

        if (showing) {
            onResume();
        } else {
            onPause();
        }
    }

    /**
     * Handles requests to upgrade to video.
     *
     * @param call The call the request was received for.
     * @param videoState The video state that the request wants to upgrade to.
     */
    @Override
    public void onUpgradeToVideoRequest(Call call, int videoState) {
        // Not used.
    }

    /**
     * Handles successful upgrades to video.
     * @param call The call the request was successful for.
     */
    @Override
    public void onUpgradeToVideoSuccess(Call call) {
        // Not used.
    }

    /**
     * Handles a failure to upgrade a call to video.
     *
     * @param status The failure status.
     * @param call The call the request was successful for.
     */
    @Override
    public void onUpgradeToVideoFail(int status, Call call) {
        // TODO (ims-vt) Automatically bring in call ui to foreground.
    }

    /**
     * Handles a downgrade of a call to audio-only.
     *
     * @param call The call which was downgraded to audio-only.
     */
    @Override
    public void onDowngradeToAudio(Call call) {
    }

    /**
     * Called when UI is brought to the foreground.  Sends a session modification request to resume
     * the outgoing video.
     */
    private void onResume() {
        log("onResume");

        mIsInBackground = false;
        if (canVideoPause(mPrimaryCallContext)) {
            sendRequest(mPrimaryCallContext.getCall(), true);
        } else {
            log("onResume. Ignoring...");
        }
    }

    /**
     * Called when UI is sent to the background.  Sends a session modification request to pause the
     * outgoing video.
     */
    private void onPause() {
        log("onPause");

        mIsInBackground = true;
        if (canVideoPause(mPrimaryCallContext)) {
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
     *
     * @param call Call to be paused/resumed.
     * @param resume If true resume request will be sent, otherwise pause request.
     */
    private void sendRequest(Call call, boolean resume) {
        // Check if this call supports pause/un-pause.
        if (!call.can(android.telecom.Call.Details.CAPABILITY_CAN_PAUSE_VIDEO)) {
            return;
        }

        if (resume) {
            log("sending resume request, call=" + call);
            call.getVideoCall()
                    .sendSessionModifyRequest(CallUtils.makeVideoUnPauseProfile(call));
        } else {
            log("sending pause request, call=" + call);
            call.getVideoCall().sendSessionModifyRequest(CallUtils.makeVideoPauseProfile(call));
        }
    }

    /**
     * Determines if a given call is the same one stored in a {@link CallContext}.
     *
     * @param call The call.
     * @param callContext The call context.
     * @return {@code true} if the {@link Call} is the same as the one referenced in the
     *      {@link CallContext}.
     */
    private static boolean areSame(Call call, CallContext callContext) {
        if (call == null && callContext == null) {
            return true;
        } else if (call == null || callContext == null) {
            return false;
        }
        return call.equals(callContext.getCall());
    }

    /**
     * Determines if a video call can be paused.  Only a video call which is active can be paused.
     *
     * @param callContext The call context to check.
     * @return {@code true} if the call is an active video call.
     */
    private static boolean canVideoPause(CallContext callContext) {
        return isVideoCall(callContext) && callContext.getState() == Call.State.ACTIVE;
    }

    /**
     * Determines if a call referenced by a {@link CallContext} is a video call.
     *
     * @param callContext The call context.
     * @return {@code true} if the call is a video call, {@code false} otherwise.
     */
    private static boolean isVideoCall(CallContext callContext) {
        return callContext != null && CallUtils.isVideoCall(callContext.getVideoState());
    }

    /**
     * Determines if call is in incoming/waiting state.
     *
     * @param call The call context.
     * @return {@code true} if the call is in incoming or waiting state, {@code false} otherwise.
     */
    private static boolean isIncomingCall(CallContext call) {
        return call != null && isIncomingCall(call.getCall());
    }

    /**
     * Determines if a call is in incoming/waiting state.
     *
     * @param call The call.
     * @return {@code true} if the call is in incoming or waiting state, {@code false} otherwise.
     */
    private static boolean isIncomingCall(Call call) {
        return call != null && (call.getState() == Call.State.CALL_WAITING
                || call.getState() == Call.State.INCOMING);
    }

    /**
     * Determines if a call is dialing.
     *
     * @param call The call context.
     * @return {@code true} if the call is dialing, {@code false} otherwise.
     */
    private static boolean isDialing(CallContext call) {
        return call != null && Call.State.isDialing(call.getState());
    }

    /**
     * Determines if a call is holding.
     *
     * @param call The call context.
     * @return {@code true} if the call is holding, {@code false} otherwise.
     */
    private static boolean isHolding(CallContext call) {
        return call != null && call.getState() == Call.State.ONHOLD;
    }

    /**
     * Logs a debug message.
     *
     * @param msg The message.
     */
    private void log(String msg) {
        Log.d(this, TAG + msg);
    }

    /**
     * Logs an error message.
     *
     * @param msg The message.
     */
    private void loge(String msg) {
        Log.e(this, TAG + msg);
    }
}
