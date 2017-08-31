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

package com.android.incallui.speakerbuttonlogic;

import android.support.annotation.DrawableRes;
import android.support.annotation.IntDef;
import android.support.annotation.StringRes;
import android.telecom.CallAudioState;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Info about how a "Speaker" button should be displayed */
public class SpeakerButtonInfo {

  // Testing note: most of this is exercised in ReturnToCallTest.java

  /** Preferred size for icons */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({IconSize.SIZE_24_DP, IconSize.SIZE_36_DP})
  public @interface IconSize {
    int SIZE_24_DP = 1;
    int SIZE_36_DP = 2;
  }

  @DrawableRes public final int icon;
  @StringRes public final int contentDescription;
  @StringRes public final int label;
  public final boolean checkable;
  public final boolean isChecked;

  public SpeakerButtonInfo(CallAudioState audioState, @IconSize int iconSize) {
    if ((audioState.getSupportedRouteMask() & CallAudioState.ROUTE_BLUETOOTH)
        == CallAudioState.ROUTE_BLUETOOTH) {
      checkable = false;
      isChecked = false;
      label = R.string.incall_label_audio;

      if ((audioState.getRoute() & CallAudioState.ROUTE_BLUETOOTH)
          == CallAudioState.ROUTE_BLUETOOTH) {
        icon =
            iconSize == IconSize.SIZE_36_DP
                ? R.drawable.quantum_ic_bluetooth_audio_white_36
                : R.drawable.quantum_ic_bluetooth_audio_white_24;
        contentDescription = R.string.incall_content_description_bluetooth;
      } else if ((audioState.getRoute() & CallAudioState.ROUTE_SPEAKER)
          == CallAudioState.ROUTE_SPEAKER) {
        icon =
            iconSize == IconSize.SIZE_36_DP
                ? R.drawable.quantum_ic_volume_up_white_36
                : R.drawable.quantum_ic_volume_up_white_24;
        contentDescription = R.string.incall_content_description_speaker;
      } else if ((audioState.getRoute() & CallAudioState.ROUTE_WIRED_HEADSET)
          == CallAudioState.ROUTE_WIRED_HEADSET) {
        icon =
            iconSize == IconSize.SIZE_36_DP
                ? R.drawable.quantum_ic_headset_white_36
                : R.drawable.quantum_ic_headset_white_24;
        contentDescription = R.string.incall_content_description_headset;
      } else {
        icon =
            iconSize == IconSize.SIZE_36_DP
                ? R.drawable.quantum_ic_phone_in_talk_white_36
                : R.drawable.quantum_ic_phone_in_talk_white_24;
        contentDescription = R.string.incall_content_description_earpiece;
      }
    } else {
      checkable = true;
      isChecked = audioState.getRoute() == CallAudioState.ROUTE_SPEAKER;
      label = R.string.incall_label_speaker;
      icon =
          iconSize == IconSize.SIZE_36_DP
              ? R.drawable.quantum_ic_volume_up_white_36
              : R.drawable.quantum_ic_volume_up_white_24;
      contentDescription = R.string.incall_content_description_speaker;
    }
  }
}
