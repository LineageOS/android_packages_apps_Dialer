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
import android.animation.ValueAnimator;
import android.content.Context;
import android.support.annotation.Nullable;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import com.android.incallui.answer.impl.utils.FlingAnimationUtils;
import com.android.incallui.answer.impl.utils.Interpolators;

/** A touch handler of the swipe buttons */
public class SwipeButtonHelper {

  public static final float SWIPE_RESTING_ALPHA_AMOUNT = 0.87f;
  public static final long HINT_PHASE1_DURATION = 200;
  private static final long HINT_PHASE2_DURATION = 350;
  private static final float BACKGROUND_RADIUS_SCALE_FACTOR = 0.25f;
  private static final int HINT_CIRCLE_OPEN_DURATION = 500;

  private final Context context;
  private final Callback callback;

  private FlingAnimationUtils flingAnimationUtils;
  private VelocityTracker velocityTracker;
  private boolean swipingInProgress;
  private float initialTouchX;
  private float initialTouchY;
  private float translation;
  private float translationOnDown;
  private int touchSlop;
  private int minTranslationAmount;
  private int minFlingVelocity;
  private int hintGrowAmount;
  @Nullable private SwipeButtonView leftIcon;
  @Nullable private SwipeButtonView rightIcon;
  private Animator swipeAnimator;
  private int minBackgroundRadius;
  private boolean motionCancelled;
  private int touchTargetSize;
  private View targetedView;
  private boolean touchSlopExeeded;
  private AnimatorListenerAdapter flingEndListener =
      new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
          swipeAnimator = null;
          swipingInProgress = false;
          targetedView = null;
        }
      };

  private class AnimationEndRunnable implements Runnable {
    private final boolean rightPage;

    public AnimationEndRunnable(boolean rightPage) {
      this.rightPage = rightPage;
    }

    @Override
    public void run() {
      callback.onAnimationToSideEnded(rightPage);
    }
  };

  public SwipeButtonHelper(Callback callback, Context context) {
    this.context = context;
    this.callback = callback;
    init();
  }

  public void init() {
    initIcons();
    updateIcon(
        leftIcon,
        0.0f,
        leftIcon != null ? leftIcon.getRestingAlpha() : 0,
        false,
        false,
        true,
        false);
    updateIcon(
        rightIcon,
        0.0f,
        rightIcon != null ? rightIcon.getRestingAlpha() : 0,
        false,
        false,
        true,
        false);
    initDimens();
  }

  private void initDimens() {
    final ViewConfiguration configuration = ViewConfiguration.get(context);
    touchSlop = configuration.getScaledPagingTouchSlop();
    minFlingVelocity = configuration.getScaledMinimumFlingVelocity();
    minTranslationAmount =
        context.getResources().getDimensionPixelSize(R.dimen.answer_min_swipe_amount);
    minBackgroundRadius =
        context
            .getResources()
            .getDimensionPixelSize(R.dimen.answer_affordance_min_background_radius);
    touchTargetSize =
        context.getResources().getDimensionPixelSize(R.dimen.answer_affordance_touch_target_size);
    hintGrowAmount =
        context.getResources().getDimensionPixelSize(R.dimen.hint_grow_amount_sideways);
    flingAnimationUtils = new FlingAnimationUtils(context, 0.4f);
  }

  private void initIcons() {
    leftIcon = callback.getLeftIcon();
    rightIcon = callback.getRightIcon();
    updatePreviews();
  }

  public void updatePreviews() {
    if (leftIcon != null) {
      leftIcon.setPreviewView(callback.getLeftPreview());
    }
    if (rightIcon != null) {
      rightIcon.setPreviewView(callback.getRightPreview());
    }
  }

  public boolean onTouchEvent(MotionEvent event) {
    int action = event.getActionMasked();
    if (motionCancelled && action != MotionEvent.ACTION_DOWN) {
      return false;
    }
    final float y = event.getY();
    final float x = event.getX();

    boolean isUp = false;
    switch (action) {
      case MotionEvent.ACTION_DOWN:
        View targetView = getIconAtPosition(x, y);
        if (targetView == null || (targetedView != null && targetedView != targetView)) {
          motionCancelled = true;
          return false;
        }
        if (targetedView != null) {
          cancelAnimation();
        } else {
          touchSlopExeeded = false;
        }
        startSwiping(targetView);
        initialTouchX = x;
        initialTouchY = y;
        translationOnDown = translation;
        initVelocityTracker();
        trackMovement(event);
        motionCancelled = false;
        break;
      case MotionEvent.ACTION_POINTER_DOWN:
        motionCancelled = true;
        endMotion(true /* forceSnapBack */, x, y);
        break;
      case MotionEvent.ACTION_MOVE:
        trackMovement(event);
        float xDist = x - initialTouchX;
        float yDist = y - initialTouchY;
        float distance = (float) Math.hypot(xDist, yDist);
        if (!touchSlopExeeded && distance > touchSlop) {
          touchSlopExeeded = true;
        }
        if (swipingInProgress) {
          if (targetedView == rightIcon) {
            distance = translationOnDown - distance;
            distance = Math.min(0, distance);
          } else {
            distance = translationOnDown + distance;
            distance = Math.max(0, distance);
          }
          setTranslation(distance, false /* isReset */, false /* animateReset */);
        }
        break;

      case MotionEvent.ACTION_UP:
        isUp = true;
        // fall through
      case MotionEvent.ACTION_CANCEL:
        boolean hintOnTheRight = targetedView == rightIcon;
        trackMovement(event);
        endMotion(!isUp, x, y);
        if (!touchSlopExeeded && isUp) {
          callback.onIconClicked(hintOnTheRight);
        }
        break;
    }
    return true;
  }

  private void startSwiping(View targetView) {
    callback.onSwipingStarted(targetView == rightIcon);
    swipingInProgress = true;
    targetedView = targetView;
  }

  private View getIconAtPosition(float x, float y) {
    if (leftSwipePossible() && isOnIcon(leftIcon, x, y)) {
      return leftIcon;
    }
    if (rightSwipePossible() && isOnIcon(rightIcon, x, y)) {
      return rightIcon;
    }
    return null;
  }

  public boolean isOnAffordanceIcon(float x, float y) {
    return isOnIcon(leftIcon, x, y) || isOnIcon(rightIcon, x, y);
  }

  private boolean isOnIcon(View icon, float x, float y) {
    float iconX = icon.getX() + icon.getWidth() / 2.0f;
    float iconY = icon.getY() + icon.getHeight() / 2.0f;
    double distance = Math.hypot(x - iconX, y - iconY);
    return distance <= touchTargetSize / 2;
  }

  private void endMotion(boolean forceSnapBack, float lastX, float lastY) {
    if (swipingInProgress) {
      flingWithCurrentVelocity(forceSnapBack, lastX, lastY);
    } else {
      targetedView = null;
    }
    if (velocityTracker != null) {
      velocityTracker.recycle();
      velocityTracker = null;
    }
  }

  private boolean rightSwipePossible() {
    return rightIcon != null && rightIcon.getVisibility() == View.VISIBLE;
  }

  private boolean leftSwipePossible() {
    return leftIcon != null && leftIcon.getVisibility() == View.VISIBLE;
  }

  public void startHintAnimation(boolean right, @Nullable Runnable onFinishedListener) {
    cancelAnimation();
    startHintAnimationPhase1(right, onFinishedListener);
  }

  private void startHintAnimationPhase1(
      final boolean right, @Nullable final Runnable onFinishedListener) {
    final SwipeButtonView targetView = right ? rightIcon : leftIcon;
    ValueAnimator animator = getAnimatorToRadius(right, hintGrowAmount);
    if (animator == null) {
      if (onFinishedListener != null) {
        onFinishedListener.run();
      }
      return;
    }
    animator.addListener(
        new AnimatorListenerAdapter() {
          private boolean cancelled;

          @Override
          public void onAnimationCancel(Animator animation) {
            cancelled = true;
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            if (cancelled) {
              swipeAnimator = null;
              targetedView = null;
              if (onFinishedListener != null) {
                onFinishedListener.run();
              }
            } else {
              startUnlockHintAnimationPhase2(right, onFinishedListener);
            }
          }
        });
    animator.setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN);
    animator.setDuration(HINT_PHASE1_DURATION);
    animator.start();
    swipeAnimator = animator;
    targetedView = targetView;
  }

  /** Phase 2: Move back. */
  private void startUnlockHintAnimationPhase2(
      boolean right, @Nullable final Runnable onFinishedListener) {
    ValueAnimator animator = getAnimatorToRadius(right, 0);
    if (animator == null) {
      if (onFinishedListener != null) {
        onFinishedListener.run();
      }
      return;
    }
    animator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            swipeAnimator = null;
            targetedView = null;
            if (onFinishedListener != null) {
              onFinishedListener.run();
            }
          }
        });
    animator.setInterpolator(Interpolators.FAST_OUT_LINEAR_IN);
    animator.setDuration(HINT_PHASE2_DURATION);
    animator.setStartDelay(HINT_CIRCLE_OPEN_DURATION);
    animator.start();
    swipeAnimator = animator;
  }

  private ValueAnimator getAnimatorToRadius(final boolean right, int radius) {
    final SwipeButtonView targetView = right ? rightIcon : leftIcon;
    if (targetView == null) {
      return null;
    }
    ValueAnimator animator = ValueAnimator.ofFloat(targetView.getCircleRadius(), radius);
    animator.addUpdateListener(
        new ValueAnimator.AnimatorUpdateListener() {
          @Override
          public void onAnimationUpdate(ValueAnimator animation) {
            float newRadius = (float) animation.getAnimatedValue();
            targetView.setCircleRadiusWithoutAnimation(newRadius);
            float translation = getTranslationFromRadius(newRadius);
            SwipeButtonHelper.this.translation = right ? -translation : translation;
            updateIconsFromTranslation(targetView);
          }
        });
    return animator;
  }

  private void cancelAnimation() {
    if (swipeAnimator != null) {
      swipeAnimator.cancel();
    }
  }

  private void flingWithCurrentVelocity(boolean forceSnapBack, float lastX, float lastY) {
    float vel = getCurrentVelocity(lastX, lastY);

    // We snap back if the current translation is not far enough
    boolean snapBack = isBelowFalsingThreshold();

    // or if the velocity is in the opposite direction.
    boolean velIsInWrongDirection = vel * translation < 0;
    snapBack |= Math.abs(vel) > minFlingVelocity && velIsInWrongDirection;
    vel = snapBack ^ velIsInWrongDirection ? 0 : vel;
    fling(vel, snapBack || forceSnapBack, translation < 0);
  }

  private boolean isBelowFalsingThreshold() {
    return Math.abs(translation) < Math.abs(translationOnDown) + getMinTranslationAmount();
  }

  private int getMinTranslationAmount() {
    float factor = callback.getAffordanceFalsingFactor();
    return (int) (minTranslationAmount * factor);
  }

  private void fling(float vel, final boolean snapBack, boolean right) {
    float target =
        right ? -callback.getMaxTranslationDistance() : callback.getMaxTranslationDistance();
    target = snapBack ? 0 : target;

    ValueAnimator animator = ValueAnimator.ofFloat(translation, target);
    flingAnimationUtils.apply(animator, translation, target, vel);
    animator.addUpdateListener(
        new ValueAnimator.AnimatorUpdateListener() {
          @Override
          public void onAnimationUpdate(ValueAnimator animation) {
            translation = (float) animation.getAnimatedValue();
          }
        });
    animator.addListener(flingEndListener);
    if (!snapBack) {
      startFinishingCircleAnimation(vel * 0.375f, new AnimationEndRunnable(right), right);
      callback.onAnimationToSideStarted(right, translation, vel);
    } else {
      reset(true);
    }
    animator.start();
    swipeAnimator = animator;
    if (snapBack) {
      callback.onSwipingAborted();
    }
  }

  private void startFinishingCircleAnimation(
      float velocity, Runnable mAnimationEndRunnable, boolean right) {
    SwipeButtonView targetView = right ? rightIcon : leftIcon;
    if (targetView != null) {
      targetView.finishAnimation(velocity, mAnimationEndRunnable);
    }
  }

  private void setTranslation(float translation, boolean isReset, boolean animateReset) {
    translation = rightSwipePossible() ? translation : Math.max(0, translation);
    translation = leftSwipePossible() ? translation : Math.min(0, translation);
    float absTranslation = Math.abs(translation);
    if (translation != this.translation || isReset) {
      SwipeButtonView targetView = translation > 0 ? leftIcon : rightIcon;
      SwipeButtonView otherView = translation > 0 ? rightIcon : leftIcon;
      float alpha = absTranslation / getMinTranslationAmount();

      // We interpolate the alpha of the other icons to 0
      float fadeOutAlpha = 1.0f - alpha;
      fadeOutAlpha = Math.max(fadeOutAlpha, 0.0f);

      boolean animateIcons = isReset && animateReset;
      boolean forceNoCircleAnimation = isReset && !animateReset;
      float radius = getRadiusFromTranslation(absTranslation);
      boolean slowAnimation = isReset && isBelowFalsingThreshold();
      if (targetView != null) {
        if (!isReset) {
          updateIcon(
              targetView,
              radius,
              alpha + fadeOutAlpha * targetView.getRestingAlpha(),
              false,
              false,
              false,
              false);
        } else {
          updateIcon(
              targetView,
              0.0f,
              fadeOutAlpha * targetView.getRestingAlpha(),
              animateIcons,
              slowAnimation,
              false,
              forceNoCircleAnimation);
        }
      }
      if (otherView != null) {
        updateIcon(
            otherView,
            0.0f,
            fadeOutAlpha * otherView.getRestingAlpha(),
            animateIcons,
            slowAnimation,
            false,
            forceNoCircleAnimation);
      }

      this.translation = translation;
    }
  }

  private void updateIconsFromTranslation(SwipeButtonView targetView) {
    float absTranslation = Math.abs(translation);
    float alpha = absTranslation / getMinTranslationAmount();

    // We interpolate the alpha of the other icons to 0
    float fadeOutAlpha = 1.0f - alpha;
    fadeOutAlpha = Math.max(0.0f, fadeOutAlpha);

    // We interpolate the alpha of the targetView to 1
    SwipeButtonView otherView = targetView == rightIcon ? leftIcon : rightIcon;
    updateIconAlpha(targetView, alpha + fadeOutAlpha * targetView.getRestingAlpha(), false);
    if (otherView != null) {
      updateIconAlpha(otherView, fadeOutAlpha * otherView.getRestingAlpha(), false);
    }
  }

  private float getTranslationFromRadius(float circleSize) {
    float translation = (circleSize - minBackgroundRadius) / BACKGROUND_RADIUS_SCALE_FACTOR;
    return translation > 0.0f ? translation + touchSlop : 0.0f;
  }

  private float getRadiusFromTranslation(float translation) {
    if (translation <= touchSlop) {
      return 0.0f;
    }
    return (translation - touchSlop) * BACKGROUND_RADIUS_SCALE_FACTOR + minBackgroundRadius;
  }

  public void animateHideLeftRightIcon() {
    cancelAnimation();
    updateIcon(rightIcon, 0f, 0f, true, false, false, false);
    updateIcon(leftIcon, 0f, 0f, true, false, false, false);
  }

  private void updateIcon(
      @Nullable SwipeButtonView view,
      float circleRadius,
      float alpha,
      boolean animate,
      boolean slowRadiusAnimation,
      boolean force,
      boolean forceNoCircleAnimation) {
    if (view == null) {
      return;
    }
    if (view.getVisibility() != View.VISIBLE && !force) {
      return;
    }
    if (forceNoCircleAnimation) {
      view.setCircleRadiusWithoutAnimation(circleRadius);
    } else {
      view.setCircleRadius(circleRadius, slowRadiusAnimation);
    }
    updateIconAlpha(view, alpha, animate);
  }

  private void updateIconAlpha(SwipeButtonView view, float alpha, boolean animate) {
    float scale = getScale(alpha, view);
    alpha = Math.min(1.0f, alpha);
    view.setImageAlpha(alpha, animate);
    view.setImageScale(scale, animate);
  }

  private float getScale(float alpha, SwipeButtonView icon) {
    float scale = alpha / icon.getRestingAlpha() * 0.2f + SwipeButtonView.MIN_ICON_SCALE_AMOUNT;
    return Math.min(scale, SwipeButtonView.MAX_ICON_SCALE_AMOUNT);
  }

  private void trackMovement(MotionEvent event) {
    if (velocityTracker != null) {
      velocityTracker.addMovement(event);
    }
  }

  private void initVelocityTracker() {
    if (velocityTracker != null) {
      velocityTracker.recycle();
    }
    velocityTracker = VelocityTracker.obtain();
  }

  private float getCurrentVelocity(float lastX, float lastY) {
    if (velocityTracker == null) {
      return 0;
    }
    velocityTracker.computeCurrentVelocity(1000);
    float aX = velocityTracker.getXVelocity();
    float aY = velocityTracker.getYVelocity();
    float bX = lastX - initialTouchX;
    float bY = lastY - initialTouchY;
    float bLen = (float) Math.hypot(bX, bY);
    // Project the velocity onto the distance vector: a * b / |b|
    float projectedVelocity = (aX * bX + aY * bY) / bLen;
    if (targetedView == rightIcon) {
      projectedVelocity = -projectedVelocity;
    }
    return projectedVelocity;
  }

  public void onConfigurationChanged() {
    initDimens();
    initIcons();
  }

  public void onRtlPropertiesChanged() {
    initIcons();
  }

  public void reset(boolean animate) {
    cancelAnimation();
    setTranslation(0.0f, true, animate);
    motionCancelled = true;
    if (swipingInProgress) {
      callback.onSwipingAborted();
      swipingInProgress = false;
    }
  }

  public boolean isSwipingInProgress() {
    return swipingInProgress;
  }

  public void launchAffordance(boolean animate, boolean left) {
    SwipeButtonView targetView = left ? leftIcon : rightIcon;
    if (swipingInProgress || targetView == null) {
      // We don't want to mess with the state if the user is actually swiping already.
      return;
    }
    SwipeButtonView otherView = left ? rightIcon : leftIcon;
    startSwiping(targetView);
    if (animate) {
      fling(0, false, !left);
      updateIcon(otherView, 0.0f, 0, true, false, true, false);
    } else {
      callback.onAnimationToSideStarted(!left, translation, 0);
      translation =
          left ? callback.getMaxTranslationDistance() : callback.getMaxTranslationDistance();
      updateIcon(otherView, 0.0f, 0.0f, false, false, true, false);
      targetView.instantFinishAnimation();
      flingEndListener.onAnimationEnd(null);
      new AnimationEndRunnable(!left).run();
    }
  }

  /** Callback interface for various actions */
  public interface Callback {

    /**
     * Notifies the callback when an animation to a side page was started.
     *
     * @param rightPage Is the page animated to the right page?
     */
    void onAnimationToSideStarted(boolean rightPage, float translation, float vel);

    /** Notifies the callback the animation to a side page has ended. */
    void onAnimationToSideEnded(boolean rightPage);

    float getMaxTranslationDistance();

    void onSwipingStarted(boolean rightIcon);

    void onSwipingAborted();

    void onIconClicked(boolean rightIcon);

    @Nullable
    SwipeButtonView getLeftIcon();

    @Nullable
    SwipeButtonView getRightIcon();

    @Nullable
    View getLeftPreview();

    @Nullable
    View getRightPreview();

    /** @return The factor the minimum swipe amount should be multiplied with. */
    float getAffordanceFalsingFactor();
  }
}
