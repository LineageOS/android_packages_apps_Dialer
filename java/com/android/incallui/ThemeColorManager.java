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
import android.support.annotation.ColorInt;
import android.support.annotation.Nullable;
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
      updateThemeColors(getHighlightColor(context, pendingPhoneAccountHandle), false);
    } else {
      updateThemeColors(
          getHighlightColor(context, newForegroundCall.getAccountHandle()),
          newForegroundCall.isSpam());
    }
  }

  private void updateThemeColors(@ColorInt int highlightColor, boolean isSpam) {
    MaterialPalette palette;
    if (isSpam) {
      palette =
          colorMap.calculatePrimaryAndSecondaryColor(R.color.incall_call_spam_background_color);
    } else {
      palette = colorMap.calculatePrimaryAndSecondaryColor(highlightColor);
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
}
