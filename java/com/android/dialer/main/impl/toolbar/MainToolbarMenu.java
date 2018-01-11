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
import android.support.v7.widget.PopupMenu;
import android.view.Gravity;
import android.view.View;

/** Popup menu accessible from the search bar */
final class MainToolbarMenu extends PopupMenu {

  public MainToolbarMenu(Context context, View anchor) {
    super(context, anchor, Gravity.TOP);
    // TODO(calderwoodra): menu should open from the top, not the bottom
  }

  @Override
  public void show() {
    super.show();
    // TODO(calderwoodra): show/hide clear frequents
    // TODO(calderwoodra): only show call history item if we have phone permission
    // TODO(calderwoodra): show simulator buttons
  }
}
