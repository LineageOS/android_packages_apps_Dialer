/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.dialer.app.widget;

import android.animation.ValueAnimator;
import android.os.Bundle;
import com.android.dialer.animation.AnimUtils.AnimationCallback;
import com.android.dialer.common.LogUtil;

/**
 * Controls the various animated properties of the actionBar: showing/hiding, fading/revealing, and
 * collapsing/expanding, and assigns suitable properties to the actionBar based on the current state
 * of the UI.
 */
public class ActionBarController {

  private static final String KEY_IS_SLID_UP = "key_actionbar_is_slid_up";
  private static final String KEY_IS_FADED_OUT = "key_actionbar_is_faded_out";
  private static final String KEY_IS_EXPANDED = "key_actionbar_is_expanded";

  private ActivityUi mActivityUi;
  private SearchEditTextLayout mSearchBox;

  private boolean mIsActionBarSlidUp;

  private final AnimationCallback mFadeOutCallback =
      new AnimationCallback() {
        @Override
        public void onAnimationEnd() {
          slideActionBar(true /* slideUp */, false /* animate */);
        }

        @Override
        public void onAnimationCancel() {
          slideActionBar(true /* slideUp */, false /* animate */);
        }
      };

  private final AnimationCallback mFadeInCallback =
      new AnimationCallback() {
        @Override
        public void onAnimationEnd() {
          slideActionBar(false /* slideUp */, false /* animate */);
        }

        @Override
        public void onAnimationCancel() {
          slideActionBar(false /* slideUp */, false /* animate */);
        }
      };
  private ValueAnimator mAnimator;

  public ActionBarController(ActivityUi activityUi, SearchEditTextLayout searchBox) {
    mActivityUi = activityUi;
    mSearchBox = searchBox;
  }

  /** @return Whether or not the action bar is currently showing (both slid down and visible) */
  public boolean isActionBarShowing() {
    return !mIsActionBarSlidUp && !mSearchBox.isFadedOut();
  }

  /** Called when the user has tapped on the collapsed search box, to start a new search query. */
  public void onSearchBoxTapped() {
    LogUtil.d(
        "ActionBarController.onSearchBoxTapped", "isInSearchUi " + mActivityUi.isInSearchUi());
    if (!mActivityUi.isInSearchUi()) {
      mSearchBox.expand(true /* animate */, true /* requestFocus */);
    }
  }

  /** Called when search UI has been exited for some reason. */
  public void onSearchUiExited() {
    LogUtil.d(
        "ActionBarController.onSearchUIExited",
        "isExpanded: %b, isFadedOut %b",
        mSearchBox.isExpanded(),
        mSearchBox.isFadedOut());
    if (mSearchBox.isExpanded()) {
      mSearchBox.collapse(true /* animate */);
    }
    if (mSearchBox.isFadedOut()) {
      mSearchBox.fadeIn();
    }

    slideActionBar(false /* slideUp */, false /* animate */);
  }

  /**
   * Called to indicate that the user is trying to hide the dialpad. Should be called before any
   * state changes have actually occurred.
   */
  public void onDialpadDown() {
    LogUtil.d(
        "ActionBarController.onDialpadDown",
        "isInSearchUi: %b, hasSearchQuery: %b, isFadedOut: %b, isExpanded: %b",
        mActivityUi.isInSearchUi(),
        mActivityUi.hasSearchQuery(),
        mSearchBox.isFadedOut(),
        mSearchBox.isExpanded());
    if (mActivityUi.isInSearchUi()) {
      if (mActivityUi.hasSearchQuery()) {
        if (mSearchBox.isFadedOut()) {
          mSearchBox.setVisible(true);
        }
        if (!mSearchBox.isExpanded()) {
          mSearchBox.expand(false /* animate */, false /* requestFocus */);
        }
        slideActionBar(false /* slideUp */, true /* animate */);
      } else {
        mSearchBox.fadeIn(mFadeInCallback);
      }
    }
  }

  /**
   * Called to indicate that the user is trying to show the dialpad. Should be called before any
   * state changes have actually occurred.
   */
  public void onDialpadUp() {
    LogUtil.d("ActionBarController.onDialpadUp", "isInSearchUi " + mActivityUi.isInSearchUi());
    if (mActivityUi.isInSearchUi()) {
      slideActionBar(true /* slideUp */, true /* animate */);
    } else {
      // From the lists fragment
      mSearchBox.fadeOut(mFadeOutCallback);
    }
  }

  public void slideActionBar(boolean slideUp, boolean animate) {
    LogUtil.d("ActionBarController.slidingActionBar", "up: %b, animate: %b", slideUp, animate);

    if (mAnimator != null && mAnimator.isRunning()) {
      mAnimator.cancel();
      mAnimator.removeAllUpdateListeners();
    }
    if (animate) {
      mAnimator = slideUp ? ValueAnimator.ofFloat(0, 1) : ValueAnimator.ofFloat(1, 0);
      mAnimator.addUpdateListener(
          animation -> {
            final float value = (float) animation.getAnimatedValue();
            setHideOffset((int) (mActivityUi.getActionBarHeight() * value));
          });
      mAnimator.start();
    } else {
      setHideOffset(slideUp ? mActivityUi.getActionBarHeight() : 0);
    }
    mIsActionBarSlidUp = slideUp;
  }

  public void setAlpha(float alphaValue) {
    mSearchBox.animate().alpha(alphaValue).start();
  }

  private void setHideOffset(int offset) {
    mActivityUi.setActionBarHideOffset(offset);
  }

  /** Saves the current state of the action bar into a provided {@link Bundle} */
  public void saveInstanceState(Bundle outState) {
    outState.putBoolean(KEY_IS_SLID_UP, mIsActionBarSlidUp);
    outState.putBoolean(KEY_IS_FADED_OUT, mSearchBox.isFadedOut());
    outState.putBoolean(KEY_IS_EXPANDED, mSearchBox.isExpanded());
  }

  /** Restores the action bar state from a provided {@link Bundle}. */
  public void restoreInstanceState(Bundle inState) {
    mIsActionBarSlidUp = inState.getBoolean(KEY_IS_SLID_UP);

    final boolean isSearchBoxFadedOut = inState.getBoolean(KEY_IS_FADED_OUT);
    if (isSearchBoxFadedOut) {
      if (!mSearchBox.isFadedOut()) {
        mSearchBox.setVisible(false);
      }
    } else if (mSearchBox.isFadedOut()) {
      mSearchBox.setVisible(true);
    }

    final boolean isSearchBoxExpanded = inState.getBoolean(KEY_IS_EXPANDED);
    if (isSearchBoxExpanded) {
      if (!mSearchBox.isExpanded()) {
        mSearchBox.expand(false, false);
      }
    } else if (mSearchBox.isExpanded()) {
      mSearchBox.collapse(false);
    }
  }

  /**
   * This should be called after onCreateOptionsMenu has been called, when the actionbar has been
   * laid out and actually has a height.
   */
  public void restoreActionBarOffset() {
    slideActionBar(mIsActionBarSlidUp /* slideUp */, false /* animate */);
  }

  public interface ActivityUi {

    boolean isInSearchUi();

    boolean hasSearchQuery();

    int getActionBarHeight();

    void setActionBarHideOffset(int offset);
  }
}
