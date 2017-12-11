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

import android.content.Context;
import android.graphics.Point;
import android.support.animation.FloatPropertyCompat;
import android.support.animation.SpringAnimation;
import android.support.animation.SpringForce;
import android.support.annotation.NonNull;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Scroller;

/** Handles touches and manages moving the bubble in response */
class NewMoveHandler implements OnTouchListener {

  // Amount the ViewConfiguration's minFlingVelocity will be scaled by for our own minVelocity
  private static final int MIN_FLING_VELOCITY_FACTOR = 8;
  // The friction multiplier to control how slippery the bubble is when flung
  private static final float SCROLL_FRICTION_MULTIPLIER = 4f;

  private final Context context;
  private final WindowManager windowManager;
  private final NewBubble bubble;
  private final int minX;
  private final int minY;
  private final int maxX;
  private final int maxY;
  private final int bubbleSize;
  private final int bubbleShadowPaddingHorizontal;
  private final int bubbleExpandedViewWidth;
  private final float touchSlopSquared;

  private boolean clickable = true;
  private boolean isMoving;
  private float firstX;
  private float firstY;

  private SpringAnimation moveXAnimation;
  private SpringAnimation moveYAnimation;
  private VelocityTracker velocityTracker;
  private Scroller scroller;

  private static float clamp(float value, float min, float max) {
    return Math.min(max, Math.max(min, value));
  }

  // Handles the left/right gravity conversion and centering
  private final FloatPropertyCompat<WindowManager.LayoutParams> xProperty =
      new FloatPropertyCompat<LayoutParams>("xProperty") {
        @Override
        public float getValue(LayoutParams windowParams) {
          int realX = windowParams.x;
          // Get bubble center position from real position
          if (bubble.getDrawerVisibility() == View.INVISIBLE) {
            realX += bubbleExpandedViewWidth / 2 + bubbleShadowPaddingHorizontal * 2;
          } else {
            realX += bubbleSize / 2 + bubbleShadowPaddingHorizontal;
          }
          if (relativeToRight(windowParams)) {
            // If gravity is right, get distant from bubble center position to screen right edge
            int displayWidth = context.getResources().getDisplayMetrics().widthPixels;
            realX = displayWidth - realX;
          }
          return clamp(realX, minX, maxX);
        }

        @Override
        public void setValue(LayoutParams windowParams, float value) {
          boolean wasOnRight = (windowParams.gravity & Gravity.RIGHT) == Gravity.RIGHT;
          int displayWidth = context.getResources().getDisplayMetrics().widthPixels;
          boolean onRight;
          Integer gravityOverride = bubble.getGravityOverride();
          if (gravityOverride == null) {
            onRight = value > displayWidth / 2;
          } else {
            onRight = (gravityOverride & Gravity.RIGHT) == Gravity.RIGHT;
          }
          // Get real position from bubble center position
          int centeringOffset;
          if (bubble.getDrawerVisibility() == View.INVISIBLE) {
            centeringOffset = bubbleExpandedViewWidth / 2 + bubbleShadowPaddingHorizontal * 2;
          } else {
            centeringOffset = bubbleSize / 2 + bubbleShadowPaddingHorizontal;
          }
          windowParams.x =
              (int) (onRight ? (displayWidth - value - centeringOffset) : value - centeringOffset);
          windowParams.gravity = Gravity.TOP | (onRight ? Gravity.RIGHT : Gravity.LEFT);
          if (bubble.isVisible()) {
            windowManager.updateViewLayout(bubble.getRootView(), windowParams);
            if (onRight != wasOnRight) {
              bubble.onLeftRightSwitch(onRight);
            }
          }
        }
      };

  private final FloatPropertyCompat<WindowManager.LayoutParams> yProperty =
      new FloatPropertyCompat<LayoutParams>("yProperty") {
        @Override
        public float getValue(LayoutParams object) {
          return clamp(object.y + bubbleSize, minY, maxY);
        }

        @Override
        public void setValue(LayoutParams object, float value) {
          object.y = (int) value - bubbleSize;
          if (bubble.isVisible()) {
            windowManager.updateViewLayout(bubble.getRootView(), object);
          }
        }
      };

  public NewMoveHandler(@NonNull View targetView, @NonNull NewBubble bubble) {
    this.bubble = bubble;
    context = targetView.getContext();
    windowManager = context.getSystemService(WindowManager.class);

    bubbleSize = context.getResources().getDimensionPixelSize(R.dimen.bubble_size);
    bubbleShadowPaddingHorizontal =
        context.getResources().getDimensionPixelSize(R.dimen.bubble_shadow_padding_size_horizontal);
    bubbleExpandedViewWidth =
        context.getResources().getDimensionPixelSize(R.dimen.bubble_expanded_width);
    // The following value is based on bubble center
    minX =
        context.getResources().getDimensionPixelOffset(R.dimen.bubble_off_screen_size_horizontal)
            + bubbleSize / 2;
    minY =
        context.getResources().getDimensionPixelOffset(R.dimen.bubble_safe_margin_vertical)
            + bubbleSize / 2;
    maxX = context.getResources().getDisplayMetrics().widthPixels - minX;
    maxY = context.getResources().getDisplayMetrics().heightPixels - minY;

    // Squared because it will be compared against the square of the touch delta. This is more
    // efficient than needing to take a square root.
    touchSlopSquared = (float) Math.pow(ViewConfiguration.get(context).getScaledTouchSlop(), 2);

    targetView.setOnTouchListener(this);
  }

