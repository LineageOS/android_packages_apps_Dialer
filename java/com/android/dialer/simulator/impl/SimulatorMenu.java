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

package com.android.dialer.simulator.impl;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.ActionProvider;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import com.android.dialer.common.Assert;
import java.util.Map.Entry;

/** Makes option menu for simulator. */
public final class SimulatorMenu extends ActionProvider {

  SimulatorPortalEntryGroup portal;

  Context context;

  public SimulatorMenu(@NonNull Context context, SimulatorPortalEntryGroup portal) {
    super(Assert.isNotNull(context));
    this.context = context;
    this.portal = portal;
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

    for (Entry<String, SimulatorPortalEntryGroup> subPortal : portal.subPortals().entrySet()) {
      subMenu
          .add(subPortal.getKey())
          .setActionProvider(new SimulatorMenu(context, subPortal.getValue()));
    }
    for (Entry<String, Runnable> method : portal.methods().entrySet()) {
      subMenu
          .add(method.getKey())
          .setOnMenuItemClickListener(
              (i) -> {
                method.getValue().run();
                return true;
              });
    }
  }
}
