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

import android.support.annotation.NonNull;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.DialerCall.State;
import java.util.Objects;

/**
 * This class is responsible for generating video pause/resume requests when the InCall UI is sent
 * to the background and subsequently brought back to the foreground.
 */
class VideoPauseController implements InCallStateListener, IncomingCallListener {
  private static VideoPauseController sVideoPauseController;
  private InCallPresenter mInCallPresenter;

  /** The current call, if applicable. */
  private DialerCall mPrimaryCall = null;

  /**
   * The cached state of primary call, updated after onStateChange has processed.
   *
   * <p>These values are stored to detect specific changes in state between onStateChange calls.
   */
  private int mPrevCallState = State.INVALID;

  private boolean mWasVideoCall = false;

  /**
   * Tracks whether the application is in the background. {@code True} if the application is in the
   * background, {@code false} otherwise.
   */
  private boolean mIsInBackground = false;

  /**
   * Singleton accessor for the {@link VideoPauseController}.
   *
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
   * Determines if a call is in incoming/waiting state.
   *
   * @param call The call.
   * @return {@code true} if the call is in incoming or waiting state, {@code false} otherwise.
   */
  private static boolean isIncomingCall(DialerCall call) {
    return call != null
        && (call.getState() == DialerCall.State.CALL_WAITING
            || call.getState() == DialerCall.State.INCOMING);
  }

  /**
   * Determines if a call is dialing.
   *
   * @return {@code true} if the call is dialing, {@code false} otherwise.
   */
  private boolean wasDialing() {
    return DialerCall.State.isDialing(mPrevCallState);
  }

  /**
   * Configures the {@link VideoPauseController} to listen to call events. Configured via the {@link
   * com.android.incallui.InCallPresenter}.
   *
   * @param inCallPresenter The {@link com.android.incallui.InCallPresenter}.
   */
  public void setUp(@NonNull InCallPresenter inCallPresenter) {
    LogUtil.enterBlock("VideoPauseController.setUp");
    mInCallPresenter = Assert.isNotNull(inCallPresenter);
    mInCallPresenter.addListener(this);
    mInCallPresenter.addIncomingCallListener(this);
  }

  /**
   * Cleans up the {@link VideoPauseController} by removing all listeners and clearing its internal
   * state. Called from {@link com.android.incallui.InCallPresenter}.
   */
  public void tearDown() {
    LogUtil.enterBlock("VideoPauseController.tearDown");
    mInCallPresenter.removeListener(this);
    mInCallPresenter.removeIncomingCallListener(this);
    clear();
  }

  /** Clears the internal state for the {@link VideoPauseController}. */
  private void clear() {
    mInCallPresenter = null;
    mPrimaryCall = null;
    mPrevCallState = State.INVALID;
    mWasVideoCall = false;
    mIsInBackground = false;
  }

  /**
   * Handles changes in the {@link InCallState}. Triggers pause and resumption of video for the
   * current foreground call.
   *
   * @param oldState The previous {@link InCallState}.
   * @param newState The current {@link InCallState}.
   * @param callList List of current call.
   */
  @Override
  public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
    DialerCall call;
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

    boolean hasPrimaryCallChanged = !Objects.equals(call, mPrimaryCall);
    boolean canVideoPause = videoCanPause(call);

    LogUtil.i(
        "VideoPauseController.onStateChange",
        "hasPrimaryCallChanged: %b, videoCanPause: %b, isInBackground: %b",
        hasPrimaryCallChanged,
        canVideoPause,
        mIsInBackground);

    if (hasPrimaryCallChanged) {
      onPrimaryCallChanged(call);
      return;
    }

    if (wasDialing() && canVideoPause && mIsInBackground) {
      // Bring UI to foreground if outgoing request becomes active while UI is in
      // background.
      bringToForeground();
    } else if (!mWasVideoCall && canVideoPause && mIsInBackground) {
      // Bring UI to foreground if VoLTE call becomes active while UI is in
      // background.
      bringToForeground();
    }

