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

package com.android.incallui.incall.protocol;

import android.content.Context;
import android.os.Bundle;
import android.telecom.CallAudioState;

/** Callbacks from the module out to the container. */
public interface InCallButtonUiDelegate {

  void onInCallButtonUiReady(InCallButtonUi inCallButtonUi);

  void onInCallButtonUiUnready();

  void onSaveInstanceState(Bundle outState);

  void onRestoreInstanceState(Bundle savedInstanceState);

  void refreshMuteState();

  void addCallClicked();

  void muteClicked(boolean checked, boolean clickedByUser);

  void mergeClicked();

  void holdClicked(boolean checked);

  void swapClicked();

  void showDialpadClicked(boolean checked);

  void changeToVideoClicked();

  void switchCameraClicked(boolean useFrontFacingCamera);

  void toggleCameraClicked();

  void pauseVideoClicked(boolean pause);

  void toggleSpeakerphone();

  CallAudioState getCurrentAudioState();

  void setAudioRoute(int route);

  void onEndCallClicked();

  void showAudioRouteSelector();

  Context getContext();
}
