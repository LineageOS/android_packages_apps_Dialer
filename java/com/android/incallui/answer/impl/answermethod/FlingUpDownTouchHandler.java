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

package com.android.incallui.answer.impl.answermethod;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.FloatRange;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import com.android.dialer.common.DpUtil;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.MathUtil;
import com.android.incallui.answer.impl.classifier.FalsingManager;
import com.android.incallui.answer.impl.utils.FlingAnimationUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Touch handler that keeps track of flings for {@link FlingUpDownMethod}. */
@SuppressLint("ClickableViewAccessibility")
class FlingUpDownTouchHandler implements OnTouchListener {

  /** Callback interface for significant events with this touch handler */
  interface OnProgressChangedListener {

    /**
     * Called when the visible answer progress has changed. Implementations should use this for
     * animation, but should not perform accepts or rejects until {@link #onMoveFinish(boolean)} is
     * called.
     *
     * @param progress float representation of the progress with +1f fully accepted, -1f fully
     *     rejected, and 0 neutral.
     */
    void onProgressChanged(@FloatRange(from = -1f, to = 1f) float progress);

    /** Called when a touch event has started being tracked. */
    void onTrackingStart();

    /** Called when touch events stop being tracked. */
    void onTrackingStopped();

    /**
     * Called when the progress has fully animated back to neutral. Normal resting animation should
     * resume, possibly with a hint animation first.
     *
     * @param showHint {@code true} iff the hint animation should be run before resuming normal
     *     animation.
     */
    void onMoveReset(boolean showHint);

    /**
     * Called when the progress has animated fully to accept or reject.
     *
     * @param accept {@code true} if the call has been accepted, {@code false} if it has been
     *     rejected.
     */
    void onMoveFinish(boolean accept);

    /**
     * Determine whether this gesture should use the {@link FalsingManager} to reject accidental
     * touches
     *
     * @param downEvent the MotionEvent corresponding to the start of the gesture
     * @return {@code true} if the {@link FalsingManager} should be used to reject accidental
     *     touches for this gesture
     */
    boolean shouldUseFalsing(@NonNull MotionEvent downEvent);
  }

  // Progress that must be moved through to not show the hint animation after gesture completes
  private static final float HINT_MOVE_THRESHOLD_RATIO = .1f;
  // Dp touch needs to move upward to be considered fully accepted
  private static final int ACCEPT_THRESHOLD_DP = 150;
  // Dp touch needs to move downward to be considered fully rejected
  private static final int REJECT_THRESHOLD_DP = 150;
  // Dp touch needs to move for it to not be considered a false touch (if FalsingManager is not
  // enabled)
  private static final int FALSING_THRESHOLD_DP = 40;

  // Progress at which a fling in the opposite direction will recenter instead of
  // accepting/rejecting
  private static final float PROGRESS_FLING_RECENTER = .1f;

  // Progress at which a slow swipe would continue toward accept/reject after the
  // touch has been let go, otherwise will recenter
  private static final float PROGRESS_SWIPE_RECENTER = .8f;

  private static final float REJECT_FLING_THRESHOLD_MODIFIER = 2f;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({FlingTarget.CENTER, FlingTarget.ACCEPT, FlingTarget.REJECT})
  private @interface FlingTarget {
    int CENTER = 0;
    int ACCEPT = 1;
    int REJECT = -1;
  }

  /**
   * Create a new FlingUpDownTouchHandler and attach it to the target. Will call {@link
   * View#setOnTouchListener(OnTouchListener)} before returning.
   *
   * @param target View whose touches are to be listened to
   * @param listener Callback to listen to major events
   * @param falsingManager FalsingManager to identify false touches
   * @return the instance of FlingUpDownTouchHandler that has been added as a touch listener
   */
  public static FlingUpDownTouchHandler attach(
      @NonNull View target,
      @NonNull OnProgressChangedListener listener,
      @Nullable FalsingManager falsingManager) {
    FlingUpDownTouchHandler handler = new FlingUpDownTouchHandler(target, listener, falsingManager);
    target.setOnTouchListener(handler);
    return handler;
  }

  @NonNull private final View target;
  @NonNull private final OnProgressChangedListener listener;

  private VelocityTracker velocityTracker;
  private FlingAnimationUtils flingAnimationUtils;

  private boolean touchEnabled = true;
  private boolean flingEnabled = true;
  private float currentProgress;
  private boolean tracking;

  private boolean motionAborted;
  private boolean touchSlopExceeded;
  private boolean hintDistanceExceeded;
  private int trackingPointer;
  private Animator progressAnimator;

  private float touchSlop;
  private float initialTouchY;
  private float acceptThresholdY;
  private float rejectThresholdY;
  private float zeroY;

  private boolean touchAboveFalsingThreshold;
  private float falsingThresholdPx;
  private boolean touchUsesFalsing;

  private final float acceptThresholdPx;
  private final float rejectThresholdPx;
  private final float deadZoneTopPx;

