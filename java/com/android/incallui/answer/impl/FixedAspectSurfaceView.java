/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.SurfaceView;
import android.view.View;
import com.android.dialer.common.Assert;

/**
 * A SurfaceView that maintains its aspect ratio to be a desired target value.
 *
 * <p>The FixedAspectSurfaceView will not be able to maintain the requested aspect ratio if both the
 * width and the height are exactly determined by the layout. To avoid this, ensure that either the
 * height or the width is adjustable by the view; for example, by setting the layout parameters to
 * be WRAP_CONTENT for the dimension that is best adjusted to maintain the aspect ratio.
 */
public class FixedAspectSurfaceView extends SurfaceView {

  /** Desired width/height ratio */
  private float aspectRatio;

  private final boolean scaleWidth;
  private final boolean scaleHeight;

  public FixedAspectSurfaceView(Context context, AttributeSet attrs) {
    super(context, attrs);

    // Get initial aspect ratio from custom attributes
    TypedArray a =
        context.getTheme().obtainStyledAttributes(attrs, R.styleable.FixedAspectSurfaceView, 0, 0);
    scaleHeight = a.getBoolean(R.styleable.FixedAspectSurfaceView_scaleHeight, false);
    scaleWidth = a.getBoolean(R.styleable.FixedAspectSurfaceView_scaleWidth, false);
    Assert.checkArgument(scaleHeight != scaleWidth, "Must either scale width or height");
    setAspectRatio(a.getFloat(R.styleable.FixedAspectSurfaceView_aspectRatio, 1.f));
    a.recycle();
  }

  /**
   * Set the desired aspect ratio for this view.
   *
   * @param aspect the desired width/height ratio in the current UI orientation. Must be a positive
   *     value.
   */
  public void setAspectRatio(float aspect) {
    Assert.checkArgument(aspect >= 0, "Aspect ratio must be positive");
    aspectRatio = aspect;
    requestLayout();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int height = MeasureSpec.getSize(heightMeasureSpec);

    // Do the scaling
    if (scaleWidth) {
      width = (int) (height * aspectRatio);
    } else if (scaleHeight) {
      height = (int) (width / aspectRatio);
    }

    // Override width/height if needed for EXACTLY and AT_MOST specs
    width = View.resolveSizeAndState(width, widthMeasureSpec, 0);
    height = View.resolveSizeAndState(height, heightMeasureSpec, 0);

    // Finally set the calculated dimensions
    setMeasuredDimension(width, height);
  }
}
