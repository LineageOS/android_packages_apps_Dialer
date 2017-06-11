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
 * limitations under the License
 */

package com.android.incallui.answer.impl;

import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;

/** Draws a pill-shaped background */
public class PillDrawable extends GradientDrawable {

  public PillDrawable() {
    super();
    setShape(RECTANGLE);
  }

  @Override
  protected void onBoundsChange(Rect r) {
    super.onBoundsChange(r);
    setCornerRadius(r.height() / 2);
  }

  @Override
  public void setShape(int shape) {
    if (shape != GradientDrawable.RECTANGLE) {
      throw new UnsupportedOperationException("PillDrawable must be a rectangle");
    }
    super.setShape(shape);
  }
}