    updatePrimaryCallContext(call);
  }

  /**
   * Handles a change to the primary call.
   *
   * <p>Reject incoming or hangup dialing call: Where the previous call was an incoming call or a
   * call in dialing state, resume the new primary call. DialerCall swap: Where the new primary call
   * is incoming, pause video on the previous primary call.
   *
   * @param call The new primary call.
   */
  private void onPrimaryCallChanged(DialerCall call) {
    LogUtil.i(
        "VideoPauseController.onPrimaryCallChanged",
        "new call: %s, old call: %s, mIsInBackground: %b",
        call,
        mPrimaryCall,
        mIsInBackground);

    if (Objects.equals(call, mPrimaryCall)) {
      throw new IllegalStateException();
    }
    final boolean canVideoPause = videoCanPause(call);

    if (canVideoPause && !mIsInBackground) {
      // Send resume request for the active call, if user rejects incoming call, ends dialing
      // call, or the call was previously in a paused state and UI is in the foreground.
      sendRequest(call, true);
    } else if (isIncomingCall(call) && videoCanPause(mPrimaryCall)) {
      // Send pause request if there is an active video call, and we just received a new
      // incoming call.
      sendRequest(mPrimaryCall, false);
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
  public void onIncomingCall(InCallState oldState, InCallState newState, DialerCall call) {
    LogUtil.i(
        "VideoPauseController.onIncomingCall",
        "oldState: %s, newState: %s, call: %s",
        oldState,
        newState,
        call);

    if (Objects.equals(call, mPrimaryCall)) {
      return;
    }

    onPrimaryCallChanged(call);
  }

  /**
   * Caches a reference to the primary call and stores its previous state.
   *
   * @param call The new primary call.
   */
  private void updatePrimaryCallContext(DialerCall call) {
    if (call == null) {
      mPrimaryCall = null;
      mPrevCallState = State.INVALID;
      mWasVideoCall = false;
    } else {
      mPrimaryCall = call;
      mPrevCallState = call.getState();
      mWasVideoCall = call.isVideoCall();
    }
  }

  /**
   * Called when UI goes in/out of the foreground.
   *
   * @param showing true if UI is in the foreground, false otherwise.
   */
  public void onUiShowing(boolean showing) {
    if (mInCallPresenter == null) {
      return;
    }

    final boolean isInCall = mInCallPresenter.getInCallState() == InCallState.INCALL;
    if (showing) {
      onResume(isInCall);
    } else {
      onPause(isInCall);
    }
  }

  /**
   * Called when UI is brought to the foreground. Sends a session modification request to resume the
   * outgoing video.
   *
   * @param isInCall {@code true} if we are in an active call. A resume request is only sent to the
   *     video provider if we are in a call.
   */
  private void onResume(boolean isInCall) {
    mIsInBackground = false;
    if (isInCall) {
      sendRequest(mPrimaryCall, true);
    }
  }

  /**
   * Called when UI is sent to the background. Sends a session modification request to pause the
   * outgoing video.
   *
   * @param isInCall {@code true} if we are in an active call. A pause request is only sent to the
   *     video provider if we are in a call.
   */
  private void onPause(boolean isInCall) {
    mIsInBackground = true;
    if (isInCall) {
      sendRequest(mPrimaryCall, false);
    }
  }

  private void bringToForeground() {
    LogUtil.enterBlock("VideoPauseController.bringToForeground");
    if (mInCallPresenter != null) {
      mInCallPresenter.bringToForeground(false);
    } else {
      LogUtil.e(
          "VideoPauseController.bringToForeground",
          "InCallPresenter is null. Cannot bring UI to foreground");
    }
  }

  /**
   * Sends Pause/Resume request.
   *
   * @param call DialerCall to be paused/resumed.
   * @param resume If true resume request will be sent, otherwise pause request.
   */
  private void sendRequest(DialerCall call, boolean resume) {
    if (call == null) {
      return;
    }

    if (resume) {
      call.getVideoTech().unpause();
    } else {
      call.getVideoTech().pause();
    }
  }

  private static boolean videoCanPause(DialerCall call) {
    return call != null && call.isVideoCall() && call.getState() == DialerCall.State.ACTIVE;
  }
}