  @Nullable private final FalsingManager falsingManager;

  private FlingUpDownTouchHandler(
      @NonNull View target,
      @NonNull OnProgressChangedListener listener,
      @Nullable FalsingManager falsingManager) {
    this.target = target;
    this.listener = listener;
    Context context = target.getContext();
    touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    flingAnimationUtils = new FlingAnimationUtils(context, .6f);
    falsingThresholdPx = DpUtil.dpToPx(context, FALSING_THRESHOLD_DP);
    acceptThresholdPx = DpUtil.dpToPx(context, ACCEPT_THRESHOLD_DP);
    rejectThresholdPx = DpUtil.dpToPx(context, REJECT_THRESHOLD_DP);

    deadZoneTopPx =
        Math.max(
            context.getResources().getDimension(R.dimen.answer_swipe_dead_zone_top),
            acceptThresholdPx);
    this.falsingManager = falsingManager;
  }

  /** Returns {@code true} iff a touch is being tracked */
  public boolean isTracking() {
    return tracking;
  }

  /**
   * Sets whether touch events will continue to be listened to
   *
   * @param touchEnabled whether future touch events will be listened to
   */
  public void setTouchEnabled(boolean touchEnabled) {
    this.touchEnabled = touchEnabled;
  }

  /**
   * Sets whether fling velocity is used to affect accept/reject behavior
   *
   * @param flingEnabled whether fling velocity will be used when determining whether to
   *     accept/reject or recenter
   */
  public void setFlingEnabled(boolean flingEnabled) {
    this.flingEnabled = flingEnabled;
  }

  public void detach() {
    cancelProgressAnimator();
    setTouchEnabled(false);
  }

