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
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.telecom.PhoneAccount;
import com.android.contacts.common.util.MaterialColorMapUtils;
import com.android.dialer.theme.base.ThemeComponent;

public class InCallUIMaterialColorMapUtils extends MaterialColorMapUtils {

  private final TypedArray primaryColors;
  private final TypedArray secondaryColors;
  private final Resources resources;
  private final Context context;

  public InCallUIMaterialColorMapUtils(Context context) {
    super(context.getResources());
    this.resources = context.getResources();
    this.context = context;
    primaryColors = resources.obtainTypedArray(R.array.background_colors);
    secondaryColors = resources.obtainTypedArray(R.array.background_colors_dark);
  }

  /**
   * {@link Resources#getColor(int) used for compatibility
   */
  @SuppressWarnings("deprecation")
  public static MaterialPalette getDefaultPrimaryAndSecondaryColors(Context context) {
    final int primaryColor = ThemeComponent.get(context).theme().getColorPrimary();
    final int secondaryColor = ThemeComponent.get(context).theme().getColorPrimaryDark();
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
      return getDefaultPrimaryAndSecondaryColors(context);
    }

    for (int i = 0; i < primaryColors.length(); i++) {
      if (primaryColors.getColor(i, 0) == color) {
        return new MaterialPalette(primaryColors.getColor(i, 0), secondaryColors.getColor(i, 0));
      }
    }

    // The color isn't in the list, so use the superclass to find an approximate color.
    return super.calculatePrimaryAndSecondaryColor(color);
  }
}
