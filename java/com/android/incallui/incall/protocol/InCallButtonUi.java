/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.incallui.incall.protocol;

import android.support.v4.app.Fragment;
import android.telecom.CallAudioState;

/** Interface for the call button UI. */
public interface InCallButtonUi {

  void showButton(@InCallButtonIds int buttonId, boolean show);

  void enableButton(@InCallButtonIds int buttonId, boolean enable);

  void setEnabled(boolean on);

  void setHold(boolean on);

  void setCameraSwitched(boolean isBackFacingCamera);

  void setVideoPaused(boolean isPaused);

  void setAudioState(CallAudioState audioState);

  /**
   * Once showButton() has been called on each of the individual buttons in the UI, call this to
   * configure the overflow menu appropriately.
   */
  void updateButtonStates();

  void updateInCallButtonUiColors();

  Fragment getInCallButtonUiFragment();

  void showAudioRouteSelector();
}
