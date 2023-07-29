/*
 * Copyright (C) 2018 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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

package com.android.dialer.main.impl.bottomnav;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.StringRes;

import com.android.dialer.R;
import com.android.dialer.common.Assert;
import com.android.dialer.common.MathUtil;
import com.android.dialer.util.DialerUtils;
import com.android.incallui.answer.impl.utils.Interpolators;
import com.google.android.material.navigation.NavigationBarItemView;

/** Navigation item in a bottom nav. */
final class BottomNavItem extends LinearLayout {

  @Nullable
  private View activeIndicatorView;
  private ValueAnimator activeIndicatorAnimator;
  private ImageView image;
  private TextView text;
  private TextView notificationBadge;
  private @DrawableRes int drawableRes;
  private @DrawableRes int drawableResSelected;

  private final ActiveIndicatorTransform activeIndicatorTransform = new ActiveIndicatorTransform();
  private float activeIndicatorProgress = 0F;
  private boolean initialized;

  public BottomNavItem(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    activeIndicatorView = findViewById(R.id.active_indicator);
    activeIndicatorView.setBackgroundResource(R.drawable.pill);
    image = findViewById(R.id.bottom_nav_item_image);
    text = findViewById(R.id.bottom_nav_item_text);
    notificationBadge = findViewById(R.id.notification_badge);
    initialized = true;
  }

  @Override
  public void setSelected(boolean selected) {
    super.setSelected(selected);
    int colorId = selected
            ? DialerUtils.resolveColor(getContext(), android.R.attr.textColorPrimary)
            : DialerUtils.resolveColor(getContext(), android.R.attr.textColorSecondary);
    image.setImageResource(selected ? drawableResSelected : drawableRes);
    image.setImageTintList(ColorStateList.valueOf(colorId));
    text.setTextColor(colorId);

    float newIndicatorProgress = selected ? 1F : 0F;
    maybeAnimateActiveIndicatorToProgress(newIndicatorProgress);
  }

  private void setActiveIndicatorProgress(
          @FloatRange(from = 0F, to = 1F) float progress, float target) {
    if (activeIndicatorView != null) {
      activeIndicatorTransform.updateForProgress(progress, target, activeIndicatorView);
    }
    activeIndicatorProgress = progress;
  }

  /** If the active indicator is enabled, animate from it's current state to it's new state. */
  private void maybeAnimateActiveIndicatorToProgress(
          @FloatRange(from = 0F, to = 1F) final float newProgress) {
    // If the active indicator is disabled or this view is in the process of being initialized,
    // jump the active indicator to it's final state.
    if (!initialized || activeIndicatorView == null || !activeIndicatorView.isAttachedToWindow()) {
      setActiveIndicatorProgress(newProgress, newProgress);
      return;
    }

    if (activeIndicatorAnimator != null) {
      activeIndicatorAnimator.cancel();
      activeIndicatorAnimator = null;
    }
    activeIndicatorAnimator = ValueAnimator.ofFloat(activeIndicatorProgress, newProgress);
    activeIndicatorAnimator.addUpdateListener(animation -> {
      float progress = (float) animation.getAnimatedValue();
      setActiveIndicatorProgress(progress, newProgress);
    });
    activeIndicatorAnimator.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
    activeIndicatorAnimator.setDuration(400);
    activeIndicatorAnimator.start();
  }

  void setup(@StringRes int stringRes, @DrawableRes int drawableRes,
             @DrawableRes int drawableResSelected) {
    this.drawableRes = drawableRes;
    this.drawableResSelected = drawableResSelected;
    text.setText(stringRes);
    image.setImageResource(drawableRes);
  }

  void setNotificationCount(int count) {
    Assert.checkArgument(count >= 0, "Invalid count: " + count);
    if (count == 0) {
      notificationBadge.setVisibility(View.INVISIBLE);
    } else {
      String countString = String.format(Integer.toString(count));

      if (count > 9) {
        countString = getContext().getString(R.string.bottom_nav_count_9_plus);
      }
      notificationBadge.setVisibility(View.VISIBLE);
      notificationBadge.setText(countString);

      @Px int margin;
      if (countString.length() == 1) {
        margin = getContext().getResources().getDimensionPixelSize(R.dimen.badge_margin_length_1);
      } else if (countString.length() == 2) {
        margin = getContext().getResources().getDimensionPixelSize(R.dimen.badge_margin_length_2);
      } else {
        margin = getContext().getResources().getDimensionPixelSize(R.dimen.badge_margin_length_3);
      }

      FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) image.getLayoutParams();
      params.setMarginStart(margin);
      params.setMarginEnd(margin);
      image.setLayoutParams(params);
    }
  }

  /**
   * A class used to manipulate the {@link NavigationBarItemView}'s active indicator view when
   * animating between hidden and shown.
   *
   * <p>By default, this class scales the indicator in the x direction to reveal the default pill
   * shape.
   *
   * <p>Subclasses can override {@link #updateForProgress(float, float, View)} to manipulate the
   * view in any way appropriate.
   */
  private static class ActiveIndicatorTransform {

    private static final float SCALE_X_HIDDEN = .4F;
    private static final float SCALE_X_SHOWN = 1F;

    // The fraction of the animation's total duration over which the indicator will be faded in or
    // out.
    private static final float ALPHA_FRACTION = 1F / 5F;

    /**
     * Calculate the alpha value, based on a progress and target value, that has the indicator
     * appear or disappear over the first 1/5th of the transform.
     */
    protected float calculateAlpha(
            @FloatRange(from = 0F, to = 1F) float progress,
            @FloatRange(from = 0F, to = 1F) float targetValue) {
      // Animate the alpha of the indicator over the first ALPHA_FRACTION of the animation
      float startAlphaFraction = targetValue == 0F ? 1F - ALPHA_FRACTION : 0F;
      float endAlphaFraction = targetValue == 0F ? 1F : 0F + ALPHA_FRACTION;
      return MathUtil.lerp(0F, 1F, progress);
    }

    protected float calculateScaleX(
            @FloatRange(from = 0F, to = 1F) float progress,
            @FloatRange(from = 0F, to = 1F) float targetValue) {
      return MathUtil.lerp(SCALE_X_HIDDEN, SCALE_X_SHOWN, progress);
    }

    protected float calculateScaleY(
            @FloatRange(from = 0F, to = 1F) float progress,
            @FloatRange(from = 0F, to = 1F) float targetValue) {
      return 1F;
    }

    /**
     * Called whenever the {@code indicator} should update its parameters (scale, alpha, etc.) in
     * response to a change in progress.
     *
     * @param progress A value between 0 and 1 where 0 represents a fully hidden indicator and 1
     *     indicates a fully shown indicator.
     * @param targetValue The final value towards which the progress is moving. This will be either
     *     0 and 1 and can be used to determine whether the indicator is showing or hiding if show
     *     and hide animations differ.
     * @param indicator The active indicator {@link View}.
     */
    public void updateForProgress(
            @FloatRange(from = 0F, to = 1F) float progress,
            @FloatRange(from = 0F, to = 1F) float targetValue,
            @NonNull View indicator) {
      indicator.setScaleX(calculateScaleX(progress, targetValue));
      indicator.setScaleY(calculateScaleY(progress, targetValue));
      indicator.setAlpha(calculateAlpha(progress, targetValue));
    }
  }
}
