/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.contacts.common.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageView;

/**
 * Custom {@link AppCompatImageView} that improves layouting performance.
 *
 * <p>This improves the performance by not passing requestLayout() to its parent, taking advantage
 * of knowing that image size won't change once set.
 */
public class LayoutSuppressingImageView extends AppCompatImageView {

  public LayoutSuppressingImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @SuppressLint("MissingSuperCall")
  @Override
  public void requestLayout() {
    forceLayout();
  }
}
