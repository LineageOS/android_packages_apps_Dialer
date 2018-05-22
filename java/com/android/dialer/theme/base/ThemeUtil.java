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

package com.android.dialer.theme.base;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.ColorInt;
import android.support.annotation.IntDef;
import android.support.annotation.StyleRes;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import com.android.dialer.common.Assert;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Utility for fetching */
@SuppressWarnings("unused")
public class ThemeUtil {

  /** IntDef for the different themes Dialer supports. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({UNKNOWN, LIGHT, DARK})
  public @interface Theme {}

  public static final int UNKNOWN = 0;
  public static final int LIGHT = 1;
  public static final int DARK = 2;

  private static @Theme int theme = UNKNOWN;

  private static int colorIcon = -1;
  private static int colorIconSecondary = -1;
  private static int colorPrimary = -1;
  private static int colorPrimaryDark = -1;
  private static int colorAccent = -1;
  private static int textColorPrimary = -1;
  private static int textColorSecondary = -1;
  private static int textColorPrimaryInverse = -1;
  private static int textColorHint = -1;
  private static int colorBackground = -1;
  private static int colorBackgroundFloating = -1;
  private static int colorTextOnUnthemedDarkBackground = -1;
  private static int colorIconOnUnthemedDarkBackground = -1;

  public static void initializeTheme(Context context) {
    // TODO(a bug): add share prefs check to configure this
    theme = LIGHT;

    context = context.getApplicationContext();
    context.setTheme(getApplicationThemeRes());
    TypedArray array =
        context
            .getTheme()
            .obtainStyledAttributes(
                getApplicationThemeRes(),
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
                  R.attr.colorIconSecondary,
                  R.attr.colorTextOnUnthemedDarkBackground,
                  R.attr.colorIconOnUnthemedDarkBackground,
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
    colorIconSecondary = array.getColor(/* index= */ 10, /* defValue= */ -1);
    colorTextOnUnthemedDarkBackground = array.getColor(/* index= */ 11, /* defValue= */ -1);
    colorIconOnUnthemedDarkBackground = array.getColor(/* index= */ 12, /* defValue= */ -1);
    array.recycle();
  }

  /**
   * Returns the {@link Theme} that the application is using. Activities should check this value if
   * their custom style needs to customize further based on the application theme.
   */
  public static @Theme int getTheme() {
    Assert.checkArgument(theme != UNKNOWN);
    return theme;
  }

  public static @StyleRes int getApplicationThemeRes() {
    switch (theme) {
      case DARK:
        return R.style.Dialer_Dark_ThemeBase_NoActionBar;
      case LIGHT:
        return R.style.Dialer_ThemeBase_NoActionBar;
      case UNKNOWN:
      default:
        throw Assert.createIllegalStateFailException("Theme hasn't been set yet.");
    }
  }

  public static Context getThemedContext(Context context) {
    return new ContextThemeWrapper(context, getApplicationThemeRes());
  }

  public static LayoutInflater getThemedLayoutInflator(LayoutInflater inflater) {
    return inflater.cloneInContext(getThemedContext(inflater.getContext()));
  }

  public static @ColorInt int getColorIcon() {
    Assert.checkArgument(colorIcon != -1);
    return colorIcon;
  }

  public static @ColorInt int getColorIconSecondary() {
    Assert.checkArgument(colorIconSecondary != -1);
    return colorIconSecondary;
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

  public static @ColorInt int getTextColorPrimary() {
    Assert.checkArgument(textColorPrimary != -1);
    return textColorPrimary;
  }

  public static @ColorInt int getColorTextOnUnthemedDarkBackground() {
    Assert.checkArgument(colorTextOnUnthemedDarkBackground != -1);
    return colorTextOnUnthemedDarkBackground;
  }

  public static @ColorInt int getColorIconOnUnthemedDarkBackground() {
    Assert.checkArgument(colorIconOnUnthemedDarkBackground != -1);
    return colorIconOnUnthemedDarkBackground;
  }
}
