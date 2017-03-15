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
import android.support.annotation.FloatRange;
import android.support.annotation.NonNull;
import android.support.v4.os.UserManagerCompat;
import android.telecom.VideoProfile;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.incallui.answer.protocol.AnswerScreen;
import com.android.incallui.answer.protocol.AnswerScreenDelegate;
import com.android.incallui.answerproximitysensor.AnswerProximitySensor;
import com.android.incallui.answerproximitysensor.PseudoScreenState;
import com.android.incallui.call.DialerCall;

/** Manages changes for an incoming call screen. */
public class AnswerScreenPresenter
    implements AnswerScreenDelegate, DialerCall.CannedTextResponsesLoadedListener {
  @NonNull private final Context context;
  @NonNull private final AnswerScreen answerScreen;
  @NonNull private final DialerCall call;

  public AnswerScreenPresenter(
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
  }

  @Override
  public void onAnswer(boolean answerVideoAsAudio) {
    if (answerScreen.isVideoUpgradeRequest()) {
      if (answerVideoAsAudio) {
        call.getVideoTech().acceptVideoRequestAsAudio();
      } else {
        call.getVideoTech().acceptVideoRequest();
      }
    } else {
      if (answerVideoAsAudio) {
        call.answer(VideoProfile.STATE_AUDIO_ONLY);
      } else {
        call.answer();
      }
    }
  }

  @Override
  public void onReject() {
    if (answerScreen.isVideoUpgradeRequest()) {
      call.getVideoTech().declineVideoRequest();
    } else {
      call.reject(false /* rejectWithMessage */, null);
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

  private boolean isSmsResponseAllowed(DialerCall call) {
    return UserManagerCompat.isUserUnlocked(context)
        && call.can(android.telecom.Call.Details.CAPABILITY_RESPOND_VIA_TEXT);
  }
}
