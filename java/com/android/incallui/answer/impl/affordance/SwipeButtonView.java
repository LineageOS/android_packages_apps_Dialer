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

package com.android.incallui.answer.impl.affordance;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import com.android.incallui.answer.impl.utils.FlingAnimationUtils;
import com.android.incallui.answer.impl.utils.Interpolators;

/** Button that allows swiping to trigger */
public class SwipeButtonView extends ImageView {

  private static final long CIRCLE_APPEAR_DURATION = 80;
  private static final long CIRCLE_DISAPPEAR_MAX_DURATION = 200;
  private static final long NORMAL_ANIMATION_DURATION = 200;
  public static final float MAX_ICON_SCALE_AMOUNT = 1.5f;
  public static final float MIN_ICON_SCALE_AMOUNT = 0.8f;

  private final int minBackgroundRadius;
  private final Paint circlePaint;
  private final int inverseColor;
  private final int normalColor;
  private final ArgbEvaluator colorInterpolator;
  private final FlingAnimationUtils flingAnimationUtils;
  private float circleRadius;
  private int centerX;
  private int centerY;
  private ValueAnimator circleAnimator;
  private ValueAnimator alphaAnimator;
  private ValueAnimator scaleAnimator;
  private float circleStartValue;
  private boolean circleWillBeHidden;
  private int[] tempPoint = new int[2];
  private float tmageScale = 1f;
  private int circleColor;
  private View previewView;
  private float circleStartRadius;
  private float maxCircleSize;
  private Animator previewClipper;
  private float restingAlpha = SwipeButtonHelper.SWIPE_RESTING_ALPHA_AMOUNT;
  private boolean finishing;
  private boolean launchingAffordance;

