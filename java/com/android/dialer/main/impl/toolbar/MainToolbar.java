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
import android.support.v7.widget.PopupMenu.OnMenuItemClickListener;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.MenuItem;
import android.widget.ImageButton;
import android.widget.Toast;

/** Toolbar for {@link com.android.dialer.main.impl.MainActivity}. */
public final class MainToolbar extends Toolbar implements OnMenuItemClickListener {

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

    findViewById(R.id.voice_search_button).setOnClickListener(v -> onVoiceIconClicked());
    findViewById(R.id.search_box_collapsed).setOnClickListener(v -> onSearchBarClicked());
  }

  @Override
  public boolean onMenuItemClick(MenuItem menuItem) {
    Toast.makeText(getContext(), "Not yet implemented", Toast.LENGTH_SHORT).show();
    // TODO(calderwoodra): implement menu item clicks
    return false;
  }

  private void onVoiceIconClicked() {
    // TODO(calderwoodra): take voice input
  }

  private void onSearchBarClicked() {
    // TODO(calderwoodra): open search UI
  }
}
