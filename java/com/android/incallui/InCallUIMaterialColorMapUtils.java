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

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.telecom.PhoneAccount;
import com.android.contacts.common.util.MaterialColorMapUtils;

public class InCallUIMaterialColorMapUtils extends MaterialColorMapUtils {

  private final TypedArray mPrimaryColors;
  private final TypedArray mSecondaryColors;
  private final Resources mResources;

  public InCallUIMaterialColorMapUtils(Resources resources) {
    super(resources);
    mPrimaryColors = resources.obtainTypedArray(R.array.background_colors);
    mSecondaryColors = resources.obtainTypedArray(R.array.background_colors_dark);
    mResources = resources;
  }

  /**
   * {@link Resources#getColor(int) used for compatibility
   */
  @SuppressWarnings("deprecation")
  public static MaterialPalette getDefaultPrimaryAndSecondaryColors(Resources resources) {
    final int primaryColor = resources.getColor(R.color.dialer_theme_color);
    final int secondaryColor = resources.getColor(R.color.dialer_theme_color_dark);
    return new MaterialPalette(primaryColor, secondaryColor);
  }

  /**
   * Currently the InCallUI color will only vary by SIM color which is a list of colors defined in
   * the background_colors array, so first search the list for the matching color and fall back to
   * the closest matching color if an exact match does not exist.
   */
  @Override
  public MaterialPalette calculatePrimaryAndSecondaryColor(int color) {
    if (color == PhoneAccount.NO_HIGHLIGHT_COLOR) {
      return getDefaultPrimaryAndSecondaryColors(mResources);
    }

    for (int i = 0; i < mPrimaryColors.length(); i++) {
      if (mPrimaryColors.getColor(i, 0) == color) {
        return new MaterialPalette(mPrimaryColors.getColor(i, 0), mSecondaryColors.getColor(i, 0));
      }
    }

    // The color isn't in the list, so use the superclass to find an approximate color.
    return super.calculatePrimaryAndSecondaryColor(color);
  }
}
