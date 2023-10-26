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

package com.android.incallui;

import static android.telecom.PhoneAccount.NO_HIGHLIGHT_COLOR;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import com.android.contacts.common.util.MaterialColorMapUtils;
import com.android.contacts.common.util.MaterialColorMapUtils.MaterialPalette;
import com.android.dialer.R;
import com.android.incallui.call.DialerCall;

/**
 * Calculates the background color for the in call window. The background color is based on the SIM
 * and spam status.
 */
public class ThemeColorManager {
  private final MaterialColorMapUtils colorMap;
  @ColorInt private int primaryColor;
  @ColorInt private int secondaryColor;
  @ColorInt private int backgroundColorTop;
  @ColorInt private int backgroundColorMiddle;
  @ColorInt private int backgroundColorBottom;
  @ColorInt private int backgroundColorSolid;

  public ThemeColorManager(MaterialColorMapUtils colorMap) {
    this.colorMap = colorMap;
  }

  public void onForegroundCallChanged(Context context, @Nullable DialerCall newForegroundCall) {
    if (newForegroundCall == null) {
      updateThemeColors(context, false);
    } else {
      updateThemeColors(context, newForegroundCall.isSpam());
    }
  }

  private void updateThemeColors(Context context, boolean isSpam) {
    MaterialPalette palette;
    if (isSpam) {
      palette =
          colorMap.calculatePrimaryAndSecondaryColor(R.color.incall_call_spam_background_color);
      backgroundColorTop = context.getColor(R.color.incall_background_gradient_spam_top);
      backgroundColorMiddle = context.getColor(R.color.incall_background_gradient_spam_middle);
      backgroundColorBottom = context.getColor(R.color.incall_background_gradient_spam_bottom);
      backgroundColorSolid = context.getColor(R.color.incall_background_multiwindow_spam);
    } else {
      palette = colorMap.calculatePrimaryAndSecondaryColor(NO_HIGHLIGHT_COLOR);
      backgroundColorTop = context.getColor(R.color.incall_background_gradient_top);
      backgroundColorMiddle = context.getColor(R.color.incall_background_gradient_middle);
      backgroundColorBottom = context.getColor(R.color.incall_background_gradient_bottom);
      backgroundColorSolid = context.getColor(R.color.incall_background_multiwindow);
    }

    primaryColor = palette.mPrimaryColor;
    secondaryColor = palette.mSecondaryColor;
  }

  @ColorInt
  public int getPrimaryColor() {
    return primaryColor;
  }

  @ColorInt
  public int getSecondaryColor() {
    return secondaryColor;
  }

  @ColorInt
  public int getBackgroundColorTop() {
    return backgroundColorTop;
  }

  @ColorInt
  public int getBackgroundColorMiddle() {
    return backgroundColorMiddle;
  }

  @ColorInt
  public int getBackgroundColorBottom() {
    return backgroundColorBottom;
  }

  @ColorInt
  public int getBackgroundColorSolid() {
    return backgroundColorSolid;
  }
}
