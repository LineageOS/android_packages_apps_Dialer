/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.main.impl.toolbar;

import android.content.Context;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.PopupMenu.OnMenuItemClickListener;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.MenuItem;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageButton;
import com.android.dialer.common.Assert;

/** Toolbar for {@link com.android.dialer.main.impl.MainActivity}. */
public final class MainToolbar extends Toolbar implements OnMenuItemClickListener {

  private static final int SLIDE_DURATION = 300;
  private static final AccelerateDecelerateInterpolator SLIDE_INTERPOLATOR =
      new AccelerateDecelerateInterpolator();

  private SearchBarListener listener;
  private boolean isSlideUp;

  public MainToolbar(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    ImageButton optionsMenuButton = findViewById(R.id.main_options_menu_button);
    MainToolbarMenu overflowMenu = new MainToolbarMenu(getContext(), optionsMenuButton);
    overflowMenu.inflate(R.menu.main_menu);
    overflowMenu.setOnMenuItemClickListener(this);
    optionsMenuButton.setOnClickListener(v -> overflowMenu.show());
    optionsMenuButton.setOnTouchListener(overflowMenu.getDragToOpenListener());
  }

  @Override
  public boolean onMenuItemClick(MenuItem menuItem) {
    if (menuItem.getItemId() == R.id.settings) {
      listener.openSettings();
    } else if (menuItem.getItemId() == R.id.feedback) {
      listener.sendFeedback();
    }
    return false;
  }

  public void setSearchBarListener(SearchBarListener listener) {
    this.listener = listener;
    ((SearchBarView) findViewById(R.id.search_view_container)).setSearchBarListener(listener);
  }

  /** Slides the toolbar up and off the screen. */
  public void slideUp(boolean animate) {
    Assert.checkArgument(!isSlideUp);
    isSlideUp = true;
    animate()
        .translationY(-getHeight())
        .setDuration(animate ? SLIDE_DURATION : 0)
        .setInterpolator(SLIDE_INTERPOLATOR)
        .start();
  }

  /**
   * Slides the toolbar down and back onto the screen.
   *
   * @param animate
   */
  public void slideDown(boolean animate) {
    Assert.checkArgument(isSlideUp);
    isSlideUp = false;
    animate()
        .translationY(0)
        .setDuration(animate ? SLIDE_DURATION : 0)
        .setInterpolator(SLIDE_INTERPOLATOR)
        .start();
  }

  @VisibleForTesting
  public boolean isSlideUp() {
    return isSlideUp;
  }
}
