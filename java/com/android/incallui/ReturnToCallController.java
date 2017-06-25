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

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.telecom.CallAudioState;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialershared.bubble.Bubble;
import com.android.dialershared.bubble.BubbleInfo;
import com.android.dialershared.bubble.BubbleInfo.Action;
import com.android.incallui.InCallPresenter.InCallUiListener;
import com.android.incallui.audiomode.AudioModeProvider;
import com.android.incallui.audiomode.AudioModeProvider.AudioModeListener;
import com.android.incallui.call.CallList;
import com.android.incallui.call.CallList.Listener;
import com.android.incallui.call.DialerCall;
import com.android.incallui.speakerbuttonlogic.SpeakerButtonInfo;
import com.android.incallui.speakerbuttonlogic.SpeakerButtonInfo.IconSize;
import java.util.ArrayList;
import java.util.List;

/**
 * Listens for events relevant to the return-to-call bubble and updates the bubble's state as
 * necessary
 */
public class ReturnToCallController implements InCallUiListener, Listener, AudioModeListener {

  private final Context context;

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  Bubble bubble;

  private CallAudioState audioState;

  private final PendingIntent toggleSpeaker;
  private final PendingIntent showSpeakerSelect;
  private final PendingIntent toggleMute;
  private final PendingIntent endCall;

  public static boolean isEnabled(Context context) {
    return ConfigProviderBindings.get(context).getBoolean("enable_return_to_call_bubble", false);
  }

  public ReturnToCallController(Context context) {
    this.context = context;

    toggleSpeaker = createActionIntent(ReturnToCallActionReceiver.ACTION_TOGGLE_SPEAKER);
    showSpeakerSelect =
        createActionIntent(ReturnToCallActionReceiver.ACTION_SHOW_AUDIO_ROUTE_SELECTOR);
    toggleMute = createActionIntent(ReturnToCallActionReceiver.ACTION_TOGGLE_MUTE);
    endCall = createActionIntent(ReturnToCallActionReceiver.ACTION_END_CALL);

    InCallPresenter.getInstance().addInCallUiListener(this);
    CallList.getInstance().addListener(this);
    AudioModeProvider.getInstance().addListener(this);
    audioState = AudioModeProvider.getInstance().getAudioState();
  }

  public void tearDown() {
    InCallPresenter.getInstance().removeInCallUiListener(this);
    CallList.getInstance().removeListener(this);
    AudioModeProvider.getInstance().removeListener(this);
  }

  @Override
  public void onUiShowing(boolean showing) {
    if (showing) {
      hide();
    } else {
      if (TelecomUtil.isInCall(context)) {
        show();
      }
    }
  }

  private void hide() {
    if (bubble != null) {
      bubble.hide();
    } else {
      LogUtil.i("ReturnToCallController.hide", "hide() called without calling show()");
    }
  }

  private void show() {
    if (bubble == null) {
      bubble = startNewBubble();
    } else {
      bubble.show();
    }
  }

  private Bubble startNewBubble() {
    if (!Bubble.canShowBubbles(context)) {
      LogUtil.i("ReturnToCallController.startNewBubble", "can't show bubble, no permission");
      return null;
    }
    Bubble returnToCallBubble = Bubble.createBubble(context, generateBubbleInfo());
    returnToCallBubble.show();
    return returnToCallBubble;
  }

  @Override
  public void onIncomingCall(DialerCall call) {}

  @Override
  public void onUpgradeToVideo(DialerCall call) {}

  @Override
  public void onSessionModificationStateChange(DialerCall call) {}

  @Override
  public void onCallListChange(CallList callList) {}

  @Override
  public void onDisconnect(DialerCall call) {
    if (bubble != null && bubble.isVisible()) {
      bubble.showText(context.getText(R.string.incall_call_ended));
    }

    if (!TelecomUtil.isInCall(context)) {
      hide();
    }
  }

  @Override
  public void onWiFiToLteHandover(DialerCall call) {}

  @Override
  public void onHandoverToWifiFailed(DialerCall call) {}

  @Override
  public void onInternationalCallOnWifi(@NonNull DialerCall call) {}

  @Override
  public void onAudioStateChanged(CallAudioState audioState) {
    this.audioState = audioState;
    if (bubble != null) {
      bubble.updateActions(generateActions());
    }
  }

  private BubbleInfo generateBubbleInfo() {
    Intent activityIntent = InCallActivity.getIntent(context, false, false, false);
    activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    return BubbleInfo.builder()
        .setPrimaryColor(context.getResources().getColor(R.color.dialer_theme_color, null))
        .setPrimaryIcon(Icon.createWithResource(context, R.drawable.on_going_call))
        .setStartingYPosition(
            context.getResources().getDimensionPixelOffset(R.dimen.return_to_call_initial_offset_y))
        .setPrimaryIntent(PendingIntent.getActivity(context, 0, activityIntent, 0))
        .setActions(generateActions())
        .build();
  }

  @NonNull
  private List<Action> generateActions() {
    List<Action> actions = new ArrayList<>();
    SpeakerButtonInfo speakerButtonInfo = new SpeakerButtonInfo(audioState, IconSize.SIZE_24_DP);

    actions.add(
        Action.builder()
            .setIcon(Icon.createWithResource(context, speakerButtonInfo.icon))
            .setName(context.getText(speakerButtonInfo.label))
            .setChecked(speakerButtonInfo.isChecked)
            .setIntent(speakerButtonInfo.checkable ? toggleSpeaker : showSpeakerSelect)
            .build());

    actions.add(
        Action.builder()
            .setIcon(Icon.createWithResource(context, R.drawable.quantum_ic_mic_off_white_24))
            .setName(context.getText(R.string.incall_label_mute))
            .setChecked(audioState.isMuted())
            .setIntent(toggleMute)
            .build());
    actions.add(
        Action.builder()
            .setIcon(Icon.createWithResource(context, R.drawable.quantum_ic_call_end_white_24))
            .setName(context.getText(R.string.incall_label_end_call))
            .setIntent(endCall)
            .build());
    return actions;
  }

  @NonNull
  private PendingIntent createActionIntent(String action) {
    Intent toggleSpeaker = new Intent(context, ReturnToCallActionReceiver.class);
    toggleSpeaker.setAction(action);
    return PendingIntent.getBroadcast(context, 0, toggleSpeaker, 0);
  }
}
