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
 * limitations under the License
 */

package com.android.dialer.theme.base;

import android.content.Context;
import android.support.annotation.ColorInt;
import android.support.annotation.IntDef;
import android.support.annotation.StyleRes;
import android.view.LayoutInflater;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Interface for theme. */
public interface Theme {

  /** IntDef for the different themes Dialer supports. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({UNKNOWN, LIGHT, DARK, LIGHT_M2})
  @interface Type {}

  int UNKNOWN = 0;
  int LIGHT = 1;
  int DARK = 2;
  int LIGHT_M2 = 3;

  @Type
  int getTheme();

  @StyleRes
  int getApplicationThemeRes();

  Context getThemedContext(Context context);

  LayoutInflater getThemedLayoutInflator(LayoutInflater inflater);

  @ColorInt
  int getColorIcon();

  @ColorInt
  int getColorIconSecondary();

  @ColorInt
  int getColorPrimary();

  @ColorInt
  int getColorPrimaryDark();

  @ColorInt
  int getColorAccent();

  @ColorInt
  int getTextColorSecondary();

  @ColorInt
  int getTextColorPrimary();

  @ColorInt
  int getColorTextOnUnthemedDarkBackground();

  @ColorInt
  int getColorIconOnUnthemedDarkBackground();
}
