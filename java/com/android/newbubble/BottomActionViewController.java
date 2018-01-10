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
package com.android.newbubble;

import android.content.Context;
import android.graphics.PixelFormat;
import android.support.v4.os.BuildCompat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.animation.LinearInterpolator;

/** Controller for showing and hiding bubble bottom action view. */
final class BottomActionViewController {

  // This delay controls how long to wait before we show the target when the user first moves
  // the bubble, to prevent the bottom action view from animating if the user just wants to fling
  // the bubble.
  private static final int SHOW_TARGET_DELAY = 100;
  private static final int SHOW_HIDE_TARGET_DURATION = 175;
  private static final int HIGHLIGHT_TARGET_DURATION = 150;
  private static final float HIGHLIGHT_TARGET_SCALE = 1.5f;
  private static final float UNHIGHLIGHT_TARGET_ALPHA = 0.38f;

  private final Context context;
  private final WindowManager windowManager;
  private final int gradientHeight;
  private final int bottomActionViewTop;
  private final int textOffsetSize;

  private View bottomActionView;
  private View dismissView;
  private View endCallView;

  private boolean dismissHighlighted;
  private boolean endCallHighlighted;

  public BottomActionViewController(Context context) {
    this.context = context;
    windowManager = context.getSystemService(WindowManager.class);
    gradientHeight =
        context.getResources().getDimensionPixelSize(R.dimen.bubble_bottom_action_view_height);
    bottomActionViewTop = context.getResources().getDisplayMetrics().heightPixels - gradientHeight;
    textOffsetSize =
        context.getResources().getDimensionPixelSize(R.dimen.bubble_bottom_action_text_offset);
  }

  /** Creates and show the bottom action view. */
  public void createAndShowBottomActionView() {
    if (bottomActionView != null) {
      return;
    }

    // Create a new view for the dismiss target
    bottomActionView = LayoutInflater.from(context).inflate(R.layout.bottom_action_base, null);
    bottomActionView.setAlpha(0);

    // Sub views
    dismissView = bottomActionView.findViewById(R.id.bottom_action_dismiss_layout);
    endCallView = bottomActionView.findViewById(R.id.bottom_action_end_call_layout);

    // Add the target to the window
    // TODO(yueg): use TYPE_NAVIGATION_BAR_PANEL to draw over navigation bar
    LayoutParams layoutParams =
        new LayoutParams(
            LayoutParams.MATCH_PARENT,
            gradientHeight,
            0,
            bottomActionViewTop,
            BuildCompat.isAtLeastO()
                ? LayoutParams.TYPE_APPLICATION_OVERLAY
                : LayoutParams.TYPE_SYSTEM_OVERLAY,
            LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | LayoutParams.FLAG_NOT_TOUCHABLE
                | LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
    layoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
    windowManager.addView(bottomActionView, layoutParams);
    bottomActionView.setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN);
    bottomActionView
        .getRootView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);

    // Shows the botton action view
    bottomActionView
        .animate()
        .alpha(1f)
        .setInterpolator(new LinearInterpolator())
        .setStartDelay(SHOW_TARGET_DELAY)
        .setDuration(SHOW_HIDE_TARGET_DURATION)
        .start();
  }

  /** Hides and destroys the bottom action view. */
  public void destroyBottomActionView() {
    if (bottomActionView == null) {
      return;
    }
    bottomActionView
        .animate()
        .alpha(0f)
        .setInterpolator(new LinearInterpolator())
        .setDuration(SHOW_HIDE_TARGET_DURATION)
        .withEndAction(
            () -> {
              // Use removeViewImmediate instead of removeView to avoid view flashing before removed
              windowManager.removeViewImmediate(bottomActionView);
              bottomActionView = null;
            })
        .start();
  }

  /**
   * Change highlight state of dismiss view and end call view according to current touch point.
   * Highlight the view with touch point moving into its boundary. Unhighlight the view with touch
   * point moving out of its boundary.
   *
   * @param x x position of current touch point
   * @param y y position of current touch point
   */
  public void highlightIfHover(float x, float y) {
    if (bottomActionView == null) {
      return;
    }
    final int middle = context.getResources().getDisplayMetrics().widthPixels / 2;
    boolean shouldHighlightDismiss = y > bottomActionViewTop && x < middle;
    boolean shouldHighlightEndCall = y > bottomActionViewTop && x >= middle;

    // Set target alpha back to 1
    if (!dismissHighlighted && endCallHighlighted && !shouldHighlightEndCall) {
      dismissView.animate().alpha(1f).setDuration(HIGHLIGHT_TARGET_DURATION).start();
    }
    if (!endCallHighlighted && dismissHighlighted && !shouldHighlightDismiss) {
      endCallView.animate().alpha(1f).setDuration(HIGHLIGHT_TARGET_DURATION).start();
    }

    // Scale unhighlight target back to 1x
    if (!shouldHighlightDismiss && dismissHighlighted) {
      // Unhighlight dismiss
      dismissView.animate().scaleX(1f).scaleY(1f).setDuration(HIGHLIGHT_TARGET_DURATION).start();
      dismissHighlighted = false;
    } else if (!shouldHighlightEndCall && endCallHighlighted) {
      // Unhighlight end call
      endCallView.animate().scaleX(1f).scaleY(1f).setDuration(HIGHLIGHT_TARGET_DURATION).start();
      endCallHighlighted = false;
    }

    // Scale highlight target larger
    if (shouldHighlightDismiss && !dismissHighlighted) {
      // Highlight dismiss
      dismissView.setPivotY(dismissView.getHeight() / 2 + textOffsetSize);
      dismissView
          .animate()
          .scaleX(HIGHLIGHT_TARGET_SCALE)
          .scaleY(HIGHLIGHT_TARGET_SCALE)
          .setDuration(HIGHLIGHT_TARGET_DURATION)
          .start();
      // Fade the other target
      endCallView
          .animate()
          .alpha(UNHIGHLIGHT_TARGET_ALPHA)
          .setDuration(HIGHLIGHT_TARGET_DURATION)
          .start();
      dismissHighlighted = true;
    } else if (shouldHighlightEndCall && !endCallHighlighted) {
      // Highlight end call
      endCallView.setPivotY(dismissView.getHeight() / 2 + textOffsetSize);
      endCallView
          .animate()
          .scaleX(HIGHLIGHT_TARGET_SCALE)
          .scaleY(HIGHLIGHT_TARGET_SCALE)
          .setDuration(HIGHLIGHT_TARGET_DURATION)
          .start();
      // Fade the other target
      dismissView
          .animate()
          .alpha(UNHIGHLIGHT_TARGET_ALPHA)
          .setDuration(HIGHLIGHT_TARGET_DURATION)
          .start();
      endCallHighlighted = true;
    }
  }

  public boolean isDismissHighlighted() {
    return dismissHighlighted;
  }

  public boolean isEndCallHighlighted() {
    return endCallHighlighted;
  }
}
