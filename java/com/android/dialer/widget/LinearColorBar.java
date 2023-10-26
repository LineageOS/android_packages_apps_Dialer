/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2013 Android Open Kang Project
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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.android.dialer.R;

public class LinearColorBar extends LinearLayout {
  private float mFirstRatio;
  private float mSecondRatio;
  private float mThirdRatio;
  private float mFourthRatio;

  private int mBackgroundColor;
  private int mBlueColor;
  private int mGreenColor;
  private int mRedColor;
  private int mOrangeColor;

  final Rect mRect = new Rect();
  final Paint mPaint = new Paint();
  final Path mClipPath = new Path();

  int mLineWidth;

  public LinearColorBar(Context context, AttributeSet attrs) {
    super(context, attrs);
    setWillNotDraw(false);

    TypedArray a = context.obtainStyledAttributes(
            attrs, R.styleable.LinearColorBar, 0, 0);
    int n = a.getIndexCount();

    for (int i = 0; i < n; i++) {
      int attr = a.getIndex(i);

      if (attr == R.styleable.LinearColorBar_backgroundColor) {
        mBackgroundColor = a.getColor(attr, 0);
      } else if (attr == R.styleable.LinearColorBar_redColor) {
        mRedColor = a.getColor(attr, 0);
      } else if (attr == R.styleable.LinearColorBar_greenColor) {
        mGreenColor = a.getColor(attr, 0);
      } else if (attr == R.styleable.LinearColorBar_blueColor) {
        mBlueColor = a.getColor(attr, 0);
      } else if (attr == R.styleable.LinearColorBar_orangeColor) {
        mOrangeColor = a.getColor(attr, 0);
      }
    }

    a.recycle();

    mPaint.setStyle(Paint.Style.FILL);
  }

  public void setRatios(float blue, float green, float red, float orange) {
    mFirstRatio = blue;
    mSecondRatio = green;
    mThirdRatio = red;
    mFourthRatio = orange;
    invalidate();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);

    int off = Math.max(0, getPaddingTop() - getPaddingBottom());
    mRect.top = off;
    mRect.bottom = getHeight();

    mClipPath.reset();
    mClipPath.addRoundRect(0, 0, w, h, h / 2, h / 2, Path.Direction.CW);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    int width = getWidth();

    int left = 0;

    int right = left + (int) (width * mFirstRatio);
    int right2 = right + (int) (width * mSecondRatio);
    int right3 = right2 + (int) (width * mThirdRatio);
    int right4 = right3 + (int) (width * mFourthRatio);

    int saveCount = canvas.save();
    canvas.clipPath(mClipPath);

    if (left < right) {
      mRect.left = left;
      mRect.right = right;
      mPaint.setColor(mBlueColor);
      canvas.drawRect(mRect, mPaint);
      width -= (right - left);
      left = right;
    }

    right = right2;

    if (left < right) {
      mRect.left = left;
      mRect.right = right;
      mPaint.setColor(mGreenColor);
      canvas.drawRect(mRect, mPaint);
      width -= (right - left);
      left = right;
    }

    right = right3;

    if (left < right) {
      mRect.left = left;
      mRect.right = right;
      mPaint.setColor(mRedColor);
      canvas.drawRect(mRect, mPaint);
      width -= (right - left);
      left = right;
    }

    right = right4;

    if (left < right) {
      mRect.left = left;
      mRect.right = right;
      mPaint.setColor(mOrangeColor);
      canvas.drawRect(mRect, mPaint);
      width -= (right - left);
      left = right;
    }

    right = left + width;
    if (left < right) {
      mRect.left = left;
      mRect.right = right;
      mPaint.setColor(mBackgroundColor);
      canvas.drawRect(mRect, mPaint);
    }

    canvas.restoreToCount(saveCount);
  }
}
