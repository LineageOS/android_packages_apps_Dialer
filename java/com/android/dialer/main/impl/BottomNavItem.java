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

package com.android.dialer.main.impl;

import android.content.Context;
import android.content.res.ColorStateList;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Navigation item in a bottom nav. */
final class BottomNavItem extends LinearLayout {

  private ImageView image;
  private TextView text;

  public BottomNavItem(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    image = findViewById(R.id.bottom_nav_item_image);
    text = findViewById(R.id.bottom_nav_item_text);
  }

  @Override
  public void setSelected(boolean selected) {
    super.setSelected(selected);
    int colorId = selected ? R.color.bottom_nav_icon_selected : R.color.bottom_nav_icon_deselected;
    int color = getContext().getColor(colorId);
    image.setImageTintList(ColorStateList.valueOf(color));
    text.setTextColor(color);
  }

  void setup(@StringRes int stringRes, @DrawableRes int drawableRes) {
    text.setText(stringRes);
    image.setImageResource(drawableRes);
  }
}
