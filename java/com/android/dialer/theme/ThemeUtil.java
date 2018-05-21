/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.theme;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.ColorInt;
import android.support.annotation.StyleRes;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import com.android.dialer.common.Assert;

/** Utility for fetching */
@SuppressWarnings("unused")
public class ThemeUtil {

  private static int theme = -1;
  private static int colorIcon = -1;
  private static int colorPrimary = -1;
  private static int colorPrimaryDark = -1;
  private static int colorAccent = -1;
  private static int textColorPrimary = -1;
  private static int textColorSecondary = -1;
  private static int textColorPrimaryInverse = -1;
  private static int textColorHint = -1;
  private static int colorBackground = -1;
  private static int colorBackgroundFloating = -1;

  public static void initializeTheme(Context context) {
    // TODO(a bug): add share prefs check to configure this
    theme = R.style.Dialer_ThemeBase_NoActionBar;
    context = context.getApplicationContext();
    context.setTheme(theme);
    TypedArray array =
        context
            .getTheme()
            .obtainStyledAttributes(
                theme,
                new int[] {
                  android.R.attr.colorPrimary,
                  android.R.attr.colorPrimaryDark,
                  android.R.attr.colorAccent,
                  android.R.attr.textColorPrimary,
                  android.R.attr.textColorSecondary,
                  android.R.attr.textColorPrimaryInverse,
                  android.R.attr.textColorHint,
                  android.R.attr.colorBackground,
                  android.R.attr.colorBackgroundFloating,
                  R.attr.colorIcon,
                });
    colorPrimary = array.getColor(/* index= */ 0, /* defValue= */ -1);
    colorPrimaryDark = array.getColor(/* index= */ 1, /* defValue= */ -1);
    colorAccent = array.getColor(/* index= */ 2, /* defValue= */ -1);
    textColorPrimary = array.getColor(/* index= */ 3, /* defValue= */ -1);
    textColorSecondary = array.getColor(/* index= */ 4, /* defValue= */ -1);
    textColorPrimaryInverse = array.getColor(/* index= */ 5, /* defValue= */ -1);
    textColorHint = array.getColor(/* index= */ 6, /* defValue= */ -1);
    colorBackground = array.getColor(/* index= */ 7, /* defValue= */ -1);
    colorBackgroundFloating = array.getColor(/* index= */ 8, /* defValue= */ -1);
    colorIcon = array.getColor(/* index= */ 9, /* defValue= */ -1);
    array.recycle();
  }

  public static @ColorInt int getColorIcon() {
    Assert.checkArgument(colorIcon != -1);
    return colorIcon;
  }

  public static @ColorInt int getColorPrimary() {
    Assert.checkArgument(colorPrimary != -1);
    return colorPrimary;
  }

  public static @ColorInt int getColorAccent() {
    Assert.checkArgument(colorAccent != -1);
    return colorAccent;
  }

  public static @ColorInt int getTextColorSecondary() {
    Assert.checkArgument(textColorSecondary != -1);
    return textColorSecondary;
  }

  public static @StyleRes int getTheme() {
    Assert.checkArgument(theme != -1);
    return theme;
  }

  public static Context getThemedContext(Context context) {
    return new ContextThemeWrapper(context, getTheme());
  }

  public static LayoutInflater getThemedLayoutInflator(LayoutInflater inflater) {
    return inflater.cloneInContext(getThemedContext(inflater.getContext()));
  }
}
