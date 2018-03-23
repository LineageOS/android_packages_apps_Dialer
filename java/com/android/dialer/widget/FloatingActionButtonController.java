/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.dialer.widget;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.support.annotation.DrawableRes;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.FloatingActionButton.OnVisibilityChangedListener;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import com.android.dialer.common.Assert;

/** Controls the movement and appearance of the FAB (Floating Action Button). */
public class FloatingActionButtonController {

  public static final int ALIGN_MIDDLE = 0;
  public static final int ALIGN_QUARTER_END = 1;
  public static final int ALIGN_END = 2;

  private final int animationDuration;
  private final int floatingActionButtonWidth;
  private final int floatingActionButtonMarginRight;
  private final FloatingActionButton fab;
  private final Interpolator fabInterpolator;
  private int fabIconId = -1;
  private int screenWidth;

  public FloatingActionButtonController(Activity activity, FloatingActionButton fab) {
    Resources resources = activity.getResources();
    fabInterpolator =
        AnimationUtils.loadInterpolator(activity, android.R.interpolator.fast_out_slow_in);
    floatingActionButtonWidth =
        resources.getDimensionPixelSize(R.dimen.floating_action_button_width);
    floatingActionButtonMarginRight =
        resources.getDimensionPixelOffset(R.dimen.floating_action_button_margin_right);
    animationDuration = resources.getInteger(R.integer.floating_action_button_animation_duration);
    this.fab = fab;
  }

  /**
   * Passes the screen width into the class. Necessary for translation calculations. Should be
   * called as soon as parent View width is available.
   *
   * @param screenWidth The width of the screen in pixels.
   */
  public void setScreenWidth(int screenWidth) {
    this.screenWidth = screenWidth;
  }

  /** @see FloatingActionButton#isShown() */
  public boolean isVisible() {
    return fab.isShown();
  }

  /**
   * Sets FAB as shown or hidden.
   *
   * @see #scaleIn()
   * @see #scaleOut()
   */
  public void setVisible(boolean visible) {
    if (visible) {
      scaleIn();
    } else {
      scaleOut();
    }
  }

  public void changeIcon(Context context, @DrawableRes int iconId, String description) {
    if (this.fabIconId != iconId) {
      fab.setImageResource(iconId);
      fab.setImageTintList(
          ColorStateList.valueOf(context.getResources().getColor(android.R.color.white)));
      this.fabIconId = iconId;
    }
    if (!fab.getContentDescription().equals(description)) {
      fab.setContentDescription(description);
    }
  }

  /**
   * Updates the FAB location (middle to right position) as the PageView scrolls.
   *
   * @param positionOffset A fraction used to calculate position of the FAB during page scroll.
   */
  public void onPageScrolled(float positionOffset) {
    // As the page is scrolling, if we're on the first tab, update the FAB position so it
    // moves along with it.
    fab.setTranslationX(positionOffset * getTranslationXForAlignment(ALIGN_END));
  }

  /**
   * Aligns the FAB to the described location
   *
   * @param align One of ALIGN_MIDDLE, ALIGN_QUARTER_RIGHT, or ALIGN_RIGHT.
   * @param animate Whether or not to animate the transition.
   */
  public void align(int align, boolean animate) {
    align(align, 0 /*offsetX */, 0 /* offsetY */, animate);
  }

  /**
   * Aligns the FAB to the described location plus specified additional offsets.
   *
   * @param align One of ALIGN_MIDDLE, ALIGN_QUARTER_RIGHT, or ALIGN_RIGHT.
   * @param offsetX Additional offsetX to translate by.
   * @param offsetY Additional offsetY to translate by.
   * @param animate Whether or not to animate the transition.
   */
  private void align(int align, int offsetX, int offsetY, boolean animate) {
    if (screenWidth == 0) {
      return;
    }

    int translationX = getTranslationXForAlignment(align);

    // Skip animation if container is not shown; animation causes container to show again.
    if (animate && fab.isShown()) {
      fab.animate()
          .translationX(translationX + offsetX)
          .translationY(offsetY)
          .setInterpolator(fabInterpolator)
          .setDuration(animationDuration)
          .start();
    } else {
      fab.setTranslationX(translationX + offsetX);
      fab.setTranslationY(offsetY);
    }
  }

  /** @see FloatingActionButton#show() */
  public void scaleIn() {
    fab.show();
  }

  /** @see FloatingActionButton#hide() */
  public void scaleOut() {
    fab.hide();
  }

  public void scaleOut(OnVisibilityChangedListener listener) {
    fab.hide(listener);
  }

  /**
   * Calculates the X offset of the FAB to the given alignment, adjusted for whether or not the view
   * is in RTL mode.
   *
   * @param align One of ALIGN_MIDDLE, ALIGN_QUARTER_RIGHT, or ALIGN_RIGHT.
   * @return The translationX for the given alignment.
   */
  private int getTranslationXForAlignment(int align) {
    int result;
    switch (align) {
      case ALIGN_MIDDLE:
        // Moves the FAB to exactly center screen.
        return 0;
      case ALIGN_QUARTER_END:
        // Moves the FAB a quarter of the screen width.
        result = screenWidth / 4;
        break;
      case ALIGN_END:
        // Moves the FAB half the screen width. Same as aligning right with a marginRight.
        result = screenWidth / 2 - floatingActionButtonWidth / 2 - floatingActionButtonMarginRight;
        break;
      default:
        throw Assert.createIllegalStateFailException("Invalid alignment value: " + align);
    }
    if (isLayoutRtl()) {
      result *= -1;
    }
    return result;
  }

  private boolean isLayoutRtl() {
    return fab.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
  }
}