  private AnimatorListenerAdapter clipEndListener =
      new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
          previewClipper = null;
        }
      };
  private AnimatorListenerAdapter circleEndListener =
      new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
          circleAnimator = null;
        }
      };
  private AnimatorListenerAdapter scaleEndListener =
      new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
          scaleAnimator = null;
        }
      };
  private AnimatorListenerAdapter alphaEndListener =
      new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
          alphaAnimator = null;
        }
      };

  public SwipeButtonView(Context context) {
    this(context, null);
  }

  public SwipeButtonView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public SwipeButtonView(Context context, AttributeSet attrs, int defStyleAttr) {
    this(context, attrs, defStyleAttr, 0);
  }

  public SwipeButtonView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    circlePaint = new Paint();
    circlePaint.setAntiAlias(true);
    circleColor = 0xffffffff;
    circlePaint.setColor(circleColor);

    normalColor = 0xffffffff;
    inverseColor = 0xff000000;
    minBackgroundRadius =
        context
            .getResources()
            .getDimensionPixelSize(R.dimen.answer_affordance_min_background_radius);
    colorInterpolator = new ArgbEvaluator();
    flingAnimationUtils = new FlingAnimationUtils(context, 0.3f);
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    centerX = getWidth() / 2;
    centerY = getHeight() / 2;
    maxCircleSize = getMaxCircleSize();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    drawBackgroundCircle(canvas);
    canvas.save();
    canvas.scale(tmageScale, tmageScale, getWidth() / 2, getHeight() / 2);
    super.onDraw(canvas);
    canvas.restore();
  }

  public void setPreviewView(@Nullable View v) {
    View oldPreviewView = previewView;
    previewView = v;
    if (previewView != null) {
      previewView.setVisibility(launchingAffordance ? oldPreviewView.getVisibility() : INVISIBLE);
    }
  }

  private void updateIconColor() {
    Drawable drawable = getDrawable().mutate();
    float alpha = circleRadius / minBackgroundRadius;
    alpha = Math.min(1.0f, alpha);
    int color = (int) colorInterpolator.evaluate(alpha, normalColor, inverseColor);
    drawable.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
  }

  private void drawBackgroundCircle(Canvas canvas) {
    if (circleRadius > 0 || finishing) {
      updateCircleColor();
      canvas.drawCircle(centerX, centerY, circleRadius, circlePaint);
    }
  }

  private void updateCircleColor() {
    float fraction =
        0.5f
            + 0.5f
                * Math.max(
                    0.0f,
                    Math.min(
                        1.0f, (circleRadius - minBackgroundRadius) / (0.5f * minBackgroundRadius)));
    if (previewView != null && previewView.getVisibility() == VISIBLE) {
      float finishingFraction =
          1 - Math.max(0, circleRadius - circleStartRadius) / (maxCircleSize - circleStartRadius);
      fraction *= finishingFraction;
    }
    int color =
        Color.argb(
            (int) (Color.alpha(circleColor) * fraction),
            Color.red(circleColor),
            Color.green(circleColor),
            Color.blue(circleColor));
    circlePaint.setColor(color);
  }

  public void finishAnimation(float velocity, @Nullable final Runnable mAnimationEndRunnable) {
    cancelAnimator(circleAnimator);
    cancelAnimator(previewClipper);
    finishing = true;
    circleStartRadius = circleRadius;
    final float maxCircleSize = getMaxCircleSize();
    Animator animatorToRadius;
    animatorToRadius = getAnimatorToRadius(maxCircleSize);
    flingAnimationUtils.applyDismissing(
        animatorToRadius, circleRadius, maxCircleSize, velocity, maxCircleSize);
    animatorToRadius.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            if (mAnimationEndRunnable != null) {
              mAnimationEndRunnable.run();
            }
            finishing = false;
            circleRadius = maxCircleSize;
            invalidate();
          }
        });
    animatorToRadius.start();
    setImageAlpha(0, true);
    if (previewView != null) {
      previewView.setVisibility(View.VISIBLE);
      previewClipper =
          ViewAnimationUtils.createCircularReveal(
              previewView, getLeft() + centerX, getTop() + centerY, circleRadius, maxCircleSize);
      flingAnimationUtils.applyDismissing(
          previewClipper, circleRadius, maxCircleSize, velocity, maxCircleSize);
      previewClipper.addListener(clipEndListener);
      previewClipper.start();
    }
  }

  public void instantFinishAnimation() {
    cancelAnimator(previewClipper);
    if (previewView != null) {
      previewView.setClipBounds(null);
      previewView.setVisibility(View.VISIBLE);
    }
    circleRadius = getMaxCircleSize();
    setImageAlpha(0, false);
    invalidate();
  }

  private float getMaxCircleSize() {
    getLocationInWindow(tempPoint);
    float rootWidth = getRootView().getWidth();
    float width = tempPoint[0] + centerX;
    width = Math.max(rootWidth - width, width);
    float height = tempPoint[1] + centerY;
    return (float) Math.hypot(width, height);
  }

  public void setCircleRadius(float circleRadius) {
    setCircleRadius(circleRadius, false, false);
  }

  public void setCircleRadius(float circleRadius, boolean slowAnimation) {
    setCircleRadius(circleRadius, slowAnimation, false);
  }

  public void setCircleRadiusWithoutAnimation(float circleRadius) {
    cancelAnimator(circleAnimator);
    setCircleRadius(circleRadius, false, true);
  }

  private void setCircleRadius(float circleRadius, boolean slowAnimation, boolean noAnimation) {

    // Check if we need a new animation
    boolean radiusHidden =
        (circleAnimator != null && circleWillBeHidden)
            || (circleAnimator == null && this.circleRadius == 0.0f);
    boolean nowHidden = circleRadius == 0.0f;
    boolean radiusNeedsAnimation = (radiusHidden != nowHidden) && !noAnimation;
    if (!radiusNeedsAnimation) {
      if (circleAnimator == null) {
        this.circleRadius = circleRadius;
        updateIconColor();
        invalidate();
        if (nowHidden) {
          if (previewView != null) {
            previewView.setVisibility(View.INVISIBLE);
          }
        }
      } else if (!circleWillBeHidden) {

        // We just update the end value
        float diff = circleRadius - minBackgroundRadius;
        PropertyValuesHolder[] values = circleAnimator.getValues();
        values[0].setFloatValues(circleStartValue + diff, circleRadius);
        circleAnimator.setCurrentPlayTime(circleAnimator.getCurrentPlayTime());
      }
    } else {
      cancelAnimator(circleAnimator);
      cancelAnimator(previewClipper);
      ValueAnimator animator = getAnimatorToRadius(circleRadius);
      Interpolator interpolator =
          circleRadius == 0.0f
              ? Interpolators.FAST_OUT_LINEAR_IN
              : Interpolators.LINEAR_OUT_SLOW_IN;
      animator.setInterpolator(interpolator);
      long duration = 250;
      if (!slowAnimation) {
        float durationFactor =
            Math.abs(this.circleRadius - circleRadius) / (float) minBackgroundRadius;
        duration = (long) (CIRCLE_APPEAR_DURATION * durationFactor);
        duration = Math.min(duration, CIRCLE_DISAPPEAR_MAX_DURATION);
      }
      animator.setDuration(duration);
      animator.start();
      if (previewView != null && previewView.getVisibility() == View.VISIBLE) {
        previewView.setVisibility(View.VISIBLE);
        previewClipper =
            ViewAnimationUtils.createCircularReveal(
                previewView,
                getLeft() + centerX,
                getTop() + centerY,
                this.circleRadius,
                circleRadius);
        previewClipper.setInterpolator(interpolator);
        previewClipper.setDuration(duration);
        previewClipper.addListener(clipEndListener);
        previewClipper.addListener(
            new AnimatorListenerAdapter() {
              @Override
              public void onAnimationEnd(Animator animation) {
                previewView.setVisibility(View.INVISIBLE);
              }
            });
        previewClipper.start();
      }
    }
  }

  private ValueAnimator getAnimatorToRadius(float circleRadius) {
    ValueAnimator animator = ValueAnimator.ofFloat(this.circleRadius, circleRadius);
    circleAnimator = animator;
    circleStartValue = this.circleRadius;
    circleWillBeHidden = circleRadius == 0.0f;
    animator.addUpdateListener(
        new ValueAnimator.AnimatorUpdateListener() {
          @Override
          public void onAnimationUpdate(ValueAnimator animation) {
            SwipeButtonView.this.circleRadius = (float) animation.getAnimatedValue();
            updateIconColor();
            invalidate();
          }
        });
    animator.addListener(circleEndListener);
    return animator;
  }

  private void cancelAnimator(Animator animator) {
    if (animator != null) {
      animator.cancel();
    }
  }

  public void setImageScale(float imageScale, boolean animate) {
    setImageScale(imageScale, animate, -1, null);
  }

  /**
   * Sets the scale of the containing image
   *
   * @param imageScale The new Scale.
   * @param animate Should an animation be performed
   * @param duration If animate, whats the duration? When -1 we take the default duration
   * @param interpolator If animate, whats the interpolator? When null we take the default
   *     interpolator.
   */
  public void setImageScale(
      float imageScale, boolean animate, long duration, @Nullable Interpolator interpolator) {
    cancelAnimator(scaleAnimator);
    if (!animate) {
      tmageScale = imageScale;
      invalidate();
    } else {
      ValueAnimator animator = ValueAnimator.ofFloat(tmageScale, imageScale);
      scaleAnimator = animator;
      animator.addUpdateListener(
          new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
              tmageScale = (float) animation.getAnimatedValue();
              invalidate();
            }
          });
      animator.addListener(scaleEndListener);
      if (interpolator == null) {
        interpolator =
            imageScale == 0.0f
                ? Interpolators.FAST_OUT_LINEAR_IN
                : Interpolators.LINEAR_OUT_SLOW_IN;
      }
      animator.setInterpolator(interpolator);
      if (duration == -1) {
        float durationFactor = Math.abs(tmageScale - imageScale) / (1.0f - MIN_ICON_SCALE_AMOUNT);
        durationFactor = Math.min(1.0f, durationFactor);
        duration = (long) (NORMAL_ANIMATION_DURATION * durationFactor);
      }
      animator.setDuration(duration);
      animator.start();
    }
  }

  public void setRestingAlpha(float alpha) {
    restingAlpha = alpha;

    // TODO: Handle the case an animation is playing.
    setImageAlpha(alpha, false);
  }

  public float getRestingAlpha() {
    return restingAlpha;
  }

  public void setImageAlpha(float alpha, boolean animate) {
    setImageAlpha(alpha, animate, -1, null, null);
  }

  /**
   * Sets the alpha of the containing image
   *
   * @param alpha The new alpha.
   * @param animate Should an animation be performed
   * @param duration If animate, whats the duration? When -1 we take the default duration
   * @param interpolator If animate, whats the interpolator? When null we take the default
   *     interpolator.
   */
  public void setImageAlpha(
      float alpha,
      boolean animate,
      long duration,
      @Nullable Interpolator interpolator,
      @Nullable Runnable runnable) {
    cancelAnimator(alphaAnimator);
    alpha = launchingAffordance ? 0 : alpha;
    int endAlpha = (int) (alpha * 255);
    final Drawable background = getBackground();
    if (!animate) {
      if (background != null) {
        background.mutate().setAlpha(endAlpha);
      }
      setImageAlpha(endAlpha);
    } else {
      int currentAlpha = getImageAlpha();
      ValueAnimator animator = ValueAnimator.ofInt(currentAlpha, endAlpha);
      alphaAnimator = animator;
      animator.addUpdateListener(
          new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
              int alpha = (int) animation.getAnimatedValue();
              if (background != null) {
                background.mutate().setAlpha(alpha);
              }
              setImageAlpha(alpha);
            }
          });
      animator.addListener(alphaEndListener);
      if (interpolator == null) {
        interpolator =
            alpha == 0.0f ? Interpolators.FAST_OUT_LINEAR_IN : Interpolators.LINEAR_OUT_SLOW_IN;
      }
      animator.setInterpolator(interpolator);
      if (duration == -1) {
        float durationFactor = Math.abs(currentAlpha - endAlpha) / 255f;
        durationFactor = Math.min(1.0f, durationFactor);
        duration = (long) (NORMAL_ANIMATION_DURATION * durationFactor);
      }
      animator.setDuration(duration);
      if (runnable != null) {
        animator.addListener(getEndListener(runnable));
      }
      animator.start();
    }
  }

  private Animator.AnimatorListener getEndListener(final Runnable runnable) {
    return new AnimatorListenerAdapter() {
      boolean cancelled;

      @Override
      public void onAnimationCancel(Animator animation) {
        cancelled = true;
      }

      @Override
      public void onAnimationEnd(Animator animation) {
        if (!cancelled) {
          runnable.run();
        }
      }
    };
  }

  public float getCircleRadius() {
    return circleRadius;
  }

  @Override
  public boolean performClick() {
    return isClickable() && super.performClick();
  }

  public void setLaunchingAffordance(boolean launchingAffordance) {
    this.launchingAffordance = launchingAffordance;
  }
}
