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

package com.android.dialer.simulator.impl;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.ActionProvider;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import com.android.dialer.common.Assert;
import java.util.ArrayList;
import java.util.List;

/** Makes it easier to create submenus in the simulator. */
final class SimulatorSubMenu extends ActionProvider {
  List<Item> items = new ArrayList<>();

  SimulatorSubMenu(@NonNull Context context) {
    super(Assert.isNotNull(context));
  }

  SimulatorSubMenu addItem(@NonNull String title, @NonNull Runnable clickHandler) {
    items.add(new Item(title, clickHandler));
    return this;
  }

  SimulatorSubMenu addItem(@NonNull String title, @NonNull ActionProvider actionProvider) {
    items.add(new Item(title, actionProvider));
    return this;
  }

  @Override
  public View onCreateActionView() {
    return null;
  }

  @Override
  public View onCreateActionView(MenuItem forItem) {
    return null;
  }

  @Override
  public boolean hasSubMenu() {
    return true;
  }

  @Override
  public void onPrepareSubMenu(SubMenu subMenu) {
    super.onPrepareSubMenu(subMenu);
    subMenu.clear();

    for (Item item : items) {
      if (item.clickHandler != null) {
        subMenu
            .add(item.title)
            .setOnMenuItemClickListener(
                (i) -> {
                  item.clickHandler.run();
                  return true;
                });
      } else {
        subMenu.add(item.title).setActionProvider(item.actionProvider);
      }
    }
  }

  private static final class Item {
    @NonNull final String title;
    @Nullable final Runnable clickHandler;
    @Nullable final ActionProvider actionProvider;

    Item(@NonNull String title, @NonNull Runnable clickHandler) {
      this.title = Assert.isNotNull(title);
      this.clickHandler = Assert.isNotNull(clickHandler);
      actionProvider = null;
    }

    Item(@NonNull String title, @NonNull ActionProvider actionProvider) {
      this.title = Assert.isNotNull(title);
      this.clickHandler = null;
      this.actionProvider = Assert.isNotNull(actionProvider);
    }
  }
}
