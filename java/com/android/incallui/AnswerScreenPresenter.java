/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.content.Context;
import android.os.SystemClock;
import android.support.annotation.FloatRange;
import android.support.annotation.NonNull;
import android.support.v4.os.UserManagerCompat;
import android.telecom.VideoProfile;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.incallui.answer.protocol.AnswerScreen;
import com.android.incallui.answer.protocol.AnswerScreenDelegate;
import com.android.incallui.answerproximitysensor.AnswerProximitySensor;
import com.android.incallui.answerproximitysensor.PseudoScreenState;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.DialerCallListener;

/** Manages changes for an incoming call screen. */
public class AnswerScreenPresenter
    implements AnswerScreenDelegate, DialerCall.CannedTextResponsesLoadedListener {
  private static final int ACCEPT_REJECT_CALL_TIME_OUT_IN_MILLIS = 5000;

  @NonNull private final Context context;
  @NonNull private final AnswerScreen answerScreen;
  @NonNull private final DialerCall call;
  private long actionPerformedTimeMillis;

  AnswerScreenPresenter(
      @NonNull Context context, @NonNull AnswerScreen answerScreen, @NonNull DialerCall call) {
    LogUtil.i("AnswerScreenPresenter.constructor", null);
    this.context = Assert.isNotNull(context);
    this.answerScreen = Assert.isNotNull(answerScreen);
    this.call = Assert.isNotNull(call);
    if (isSmsResponseAllowed(call)) {
      answerScreen.setTextResponses(call.getCannedSmsResponses());
    }
    call.addCannedTextResponsesLoadedListener(this);

    PseudoScreenState pseudoScreenState = InCallPresenter.getInstance().getPseudoScreenState();
    if (AnswerProximitySensor.shouldUse(context, call)) {
      new AnswerProximitySensor(context, call, pseudoScreenState);
    } else {
      pseudoScreenState.setOn(true);
    }
  }

  @Override
  public boolean isActionTimeout() {
    return actionPerformedTimeMillis != 0
        && SystemClock.elapsedRealtime() - actionPerformedTimeMillis
            >= ACCEPT_REJECT_CALL_TIME_OUT_IN_MILLIS;
  }

  @Override
  public void onAnswerScreenUnready() {
    call.removeCannedTextResponsesLoadedListener(this);
  }

  @Override
  public void onDismissDialog() {
    InCallPresenter.getInstance().onDismissDialog();
  }

  @Override
  public void onRejectCallWithMessage(String message) {
    call.reject(true /* rejectWithMessage */, message);
    onDismissDialog();
    addTimeoutCheck();
  }

  @Override
  public void onAnswer(boolean answerVideoAsAudio) {
    if (answerScreen.isVideoUpgradeRequest()) {
      if (answerVideoAsAudio) {
        Logger.get(context)
            .logCallImpression(
                DialerImpression.Type.VIDEO_CALL_REQUEST_ACCEPTED_AS_AUDIO,
                call.getUniqueCallId(),
                call.getTimeAddedMs());
        call.getVideoTech().acceptVideoRequestAsAudio();
      } else {
        Logger.get(context)
            .logCallImpression(
                DialerImpression.Type.VIDEO_CALL_REQUEST_ACCEPTED,
                call.getUniqueCallId(),
                call.getTimeAddedMs());
        call.getVideoTech().acceptVideoRequest();
      }
    } else {
      if (answerVideoAsAudio) {
        call.answer(VideoProfile.STATE_AUDIO_ONLY);
      } else {
        call.answer();
      }
    }
    addTimeoutCheck();
  }

  @Override
  public void onReject() {
    if (answerScreen.isVideoUpgradeRequest()) {
      Logger.get(context)
          .logCallImpression(
              DialerImpression.Type.VIDEO_CALL_REQUEST_DECLINED,
              call.getUniqueCallId(),
              call.getTimeAddedMs());
      call.getVideoTech().declineVideoRequest();
    } else {
      call.reject(false /* rejectWithMessage */, null);
    }
    addTimeoutCheck();
  }

  @Override
  public void onAnswerAndReleaseCall() {
    LogUtil.enterBlock("AnswerScreenPresenter.onAnswerAndReleaseCall");
    DialerCall activeCall = CallList.getInstance().getActiveCall();
    if (activeCall == null) {
      LogUtil.i("AnswerScreenPresenter.onAnswerAndReleaseCall", "activeCall == null");
      onAnswer(false);
    } else {
      activeCall.setReleasedByAnsweringSecondCall(true);
      activeCall.addListener(new AnswerOnDisconnected(activeCall));
      activeCall.disconnect();
    }
    addTimeoutCheck();
  }

  @Override
  public void onAnswerAndReleaseButtonDisabled() {
    DialerCall activeCall = CallList.getInstance().getActiveCall();
    if (activeCall != null) {
      activeCall.increaseSecondCallWithoutAnswerAndReleasedButtonTimes();
    }
  }

  @Override
  public void onAnswerAndReleaseButtonEnabled() {
    DialerCall activeCall = CallList.getInstance().getActiveCall();
    if (activeCall != null) {
      activeCall.increaseAnswerAndReleaseButtonDisplayedTimes();
    }
  }

  @Override
  public void onCannedTextResponsesLoaded(DialerCall call) {
    if (isSmsResponseAllowed(call)) {
      answerScreen.setTextResponses(call.getCannedSmsResponses());
    }
  }

  @Override
  public void updateWindowBackgroundColor(@FloatRange(from = -1f, to = 1.0f) float progress) {
    InCallActivity activity = (InCallActivity) answerScreen.getAnswerScreenFragment().getActivity();
    if (activity != null) {
      activity.updateWindowBackgroundColor(progress);
    }
  }

  private class AnswerOnDisconnected implements DialerCallListener {

    private final DialerCall disconnectingCall;

    AnswerOnDisconnected(DialerCall disconnectingCall) {
      this.disconnectingCall = disconnectingCall;
    }

    @Override
    public void onDialerCallDisconnect() {
      LogUtil.i(
          "AnswerScreenPresenter.AnswerOnDisconnected", "call disconnected, answering new call");
      call.answer();
      disconnectingCall.removeListener(this);
    }

    @Override
    public void onDialerCallUpdate() {}

    @Override
    public void onDialerCallChildNumberChange() {}

    @Override
    public void onDialerCallLastForwardedNumberChange() {}

    @Override
    public void onDialerCallUpgradeToVideo() {}

    @Override
    public void onDialerCallSessionModificationStateChange() {}

    @Override
    public void onWiFiToLteHandover() {}

    @Override
    public void onHandoverToWifiFailure() {}

    @Override
    public void onInternationalCallOnWifi() {}

    @Override
    public void onEnrichedCallSessionUpdate() {}
  }

  private boolean isSmsResponseAllowed(DialerCall call) {
    return UserManagerCompat.isUserUnlocked(context)
        && call.can(android.telecom.Call.Details.CAPABILITY_RESPOND_VIA_TEXT);
  }

  private void addTimeoutCheck() {
    actionPerformedTimeMillis = SystemClock.elapsedRealtime();
    if (answerScreen.getAnswerScreenFragment().isVisible()) {
      ThreadUtil.postDelayedOnUiThread(
          () -> {
            if (!answerScreen.getAnswerScreenFragment().isVisible()) {
              LogUtil.d(
                  "AnswerScreenPresenter.addTimeoutCheck",
                  "accept/reject call timed out, do nothing");
              return;
            }
            LogUtil.i("AnswerScreenPresenter.addTimeoutCheck", "accept/reject call timed out");
            // Force re-evaluate which fragment to show.
            InCallPresenter.getInstance().refreshUi();
          },
          ACCEPT_REJECT_CALL_TIME_OUT_IN_MILLIS);
    }
  }
}
