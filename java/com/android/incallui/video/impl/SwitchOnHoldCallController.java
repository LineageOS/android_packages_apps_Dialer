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

package com.android.incallui.video.impl;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.View.OnClickListener;
import com.android.dialer.common.Assert;
import com.android.incallui.incall.protocol.InCallScreenDelegate;
import com.android.incallui.incall.protocol.SecondaryInfo;
import com.android.incallui.video.protocol.VideoCallScreenDelegate;

/** Manages the swap button and on hold banner. */
public class SwitchOnHoldCallController implements OnClickListener {

  @NonNull private InCallScreenDelegate inCallScreenDelegate;
  @NonNull private VideoCallScreenDelegate videoCallScreenDelegate;

  @NonNull private View switchOnHoldButton;

  @NonNull private View onHoldBanner;

  private boolean isVisible;

  private boolean isEnabled;

  @Nullable private SecondaryInfo secondaryInfo;

  public SwitchOnHoldCallController(
      @NonNull View switchOnHoldButton,
      @NonNull View onHoldBanner,
      @NonNull InCallScreenDelegate inCallScreenDelegate,
      @NonNull VideoCallScreenDelegate videoCallScreenDelegate) {
    this.switchOnHoldButton = Assert.isNotNull(switchOnHoldButton);
    switchOnHoldButton.setOnClickListener(this);
    this.onHoldBanner = Assert.isNotNull(onHoldBanner);
    this.inCallScreenDelegate = Assert.isNotNull(inCallScreenDelegate);
    this.videoCallScreenDelegate = Assert.isNotNull(videoCallScreenDelegate);
  }

  public void setEnabled(boolean isEnabled) {
    this.isEnabled = isEnabled;
    updateButtonState();
  }

  public void setVisible(boolean isVisible) {
    this.isVisible = isVisible;
    updateButtonState();
  }

  public void setOnScreen() {
    isVisible = hasSecondaryInfo();
    updateButtonState();
  }

  public void setSecondaryInfo(@Nullable SecondaryInfo secondaryInfo) {
    this.secondaryInfo = secondaryInfo;
    isVisible = hasSecondaryInfo();
  }

  private boolean hasSecondaryInfo() {
    return secondaryInfo != null && secondaryInfo.shouldShow;
  }

  public void updateButtonState() {
    switchOnHoldButton.setEnabled(isEnabled);
    switchOnHoldButton.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
    onHoldBanner.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
  }

  @Override
  public void onClick(View view) {
    inCallScreenDelegate.onSecondaryInfoClicked();
    videoCallScreenDelegate.resetAutoFullscreenTimer();
  }
}
