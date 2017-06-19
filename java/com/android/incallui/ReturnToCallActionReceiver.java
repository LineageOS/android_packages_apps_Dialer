/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telecom.CallAudioState;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.incallui.audiomode.AudioModeProvider;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.TelecomAdapter;

/** Handles clicks on the return-to-call bubble */
public class ReturnToCallActionReceiver extends BroadcastReceiver {

  public static final String ACTION_TOGGLE_SPEAKER = "toggleSpeaker";
  public static final String ACTION_SHOW_AUDIO_ROUTE_SELECTOR = "showAudioRouteSelector";
  public static final String ACTION_TOGGLE_MUTE = "toggleMute";
  public static final String ACTION_END_CALL = "endCall";

  @Override
  public void onReceive(Context context, Intent intent) {
    switch (intent.getAction()) {
      case ACTION_TOGGLE_SPEAKER:
        toggleSpeaker(context);
        break;
      case ACTION_SHOW_AUDIO_ROUTE_SELECTOR:
        showAudioRouteSelector(context);
        break;
      case ACTION_TOGGLE_MUTE:
        toggleMute(context);
        break;
      case ACTION_END_CALL:
        endCall(context);
        break;
    }
  }

  private void toggleSpeaker(Context context) {
    CallAudioState audioState = AudioModeProvider.getInstance().getAudioState();

    if ((audioState.getSupportedRouteMask() & CallAudioState.ROUTE_BLUETOOTH)
        == CallAudioState.ROUTE_BLUETOOTH) {
      LogUtil.w(
          "ReturnToCallActionReceiver.toggleSpeaker",
          "toggleSpeaker() called when bluetooth available."
              + " Probably should have shown audio route selector");
    }

    DialerCall call = getCall();

    int newRoute;
    if (audioState.getRoute() == CallAudioState.ROUTE_SPEAKER) {
      newRoute = CallAudioState.ROUTE_WIRED_OR_EARPIECE;
      Logger.get(context)
          .logCallImpression(
              DialerImpression.Type.BUBBLE_TURN_ON_WIRED_OR_EARPIECE,
              call != null ? call.getUniqueCallId() : "",
              call != null ? call.getTimeAddedMs() : 0);
    } else {
      newRoute = CallAudioState.ROUTE_SPEAKER;
      Logger.get(context)
          .logCallImpression(
              DialerImpression.Type.BUBBLE_TURN_ON_SPEAKERPHONE,
              call != null ? call.getUniqueCallId() : "",
              call != null ? call.getTimeAddedMs() : 0);
    }
    TelecomAdapter.getInstance().setAudioRoute(newRoute);
  }

  public void showAudioRouteSelector(Context context) {
    context.startActivity(new Intent(context, AudioRouteSelectorActivity.class));
  }

  private void toggleMute(Context context) {
    DialerCall call = getCall();
    boolean shouldMute = !AudioModeProvider.getInstance().getAudioState().isMuted();
    Logger.get(context)
        .logCallImpression(
            shouldMute
                ? DialerImpression.Type.BUBBLE_MUTE_CALL
                : DialerImpression.Type.BUBBLE_UNMUTE_CALL,
            call != null ? call.getUniqueCallId() : "",
            call != null ? call.getTimeAddedMs() : 0);
    TelecomAdapter.getInstance().mute(shouldMute);
  }

  private void endCall(Context context) {
    DialerCall call = getCall();

    Logger.get(context)
        .logCallImpression(
            DialerImpression.Type.BUBBLE_END_CALL,
            call != null ? call.getUniqueCallId() : "",
            call != null ? call.getTimeAddedMs() : 0);
    if (call != null) {
      call.disconnect();
    }
  }

  private DialerCall getCall() {
    CallList callList = InCallPresenter.getInstance().getCallList();
    if (callList != null) {
      DialerCall call = callList.getOutgoingCall();
      if (call == null) {
        call = callList.getActiveOrBackgroundCall();
      }
      if (call != null) {
        return call;
      }
    }
    return null;
  }
}
