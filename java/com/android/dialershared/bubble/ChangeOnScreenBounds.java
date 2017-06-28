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

package com.android.dialershared.bubble;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.support.annotation.VisibleForTesting;
import android.transition.Transition;
import android.transition.TransitionValues;
import android.util.Property;
import android.view.View;
import android.view.ViewGroup;

/** Similar to {@link android.transition.ChangeBounds ChangeBounds} but works across windows */
public class ChangeOnScreenBounds extends Transition {

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final String PROPNAME_BOUNDS = "bubble:changeScreenBounds:bounds";

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final String PROPNAME_SCREEN_X = "bubble:changeScreenBounds:screenX";

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final String PROPNAME_SCREEN_Y = "bubble:changeScreenBounds:screenY";

  private static final Property<ViewBounds, PointF> TOP_LEFT_PROPERTY =
      new Property<ViewBounds, PointF>(PointF.class, "topLeft") {
        @Override
        public void set(ViewBounds viewBounds, PointF topLeft) {
          viewBounds.setTopLeft(topLeft);
        }

        @Override
        public PointF get(ViewBounds viewBounds) {
          return null;
        }
      };

  private static final Property<ViewBounds, PointF> BOTTOM_RIGHT_PROPERTY =
      new Property<ViewBounds, PointF>(PointF.class, "bottomRight") {
        @Override
        public void set(ViewBounds viewBounds, PointF bottomRight) {
          viewBounds.setBottomRight(bottomRight);
        }

        @Override
        public PointF get(ViewBounds viewBounds) {
          return null;
        }
      };
  private final int[] tempLocation = new int[2];

  @Override
  public void captureStartValues(TransitionValues transitionValues) {
    captureValues(transitionValues);
  }

  @Override
  public void captureEndValues(TransitionValues transitionValues) {
    captureValues(transitionValues);
  }

  private void captureValues(TransitionValues values) {
    View view = values.view;

    if (view.isLaidOut() || view.getWidth() != 0 || view.getHeight() != 0) {
      values.values.put(
          PROPNAME_BOUNDS,
          new Rect(view.getLeft(), view.getTop(), view.getRight(), view.getBottom()));
      values.view.getLocationOnScreen(tempLocation);
      values.values.put(PROPNAME_SCREEN_X, tempLocation[0]);
      values.values.put(PROPNAME_SCREEN_Y, tempLocation[1]);
    }
  }

  @Override
  public Animator createAnimator(
      ViewGroup sceneRoot, TransitionValues startValues, TransitionValues endValues) {
    Rect startBounds = (Rect) startValues.values.get(PROPNAME_BOUNDS);
    Rect endBounds = (Rect) endValues.values.get(PROPNAME_BOUNDS);

    if (startBounds == null || endBounds == null) {
      // start or end values were not captured, so don't animate.
      return null;
    }

    // Offset the startBounds by the difference in screen position
    int startScreenX = (Integer) startValues.values.get(PROPNAME_SCREEN_X);
    int startScreenY = (Integer) startValues.values.get(PROPNAME_SCREEN_Y);
    int endScreenX = (Integer) endValues.values.get(PROPNAME_SCREEN_X);
    int endScreenY = (Integer) endValues.values.get(PROPNAME_SCREEN_Y);
    startBounds.offset(startScreenX - endScreenX, startScreenY - endScreenY);

    final int startLeft = startBounds.left;
    final int endLeft = endBounds.left;
    final int startTop = startBounds.top;
    final int endTop = endBounds.top;
    final int startRight = startBounds.right;
    final int endRight = endBounds.right;
    final int startBottom = startBounds.bottom;
    final int endBottom = endBounds.bottom;
    ViewBounds viewBounds = new ViewBounds(endValues.view);
    viewBounds.setTopLeft(new PointF(startLeft, startTop));
    viewBounds.setBottomRight(new PointF(startRight, startBottom));

    // Animate the top left and bottom right corners along a path
    Path topLeftPath = getPathMotion().getPath(startLeft, startTop, endLeft, endTop);
    ObjectAnimator topLeftAnimator =
        ObjectAnimator.ofObject(viewBounds, TOP_LEFT_PROPERTY, null, topLeftPath);

    Path bottomRightPath = getPathMotion().getPath(startRight, startBottom, endRight, endBottom);
    ObjectAnimator bottomRightAnimator =
        ObjectAnimator.ofObject(viewBounds, BOTTOM_RIGHT_PROPERTY, null, bottomRightPath);
    AnimatorSet set = new AnimatorSet();
    set.playTogether(topLeftAnimator, bottomRightAnimator);
    return set;
  }

  private static class ViewBounds {
    private int left;
    private int top;
    private int right;
    private int bottom;
    private final View view;
    private int topLeftCalls;
    private int bottomRightCalls;

    public ViewBounds(View view) {
      this.view = view;
    }

    public void setTopLeft(PointF topLeft) {
      left = Math.round(topLeft.x);
      top = Math.round(topLeft.y);
      topLeftCalls++;
      if (topLeftCalls == bottomRightCalls) {
        updateLeftTopRightBottom();
      }
    }

    public void setBottomRight(PointF bottomRight) {
      right = Math.round(bottomRight.x);
      bottom = Math.round(bottomRight.y);
      bottomRightCalls++;
      if (topLeftCalls == bottomRightCalls) {
        updateLeftTopRightBottom();
      }
    }

    private void updateLeftTopRightBottom() {
      view.setLeft(left);
      view.setTop(top);
      view.setRight(right);
      view.setBottom(bottom);
      topLeftCalls = 0;
      bottomRightCalls = 0;
    }
  }
}
