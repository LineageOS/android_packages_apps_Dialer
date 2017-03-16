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
 * limitations under the License.
 */

package com.android.incallui.incall.impl;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.support.v4.view.ViewPager;
import android.support.v4.view.animation.FastOutSlowInInterpolator;

/**
 * An animation that controls the fake drag of a {@link ViewPager}. See {@link
 * ViewPager#fakeDragBy(float)} for more details.
 */
public class FakeDragAnimation implements AnimatorUpdateListener {

  /** The view to animate. */
  private final ViewPager pager;

  private final ValueAnimator animator;
  private int oldDragPosition;

  public FakeDragAnimation(ViewPager pager) {
    this.pager = pager;
    animator = ValueAnimator.ofInt(0, pager.getWidth());
    animator.addUpdateListener(this);
    animator.setInterpolator(new FastOutSlowInInterpolator());
    animator.setDuration(600);
  }

  public void start() {
    animator.start();
  }

  @Override
  public void onAnimationUpdate(ValueAnimator animation) {
    if (!pager.isFakeDragging()) {
      pager.beginFakeDrag();
    }
    int dragPosition = (Integer) animation.getAnimatedValue();
    int dragOffset = dragPosition - oldDragPosition;
    oldDragPosition = dragPosition;
    pager.fakeDragBy(-dragOffset);

    if (animation.getAnimatedFraction() == 1) {
      pager.endFakeDrag();
    }
  }
}
