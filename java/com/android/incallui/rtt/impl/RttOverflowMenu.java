/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.incallui.rtt.impl;

import android.content.Context;
import android.telecom.CallAudioState;
import android.view.View;
import android.widget.PopupWindow;
import com.android.incallui.incall.protocol.InCallButtonUiDelegate;
import com.android.incallui.rtt.impl.RttCheckableButton.OnCheckedChangeListener;
import com.android.incallui.speakerbuttonlogic.SpeakerButtonInfo;
import com.android.incallui.speakerbuttonlogic.SpeakerButtonInfo.IconSize;

/** Overflow menu for RTT call. */
public class RttOverflowMenu extends PopupWindow implements OnCheckedChangeListener {

  private final RttCheckableButton muteButton;
  private final RttCheckableButton speakerButton;
  private final RttCheckableButton dialpadButton;
  private final RttCheckableButton addCallButton;
  private final InCallButtonUiDelegate inCallButtonUiDelegate;

  RttOverflowMenu(Context context, InCallButtonUiDelegate inCallButtonUiDelegate) {
    super(context);
    this.inCallButtonUiDelegate = inCallButtonUiDelegate;
    View view = View.inflate(context, R.layout.overflow_menu, null);
    setContentView(view);
    setOnDismissListener(this::dismiss);
    setFocusable(true);
    setWidth(context.getResources().getDimensionPixelSize(R.dimen.rtt_overflow_menu_width));
    muteButton = view.findViewById(R.id.menu_mute);
    muteButton.setOnCheckedChangeListener(this);
    speakerButton = view.findViewById(R.id.menu_speaker);
    speakerButton.setOnCheckedChangeListener(this);
    dialpadButton = view.findViewById(R.id.menu_keypad);
    dialpadButton.setOnCheckedChangeListener(this);
    addCallButton = view.findViewById(R.id.menu_add_call);
    addCallButton.setOnCheckedChangeListener(this);
  }

  @Override
  public void onCheckedChanged(RttCheckableButton button, boolean isChecked) {
    if (button == muteButton) {
      inCallButtonUiDelegate.muteClicked(isChecked, true);
    } else if (button == speakerButton) {
      inCallButtonUiDelegate.toggleSpeakerphone();
    } else if (button == dialpadButton) {
      inCallButtonUiDelegate.showDialpadClicked(isChecked);
    } else if (button == addCallButton) {
      inCallButtonUiDelegate.addCallClicked();
    }
  }

  void setMuteButtonChecked(boolean isChecked) {
    muteButton.setChecked(isChecked);
  }

  void setAudioState(CallAudioState audioState) {
    SpeakerButtonInfo info = new SpeakerButtonInfo(audioState, IconSize.SIZE_24_DP);
    if (info.checkable) {
      speakerButton.setChecked(info.isChecked);
    }
  }
}
