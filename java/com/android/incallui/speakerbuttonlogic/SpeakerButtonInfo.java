/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2024 The LineageOS Project
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

import android.telecom.CallAudioState;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.StringRes;

import com.android.dialer.R;
import com.android.incallui.util.BluetoothUtil;

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

  @DrawableRes
  public final int icon;
  @StringRes public final int contentDescription;
  @StringRes
  public final int label;
  public final boolean nonBluetoothMode;
  public final boolean isChecked;
  public String deviceName;

  public SpeakerButtonInfo(CallAudioState audioState) {
    if ((audioState.getSupportedRouteMask() & CallAudioState.ROUTE_BLUETOOTH)
        == CallAudioState.ROUTE_BLUETOOTH) {
      nonBluetoothMode = false;
      isChecked = true;

      if ((audioState.getRoute() & CallAudioState.ROUTE_BLUETOOTH)
          == CallAudioState.ROUTE_BLUETOOTH) {
        icon = R.drawable.quantum_ic_phone_bluetooth_vd_theme_24;
        contentDescription = R.string.incall_content_description_bluetooth;
        label = R.string.audioroute_bluetooth;
        deviceName = BluetoothUtil.getAliasName(audioState.getActiveBluetoothDevice());
      } else if ((audioState.getRoute() & CallAudioState.ROUTE_SPEAKER)
          == CallAudioState.ROUTE_SPEAKER) {
        icon = R.drawable.quantum_ic_volume_up_vd_theme_24;
        contentDescription = R.string.incall_content_description_speaker;
        label = R.string.audioroute_speaker;
      } else if ((audioState.getRoute() & CallAudioState.ROUTE_WIRED_HEADSET)
          == CallAudioState.ROUTE_WIRED_HEADSET) {
        icon = R.drawable.quantum_ic_headset_vd_theme_24;
        contentDescription = R.string.incall_content_description_headset;
        label = R.string.audioroute_headset;
      } else {
        icon = R.drawable.quantum_ic_phone_in_talk_vd_theme_24;
        contentDescription = R.string.incall_content_description_earpiece;
        label = R.string.audioroute_phone;
      }
    } else {
      nonBluetoothMode = true;
      isChecked = audioState.getRoute() == CallAudioState.ROUTE_SPEAKER;
      label = R.string.incall_label_speaker;
      icon = R.drawable.quantum_ic_volume_up_vd_theme_24;
      contentDescription = R.string.incall_content_description_speaker;
    }
  }
}
