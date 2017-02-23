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

import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.accessibility.AccessibilityEvent;

/** Interface for the call card module. */
public interface InCallScreen {

  void setPrimary(@NonNull PrimaryInfo primaryInfo);

  void setSecondary(@NonNull SecondaryInfo secondaryInfo);

  void setCallState(@NonNull PrimaryCallState primaryCallState);

  void setEndCallButtonEnabled(boolean enabled, boolean animate);

  void showManageConferenceCallButton(boolean visible);

  boolean isManageConferenceVisible();

  void dispatchPopulateAccessibilityEvent(AccessibilityEvent event);

  void showNoteSentToast();

  void updateInCallScreenColors();

  void onInCallScreenDialpadVisibilityChange(boolean isShowing);

  int getAnswerAndDialpadContainerResourceId();

  void showLocationUi(Fragment locationUi);

  boolean isShowingLocationUi();

  Fragment getInCallScreenFragment();
}
