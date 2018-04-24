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

package com.android.dialer.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.ScrollView;

/** {@link ScrollView} with a max height attribute. */
public class MaxHeightScrollView extends ScrollView {

  private final int maxHeight;

  public MaxHeightScrollView(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public MaxHeightScrollView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    this(context, attrs, defStyleAttr, 0);
  }

  public MaxHeightScrollView(
      Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    if (isInEditMode()) {
      maxHeight = -1;
      return;
    }

    TypedArray array =
        context.getTheme().obtainStyledAttributes(attrs, R.styleable.MaxHeightScrollView, 0, 0);
    try {
      maxHeight = array.getDimensionPixelSize(R.styleable.MaxHeightScrollView_maxHeight, -1);
    } finally {
      array.recycle();
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    if (maxHeight < 0) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    } else {
      super.onMeasure(
          widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
    }
  }
}
