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
 * limitations under the License.
 */

package com.android.incallui.video.impl;

import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.telecom.CallAudioState;
import android.view.View;
import android.view.View.OnClickListener;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.incallui.incall.protocol.InCallButtonUiDelegate;
import com.android.incallui.video.impl.CheckableImageButton.OnCheckedChangeListener;
import com.android.incallui.video.protocol.VideoCallScreenDelegate;

/** Manages a single button. */
public class SpeakerButtonController implements OnCheckedChangeListener, OnClickListener {

  @NonNull private final InCallButtonUiDelegate inCallButtonUiDelegate;
  @NonNull private final VideoCallScreenDelegate videoCallScreenDelegate;

  @NonNull private CheckableImageButton button;

  @DrawableRes private int icon = R.drawable.quantum_ic_volume_up_vd_theme_24;

  private boolean isChecked;
  private boolean checkable;
  private boolean isEnabled;
  private CharSequence contentDescription;

  SpeakerButtonController(
      @NonNull CheckableImageButton button,
      @NonNull InCallButtonUiDelegate inCallButtonUiDelegate,
      @NonNull VideoCallScreenDelegate videoCallScreenDelegate) {
    this.inCallButtonUiDelegate = Assert.isNotNull(inCallButtonUiDelegate);
    this.videoCallScreenDelegate = Assert.isNotNull(videoCallScreenDelegate);
    this.button = Assert.isNotNull(button);
  }

  public void setEnabled(boolean isEnabled) {
    this.isEnabled = isEnabled;
    updateButtonState();
  }

  void updateButtonState() {
    button.setVisibility(View.VISIBLE);
    button.setEnabled(isEnabled);
    button.setChecked(isChecked);
    button.setOnClickListener(checkable ? null : this);
    button.setOnCheckedChangeListener(checkable ? this : null);
    button.setImageResource(icon);
    button.setContentDescription(contentDescription);
  }

  public void setAudioState(CallAudioState audioState) {
    LogUtil.i("SpeakerButtonController.setSupportedAudio", "audioState: " + audioState);

    @StringRes int contentDescriptionResId;
    if ((audioState.getSupportedRouteMask() & CallAudioState.ROUTE_BLUETOOTH)
        == CallAudioState.ROUTE_BLUETOOTH) {
      checkable = false;
      isChecked = false;

      if ((audioState.getRoute() & CallAudioState.ROUTE_BLUETOOTH)
          == CallAudioState.ROUTE_BLUETOOTH) {
        icon = R.drawable.quantum_ic_bluetooth_audio_vd_theme_24;
        contentDescriptionResId = R.string.incall_content_description_bluetooth;
      } else if ((audioState.getRoute() & CallAudioState.ROUTE_SPEAKER)
          == CallAudioState.ROUTE_SPEAKER) {
        icon = R.drawable.quantum_ic_volume_up_vd_theme_24;
        contentDescriptionResId = R.string.incall_content_description_speaker;
      } else if ((audioState.getRoute() & CallAudioState.ROUTE_WIRED_HEADSET)
          == CallAudioState.ROUTE_WIRED_HEADSET) {
        icon = R.drawable.quantum_ic_headset_vd_theme_24;
        contentDescriptionResId = R.string.incall_content_description_headset;
      } else {
        icon = R.drawable.quantum_ic_phone_in_talk_vd_theme_24;
        contentDescriptionResId = R.string.incall_content_description_earpiece;
      }
    } else {
      checkable = true;
      isChecked = audioState.getRoute() == CallAudioState.ROUTE_SPEAKER;
      icon = R.drawable.quantum_ic_volume_up_vd_theme_24;
      contentDescriptionResId = R.string.incall_content_description_speaker;
    }

    contentDescription = button.getContext().getText(contentDescriptionResId);
    updateButtonState();
  }

  @Override
  public void onCheckedChanged(CheckableImageButton button, boolean isChecked) {
    LogUtil.i("SpeakerButtonController.onCheckedChanged", null);
    inCallButtonUiDelegate.toggleSpeakerphone();
    videoCallScreenDelegate.resetAutoFullscreenTimer();
  }

  @Override
  public void onClick(View view) {
    LogUtil.i("SpeakerButtonController.onClick", null);
    inCallButtonUiDelegate.showAudioRouteSelector();
    videoCallScreenDelegate.resetAutoFullscreenTimer();
  }
}
