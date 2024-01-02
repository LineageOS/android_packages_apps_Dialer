/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatEditText;

import com.android.dialer.util.ViewUtil;

/** EditText which resizes dynamically with respect to text length. */
public class ResizingTextEditText extends AppCompatEditText {

  private final int originalTextSize;
  private final int minTextSize;

  public ResizingTextEditText(Context context, AttributeSet attrs) {
    super(context, attrs);
    originalTextSize = (int) getTextSize();
    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ResizingText);
    minTextSize =
        (int) a.getDimension(R.styleable.ResizingText_resizing_text_min_size, originalTextSize);
    a.recycle();
  }

  @Override
  protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
    super.onTextChanged(text, start, lengthBefore, lengthAfter);
    ViewUtil.resizeText(this, originalTextSize, minTextSize);
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    ViewUtil.resizeText(this, originalTextSize, minTextSize);
  }
}
