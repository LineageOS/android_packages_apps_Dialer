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

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.ColorInt;
import android.support.annotation.Nullable;
import android.support.v4.graphics.ColorUtils;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import com.android.contacts.common.util.MaterialColorMapUtils;
import com.android.contacts.common.util.MaterialColorMapUtils.MaterialPalette;
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

  /**
   * If there is no actual call currently in the call list, this will be used as a fallback to
   * determine the theme color for InCallUI.
   */
  @Nullable private PhoneAccountHandle pendingPhoneAccountHandle;

  public ThemeColorManager(MaterialColorMapUtils colorMap) {
    this.colorMap = colorMap;
  }

  public void setPendingPhoneAccountHandle(@Nullable PhoneAccountHandle pendingPhoneAccountHandle) {
    this.pendingPhoneAccountHandle = pendingPhoneAccountHandle;
  }

  public void onForegroundCallChanged(Context context, @Nullable DialerCall newForegroundCall) {
    if (newForegroundCall == null) {
      updateThemeColors(context, getHighlightColor(context, pendingPhoneAccountHandle), false);
    } else {
      updateThemeColors(
          context,
          getHighlightColor(context, newForegroundCall.getAccountHandle()),
          newForegroundCall.isSpam());
    }
  }

  private void updateThemeColors(Context context, @ColorInt int highlightColor, boolean isSpam) {
    MaterialPalette palette;
    if (isSpam) {
      palette =
          colorMap.calculatePrimaryAndSecondaryColor(R.color.incall_call_spam_background_color);
      backgroundColorTop = context.getColor(R.color.incall_background_gradient_spam_top);
      backgroundColorMiddle = context.getColor(R.color.incall_background_gradient_spam_middle);
      backgroundColorBottom = context.getColor(R.color.incall_background_gradient_spam_bottom);
      backgroundColorSolid = context.getColor(R.color.incall_background_multiwindow_spam);
    } else {
      palette = colorMap.calculatePrimaryAndSecondaryColor(highlightColor);
      backgroundColorTop = context.getColor(R.color.incall_background_gradient_top);
      backgroundColorMiddle = context.getColor(R.color.incall_background_gradient_middle);
      backgroundColorBottom = context.getColor(R.color.incall_background_gradient_bottom);
      backgroundColorSolid = context.getColor(R.color.incall_background_multiwindow);
      if (highlightColor != PhoneAccount.NO_HIGHLIGHT_COLOR) {
        // The default background gradient has a subtle alpha. We grab that alpha and apply it to
        // the phone account color.
        backgroundColorTop = applyAlpha(palette.mPrimaryColor, backgroundColorTop);
        backgroundColorMiddle = applyAlpha(palette.mPrimaryColor, backgroundColorMiddle);
        backgroundColorBottom = applyAlpha(palette.mPrimaryColor, backgroundColorBottom);
        backgroundColorSolid = applyAlpha(palette.mPrimaryColor, backgroundColorSolid);
      }
    }

    primaryColor = palette.mPrimaryColor;
    secondaryColor = palette.mSecondaryColor;
  }

  @ColorInt
  private int getHighlightColor(Context context, @Nullable PhoneAccountHandle handle) {
    if (handle != null) {
      PhoneAccount account = context.getSystemService(TelecomManager.class).getPhoneAccount(handle);
      if (account != null) {
        return account.getHighlightColor();
      }
    }
    return PhoneAccount.NO_HIGHLIGHT_COLOR;
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

  @ColorInt
  private static int applyAlpha(@ColorInt int color, @ColorInt int sourceColorWithAlpha) {
    return ColorUtils.setAlphaComponent(color, Color.alpha(sourceColorWithAlpha));
  }
}
