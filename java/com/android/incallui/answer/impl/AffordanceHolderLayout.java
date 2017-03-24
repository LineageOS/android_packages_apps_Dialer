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

package com.android.incallui.answer.impl;

import android.content.Context;
import android.content.res.Configuration;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import com.android.incallui.answer.impl.affordance.SwipeButtonHelper;
import com.android.incallui.answer.impl.affordance.SwipeButtonHelper.Callback;
import com.android.incallui.answer.impl.affordance.SwipeButtonView;
import com.android.incallui.util.AccessibilityUtil;

/** Layout that delegates touches to its SwipeButtonHelper */
public class AffordanceHolderLayout extends FrameLayout {

  private SwipeButtonHelper affordanceHelper;

  private Callback affordanceCallback;

  public AffordanceHolderLayout(Context context) {
    this(context, null);
  }

  public AffordanceHolderLayout(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public AffordanceHolderLayout(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    affordanceHelper =
        new SwipeButtonHelper(
            new Callback() {
              @Override
              public void onAnimationToSideStarted(
                  boolean rightPage, float translation, float vel) {
                if (affordanceCallback != null) {
                  affordanceCallback.onAnimationToSideStarted(rightPage, translation, vel);
                }
              }

              @Override
              public void onAnimationToSideEnded(boolean rightPage) {
                if (affordanceCallback != null) {
                  affordanceCallback.onAnimationToSideEnded(rightPage);
                }
              }

              @Override
              public float getMaxTranslationDistance() {
                if (affordanceCallback != null) {
                  return affordanceCallback.getMaxTranslationDistance();
                }
                return 0;
              }

              @Override
              public void onSwipingStarted(boolean rightIcon) {
                if (affordanceCallback != null) {
                  affordanceCallback.onSwipingStarted(rightIcon);
                }
              }

              @Override
              public void onSwipingAborted() {
                if (affordanceCallback != null) {
                  affordanceCallback.onSwipingAborted();
                }
              }

              @Override
              public void onIconClicked(boolean rightIcon) {
                if (affordanceCallback != null) {
                  affordanceCallback.onIconClicked(rightIcon);
                }
              }

              @Nullable
              @Override
              public SwipeButtonView getLeftIcon() {
                if (affordanceCallback != null) {
                  return affordanceCallback.getLeftIcon();
                }
                return null;
              }

              @Nullable
              @Override
              public SwipeButtonView getRightIcon() {
                if (affordanceCallback != null) {
                  return affordanceCallback.getRightIcon();
                }
                return null;
              }

              @Nullable
              @Override
              public View getLeftPreview() {
                if (affordanceCallback != null) {
                  return affordanceCallback.getLeftPreview();
                }
                return null;
              }

              @Nullable
              @Override
              public View getRightPreview() {
                if (affordanceCallback != null) {
                  affordanceCallback.getRightPreview();
                }
                return null;
              }

              @Override
              public float getAffordanceFalsingFactor() {
                if (affordanceCallback != null) {
                  return affordanceCallback.getAffordanceFalsingFactor();
                }
                return 1.0f;
              }
            },
            context);
  }

  public void setAffordanceCallback(@Nullable Callback callback) {
    affordanceCallback = callback;
    affordanceHelper.init();
  }

  public void startHintAnimation(boolean rightIcon, @Nullable Runnable onFinishListener) {
    affordanceHelper.startHintAnimation(rightIcon, onFinishListener);
  }

  public void animateHideLeftRightIcon() {
    affordanceHelper.animateHideLeftRightIcon();
  }

  public void reset(boolean animate) {
    affordanceHelper.reset(animate);
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent event) {
    if (AccessibilityUtil.isTouchExplorationEnabled(getContext())) {
      return false;
    }
    return affordanceHelper.onTouchEvent(event) || super.onInterceptTouchEvent(event);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    return affordanceHelper.onTouchEvent(event) || super.onTouchEvent(event);
  }

  @Override
  protected void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    affordanceHelper.onConfigurationChanged();
  }
}