  @Override
  public boolean onTouch(View v, MotionEvent event) {
    if (falsingManager != null) {
      falsingManager.onTouchEvent(event);
    }
    if (!touchEnabled) {
      return false;
    }
    if (motionAborted && (event.getActionMasked() != MotionEvent.ACTION_DOWN)) {
      return false;
    }

    int pointerIndex = event.findPointerIndex(trackingPointer);
    if (pointerIndex < 0) {
      pointerIndex = 0;
      trackingPointer = event.getPointerId(pointerIndex);
    }
    final float pointerY = event.getY(pointerIndex);

    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        if (pointerY < deadZoneTopPx) {
          return false;
        }
        motionAborted = false;
        startMotion(pointerY, false, currentProgress);
        touchAboveFalsingThreshold = false;
        touchUsesFalsing = listener.shouldUseFalsing(event);
        if (velocityTracker == null) {
          initVelocityTracker();
        }
        trackMovement(event);
        cancelProgressAnimator();
        touchSlopExceeded = progressAnimator != null;
        onTrackingStarted();
        break;
      case MotionEvent.ACTION_POINTER_UP:
        final int upPointer = event.getPointerId(event.getActionIndex());
        if (trackingPointer == upPointer) {
          // gesture is ongoing, find a new pointer to track
          int newIndex = event.getPointerId(0) != upPointer ? 0 : 1;
          float newY = event.getY(newIndex);
          trackingPointer = event.getPointerId(newIndex);
          startMotion(newY, true, currentProgress);
        }
        break;
      case MotionEvent.ACTION_POINTER_DOWN:
        motionAborted = true;
        endMotionEvent(event, pointerY, true);
        return false;
      case MotionEvent.ACTION_MOVE:
        float deltaY = pointerY - initialTouchY;

        if (Math.abs(deltaY) > touchSlop) {
          touchSlopExceeded = true;
        }
        if (Math.abs(deltaY) >= falsingThresholdPx) {
          touchAboveFalsingThreshold = true;
        }
        setCurrentProgress(pointerYToProgress(pointerY));
        trackMovement(event);
        break;

      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        trackMovement(event);
        endMotionEvent(event, pointerY, false);
    }
    return true;
  }

  private void endMotionEvent(MotionEvent event, float pointerY, boolean forceCancel) {
    trackingPointer = -1;
    if ((tracking && touchSlopExceeded)
        || Math.abs(pointerY - initialTouchY) > touchSlop
        || event.getActionMasked() == MotionEvent.ACTION_CANCEL
        || forceCancel) {
      float vel = 0f;
      float vectorVel = 0f;
      if (velocityTracker != null) {
        velocityTracker.computeCurrentVelocity(1000);
        vel = velocityTracker.getYVelocity();
        vectorVel =
            Math.copySign(
                (float) Math.hypot(velocityTracker.getXVelocity(), velocityTracker.getYVelocity()),
                vel);
      }

      boolean falseTouch = isFalseTouch();
      boolean forceRecenter =
          falseTouch
              || !touchSlopExceeded
              || forceCancel
              || event.getActionMasked() == MotionEvent.ACTION_CANCEL;

      @FlingTarget
      int target = forceRecenter ? FlingTarget.CENTER : getFlingTarget(pointerY, vectorVel);

      fling(vel, target, falseTouch);
      onTrackingStopped();
    } else {
      onTrackingStopped();
      setCurrentProgress(0);
      onMoveEnded();
    }

    if (velocityTracker != null) {
      velocityTracker.recycle();
      velocityTracker = null;
    }
  }

  @FlingTarget
  private int getFlingTarget(float pointerY, float vectorVel) {
    float progress = pointerYToProgress(pointerY);

    float minVelocityPxPerSecond = flingAnimationUtils.getMinVelocityPxPerSecond();
    if (vectorVel > 0) {
      minVelocityPxPerSecond *= REJECT_FLING_THRESHOLD_MODIFIER;
    }
    if (!flingEnabled || Math.abs(vectorVel) < minVelocityPxPerSecond) {
      // Not a fling
      if (Math.abs(progress) > PROGRESS_SWIPE_RECENTER) {
        // Progress near one of the edges
        return progress > 0 ? FlingTarget.ACCEPT : FlingTarget.REJECT;
      } else {
        return FlingTarget.CENTER;
      }
    }

    boolean sameDirection = vectorVel < 0 == progress > 0;
    if (!sameDirection && Math.abs(progress) >= PROGRESS_FLING_RECENTER) {
      // Being flung back toward center
      return FlingTarget.CENTER;
    }
    // Flung toward an edge
    return vectorVel < 0 ? FlingTarget.ACCEPT : FlingTarget.REJECT;
  }

  @FloatRange(from = -1f, to = 1f)
  private float pointerYToProgress(float pointerY) {
    boolean pointerAboveZero = pointerY > zeroY;
    float nearestThreshold = pointerAboveZero ? rejectThresholdY : acceptThresholdY;

    float absoluteProgress = (pointerY - zeroY) / (nearestThreshold - zeroY);
    return MathUtil.clamp(absoluteProgress * (pointerAboveZero ? -1 : 1), -1f, 1f);
  }

  private boolean isFalseTouch() {
    return !touchAboveFalsingThreshold;
  }

  private void trackMovement(MotionEvent event) {
    if (velocityTracker != null) {
      velocityTracker.addMovement(event);
    }
  }

  private void fling(float velocity, @FlingTarget int target, boolean centerBecauseOfFalsing) {
    ValueAnimator animator = createProgressAnimator(target);
    if (target == FlingTarget.CENTER) {
      flingAnimationUtils.apply(animator, currentProgress, target, velocity);
    } else {
      flingAnimationUtils.applyDismissing(animator, currentProgress, target, velocity, 1);
    }
    if (target == FlingTarget.CENTER && centerBecauseOfFalsing) {
      velocity = 0;
    }
    if (velocity == 0) {
      animator.setDuration(350);
    }

    animator.addListener(
        new AnimatorListenerAdapter() {
          boolean canceled;

          @Override
          public void onAnimationCancel(Animator animation) {
            canceled = true;
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            progressAnimator = null;
            if (!canceled) {
              onMoveEnded();
            }
          }
        });
    progressAnimator = animator;
    animator.start();
  }

  private void onMoveEnded() {
    if (currentProgress == 0) {
      listener.onMoveReset(!hintDistanceExceeded);
    } else {
      listener.onMoveFinish(currentProgress > 0);
    }
  }

  private ValueAnimator createProgressAnimator(float targetProgress) {
    ValueAnimator animator = ValueAnimator.ofFloat(currentProgress, targetProgress);
    animator.addUpdateListener(
        new AnimatorUpdateListener() {
          @Override
          public void onAnimationUpdate(ValueAnimator animation) {
            setCurrentProgress((Float) animation.getAnimatedValue());
          }
        });
    return animator;
  }

  private void initVelocityTracker() {
    if (velocityTracker != null) {
      velocityTracker.recycle();
    }
    velocityTracker = VelocityTracker.obtain();
  }

  private void startMotion(float newY, boolean startTracking, float startProgress) {
    initialTouchY = newY;
    hintDistanceExceeded = false;

    if (startProgress <= .25) {
      acceptThresholdY = Math.max(0, initialTouchY - acceptThresholdPx);
      rejectThresholdY = Math.min(target.getHeight(), initialTouchY + rejectThresholdPx);
      zeroY = initialTouchY;
    }

    if (startTracking) {
      touchSlopExceeded = true;
      onTrackingStarted();
      setCurrentProgress(startProgress);
    }
  }

  private void onTrackingStarted() {
    tracking = true;
    listener.onTrackingStart();
  }

  private void onTrackingStopped() {
    tracking = false;
    listener.onTrackingStopped();
  }

  private void cancelProgressAnimator() {
    if (progressAnimator != null) {
      progressAnimator.cancel();
    }
  }

  private void setCurrentProgress(float progress) {
    if (Math.abs(progress) > HINT_MOVE_THRESHOLD_RATIO) {
      hintDistanceExceeded = true;
    }
    currentProgress = progress;
    listener.onProgressChanged(progress);
  }
}