  public void setClickable(boolean clickable) {
    this.clickable = clickable;
  }

  public boolean isMoving() {
    return isMoving;
  }

  public void undoGravityOverride() {
    LayoutParams windowParams = bubble.getWindowParams();
    xProperty.setValue(windowParams, xProperty.getValue(windowParams));
  }

  public void snapToBounds() {
    ensureSprings();

    moveXAnimation.animateToFinalPosition(relativeToRight(bubble.getWindowParams()) ? maxX : minX);
    moveYAnimation.animateToFinalPosition(yProperty.getValue(bubble.getWindowParams()));
  }

  public int getMoveUpDistance(int deltaAllowed) {
    int currentY = (int) yProperty.getValue(bubble.getWindowParams());
    int currentDelta = maxY - currentY;
    return currentDelta >= deltaAllowed ? 0 : deltaAllowed - currentDelta;
  }

  @Override
  public boolean onTouch(View v, MotionEvent event) {
    float eventX = event.getRawX();
    float eventY = event.getRawY();
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        firstX = eventX;
        firstY = eventY;
        velocityTracker = VelocityTracker.obtain();
        break;
      case MotionEvent.ACTION_MOVE:
        if (isMoving || hasExceededTouchSlop(event)) {
          if (!isMoving) {
            isMoving = true;
            bubble.onMoveStart();
          }

          ensureSprings();

          moveXAnimation.animateToFinalPosition(clamp(eventX, minX, maxX));
          moveYAnimation.animateToFinalPosition(clamp(eventY, minY, maxY));
        }

        velocityTracker.addMovement(event);
        break;
      case MotionEvent.ACTION_UP:
        if (isMoving) {
          ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
          velocityTracker.computeCurrentVelocity(
              1000, viewConfiguration.getScaledMaximumFlingVelocity());
          float xVelocity = velocityTracker.getXVelocity();
          float yVelocity = velocityTracker.getYVelocity();
          boolean isFling = isFling(xVelocity, yVelocity);

          if (isFling) {
            Point target =
                findTarget(
                    xVelocity,
                    yVelocity,
                    (int) xProperty.getValue(bubble.getWindowParams()),
                    (int) yProperty.getValue(bubble.getWindowParams()));

            moveXAnimation.animateToFinalPosition(target.x);
            moveYAnimation.animateToFinalPosition(target.y);
          } else {
            snapX();
          }
          isMoving = false;
          bubble.onMoveFinish();
        } else {
          v.performClick();
          if (clickable) {
            bubble.primaryButtonClick();
          }
        }
        break;
      default: // fall out
    }
    return true;
  }

  private void ensureSprings() {
    if (moveXAnimation == null) {
      moveXAnimation = new SpringAnimation(bubble.getWindowParams(), xProperty);
      moveXAnimation.setSpring(new SpringForce());
      moveXAnimation.getSpring().setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);
      // Moving when expanded makes expanded view INVISIBLE, and the whole view is not at the
      // boundary. It's time to create a viewHolder.
      moveXAnimation.addEndListener(
          (animation, canceled, value, velocity) -> {
            if (!isMoving && bubble.getDrawerVisibility() == View.INVISIBLE) {
              bubble.replaceViewHolder();
            }
          });
    }

    if (moveYAnimation == null) {
      moveYAnimation = new SpringAnimation(bubble.getWindowParams(), yProperty);
      moveYAnimation.setSpring(new SpringForce());
      moveYAnimation.getSpring().setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);
    }
  }

  private Point findTarget(float xVelocity, float yVelocity, int startX, int startY) {
    if (scroller == null) {
      scroller = new Scroller(context);
      scroller.setFriction(ViewConfiguration.getScrollFriction() * SCROLL_FRICTION_MULTIPLIER);
    }

    // Find where a fling would end vertically
    scroller.fling(startX, startY, (int) xVelocity, (int) yVelocity, minX, maxX, minY, maxY);
    int targetY = scroller.getFinalY();
    scroller.abortAnimation();

    // If the x component of the velocity is above the minimum fling velocity, use velocity to
    // determine edge. Otherwise use its starting position
    boolean pullRight = isFling(xVelocity, 0) ? xVelocity > 0 : isOnRightHalf(startX);
    return new Point(pullRight ? maxX : minX, targetY);
  }

  private boolean isFling(float xVelocity, float yVelocity) {
    int minFlingVelocity =
        ViewConfiguration.get(context).getScaledMinimumFlingVelocity() * MIN_FLING_VELOCITY_FACTOR;
    return getMagnitudeSquared(xVelocity, yVelocity) > minFlingVelocity * minFlingVelocity;
  }

  private boolean isOnRightHalf(float currentX) {
    return currentX > (minX + maxX) / 2;
  }

  private void snapX() {
    // Check if x value is closer to min or max
    boolean pullRight = isOnRightHalf(xProperty.getValue(bubble.getWindowParams()));
    moveXAnimation.animateToFinalPosition(pullRight ? maxX : minX);
  }

  private boolean relativeToRight(LayoutParams windowParams) {
    return (windowParams.gravity & Gravity.RIGHT) == Gravity.RIGHT;
  }

  private boolean hasExceededTouchSlop(MotionEvent event) {
    return getMagnitudeSquared(event.getRawX() - firstX, event.getRawY() - firstY)
        > touchSlopSquared;
  }

  private float getMagnitudeSquared(float deltaX, float deltaY) {
    return deltaX * deltaX + deltaY * deltaY;
  }
}
