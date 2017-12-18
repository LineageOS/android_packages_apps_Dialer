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

package com.android.newbubble;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Outline;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewOutlineProvider;

/**
 * A {@link ViewOutlineProvider} that provides an outline that interpolates between two radii and
 * two {@link Rect}s.
 *
 * <p>An example usage of this provider is an outline that starts out as a circle and ends as a
 * rounded rectangle.
 */
public class RoundedRectRevealOutlineProvider extends ViewOutlineProvider {
  private final float mStartRadius;
  private final float mEndRadius;

  private final Rect mStartRect;
  private final Rect mEndRect;

  private final Rect mOutline;
  private float mOutlineRadius;

  public RoundedRectRevealOutlineProvider(
      float startRadius, float endRadius, Rect startRect, Rect endRect) {
    mStartRadius = startRadius;
    mEndRadius = endRadius;
    mStartRect = startRect;
    mEndRect = endRect;

    mOutline = new Rect();
  }

  @Override
  public void getOutline(View v, Outline outline) {
    outline.setRoundRect(mOutline, mOutlineRadius);
  }

  /** Sets the progress, from 0 to 1, of the reveal animation. */
  public void setProgress(float progress) {
    mOutlineRadius = (1 - progress) * mStartRadius + progress * mEndRadius;

    mOutline.left = (int) ((1 - progress) * mStartRect.left + progress * mEndRect.left);
    mOutline.top = (int) ((1 - progress) * mStartRect.top + progress * mEndRect.top);
    mOutline.right = (int) ((1 - progress) * mStartRect.right + progress * mEndRect.right);
    mOutline.bottom = (int) ((1 - progress) * mStartRect.bottom + progress * mEndRect.bottom);
  }

  ValueAnimator createRevealAnimator(final View revealView, boolean isReversed) {
    ValueAnimator valueAnimator =
        isReversed ? ValueAnimator.ofFloat(1f, 0f) : ValueAnimator.ofFloat(0f, 1f);

    valueAnimator.addListener(
        new AnimatorListenerAdapter() {
          private boolean mWasCanceled = false;

          @Override
          public void onAnimationStart(Animator animation) {
            revealView.setOutlineProvider(RoundedRectRevealOutlineProvider.this);
            revealView.setClipToOutline(true);
          }

          @Override
          public void onAnimationCancel(Animator animation) {
            mWasCanceled = true;
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            if (!mWasCanceled) {
              revealView.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
              revealView.setClipToOutline(false);
            }
          }
        });

    valueAnimator.addUpdateListener(
        (currentValueAnimator) -> {
          float progress = (Float) currentValueAnimator.getAnimatedValue();
          setProgress(progress);
          revealView.invalidateOutline();
        });
    return valueAnimator;
  }
}
