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
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import com.android.dialer.simulator.Simulator;
import com.android.dialer.simulator.SimulatorComponent;

/** Popup menu accessible from the search bar */
public final class MainToolbarMenu extends PopupMenu {

  public MainToolbarMenu(Context context, View anchor) {
    super(context, anchor, Gravity.NO_GRAVITY, R.attr.actionOverflowMenuStyle, 0);
  }

  public void showClearFrequents(boolean show) {
    getMenu().findItem(R.id.clear_frequents).setVisible(show);
  }

  public void maybeShowSimulator(AppCompatActivity activity) {
    MenuItem simulatorMenuItem = getMenu().findItem(R.id.menu_simulator_submenu);
    Simulator simulator = SimulatorComponent.get(activity).getSimulator();
    if (simulator.shouldShow()) {
      simulatorMenuItem.setVisible(true);
      simulatorMenuItem.setActionProvider(simulator.getActionProvider(activity));
    } else {
      simulatorMenuItem.setVisible(false);
    }
  }
}
