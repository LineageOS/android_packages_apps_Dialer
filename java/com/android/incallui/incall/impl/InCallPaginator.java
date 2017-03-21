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
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.support.annotation.VisibleForTesting;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.AttributeSet;
import android.view.View;
import com.android.dialer.common.Assert;

/**
 * This is the view class for incall paginator visible when a user has EC data attached to their
 * call. It contains animation methods when the swipe gesture is performed.
 */
public class InCallPaginator extends View implements OnPageChangeListener {

  private int dotRadius;
  private int dotsSeparation;

  private Paint activeDotPaintPortrait;
  private Paint inactiveDotPaintPortrait;

  private Path inactiveDotPath;
  private ValueAnimator transitionAnimator;
  private boolean useModeSwitchTransition;

  private float progress;
  private boolean toFirstPage;
  private boolean pageChanged;

  public InCallPaginator(Context context) {
    super(context);
    init(context);
  }

  public InCallPaginator(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  private void init(Context context) {
    dotRadius = getResources().getDimensionPixelSize(R.dimen.paginator_dot_radius);
    dotsSeparation = getResources().getDimensionPixelSize(R.dimen.paginator_dots_separation);

    int activeDotColor = context.getColor(R.color.paginator_dot);
    int inactiveDotColor = context.getColor(R.color.paginator_path);
    activeDotPaintPortrait = new Paint(Paint.ANTI_ALIAS_FLAG);
    activeDotPaintPortrait.setColor(activeDotColor);
    inactiveDotPaintPortrait = new Paint(Paint.ANTI_ALIAS_FLAG);
    inactiveDotPaintPortrait.setColor(inactiveDotColor);

    inactiveDotPath = new Path();
    transitionAnimator = ValueAnimator.ofFloat(0f, 1f);
    transitionAnimator.setInterpolator(null);
    transitionAnimator.setCurrentFraction(0f);
    transitionAnimator.addUpdateListener(animation -> invalidate());
  }

  @VisibleForTesting
  public void setProgress(float progress, boolean toFirstPage) {
    this.progress = progress;
    this.toFirstPage = toFirstPage;

    // Ensure the dot transition keeps up with the swipe progress.
    if (transitionAnimator.isStarted() && progress > transitionAnimator.getAnimatedFraction()) {
      transitionAnimator.setCurrentFraction(progress);
    }

    invalidate();
  }

  private void startTransition() {
    if (transitionAnimator.getAnimatedFraction() < 1f) {
      transitionAnimator.setCurrentFraction(progress);
      useModeSwitchTransition = false;
      transitionAnimator.cancel();
      transitionAnimator.start();
    }
  }

  private void endTransition(boolean snapBack) {
    if (transitionAnimator.getAnimatedFraction() > 0f) {
      useModeSwitchTransition = !snapBack;
      transitionAnimator.cancel();
      transitionAnimator.reverse();
    }
  }

  @Override
  public void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    int centerX = getWidth() / 2;
    int centerY = getHeight() / 2;

    float transitionFraction = (float) transitionAnimator.getAnimatedValue();

    // Draw the inactive "dots".
    inactiveDotPath.reset();
    if (useModeSwitchTransition) {
      float trackWidth = 2 * dotRadius + transitionFraction * (2 * dotRadius + dotsSeparation);
      float indicatorRadius = dotRadius * (1f - 2f * Math.min(transitionFraction, 0.5f));
      float indicatorOffset = dotRadius + dotsSeparation / 2;
      if (toFirstPage) {
        float trackLeft = centerX - indicatorOffset - dotRadius;
        inactiveDotPath.addRoundRect(
            trackLeft,
            centerY - dotRadius,
            trackLeft + trackWidth,
            centerY + dotRadius,
            dotRadius,
            dotRadius,
            Path.Direction.CW);
        inactiveDotPath.addCircle(
            centerX + indicatorOffset, centerY, indicatorRadius, Path.Direction.CW);
      } else {
        float trackRight = centerX + indicatorOffset + dotRadius;
        inactiveDotPath.addRoundRect(
            trackRight - trackWidth,
            centerY - dotRadius,
            trackRight,
            centerY + dotRadius,
            dotRadius,
            dotRadius,
            Path.Direction.CW);
        inactiveDotPath.addCircle(
            centerX - indicatorOffset, centerY, indicatorRadius, Path.Direction.CW);
      }
    } else {
      float centerOffset = dotsSeparation / 2f;
      float innerOffset = centerOffset - transitionFraction * (dotRadius + centerOffset);
      float outerOffset = 2f * dotRadius + centerOffset;
      inactiveDotPath.addRoundRect(
          centerX - outerOffset,
          centerY - dotRadius,
          centerX - innerOffset,
          centerY + dotRadius,
          dotRadius,
          dotRadius,
          Path.Direction.CW);
      inactiveDotPath.addRoundRect(
          centerX + innerOffset,
          centerY - dotRadius,
          centerX + outerOffset,
          centerY + dotRadius,
          dotRadius,
          dotRadius,
          Path.Direction.CW);
    }
    Paint inactivePaint = inactiveDotPaintPortrait;
    canvas.drawPath(inactiveDotPath, inactivePaint);

    // Draw the white active dot.
    float activeDotOffset =
        (toFirstPage ? 1f - 2f * progress : 2f * progress - 1f) * (dotRadius + dotsSeparation / 2);
    Paint activePaint = activeDotPaintPortrait;
    canvas.drawCircle(centerX + activeDotOffset, centerY, dotRadius, activePaint);
  }

  public void setupWithViewPager(ViewPager pager) {
    Assert.checkArgument(pager.getAdapter().getCount() == 2, "Invalid page count.");
    pager.addOnPageChangeListener(this);
  }

  @Override
  public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    setProgress(positionOffset, position != 0);
  }

  @Override
  public void onPageSelected(int position) {
    pageChanged = true;
  }

  @Override
  public void onPageScrollStateChanged(int state) {
    switch (state) {
      case ViewPager.SCROLL_STATE_IDLE:
        endTransition(!pageChanged);
        pageChanged = false;
        break;
      case ViewPager.SCROLL_STATE_DRAGGING:
        startTransition();
        break;
      case ViewPager.SCROLL_STATE_SETTLING:
      default:
        break;
    }
  }
}
