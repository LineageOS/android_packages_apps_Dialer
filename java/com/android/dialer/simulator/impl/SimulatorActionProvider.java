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
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.view.ActionProvider;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;

/** Implements the simulator submenu. */
final class SimulatorActionProvider extends ActionProvider {
  @NonNull private final Context context;

  public SimulatorActionProvider(@NonNull Context context) {
    super(Assert.isNotNull(context));
    this.context = context;
  }

  @Override
  public View onCreateActionView() {
    LogUtil.enterBlock("SimulatorActionProvider.onCreateActionView(null)");
    return null;
  }

  @Override
  public View onCreateActionView(MenuItem forItem) {
    LogUtil.enterBlock("SimulatorActionProvider.onCreateActionView(MenuItem)");
    return null;
  }

  @Override
  public boolean hasSubMenu() {
    LogUtil.enterBlock("SimulatorActionProvider.hasSubMenu");
    return true;
  }

  @Override
  public void onPrepareSubMenu(SubMenu subMenu) {
    super.onPrepareSubMenu(subMenu);
    LogUtil.enterBlock("SimulatorActionProvider.onPrepareSubMenu");
    subMenu.clear();
    subMenu
        .add("Add call")
        .setOnMenuItemClickListener(
            (item) -> {
              SimulatorVoiceCall.addNewIncomingCall(context);
              return true;
            });
    subMenu
        .add("Populate database")
        .setOnMenuItemClickListener(
            (item) -> {
              populateDatabase();
              return true;
            });
  }

  private void populateDatabase() {
    new AsyncTask<Void, Void, Void>() {
      @Override
      public Void doInBackground(Void... params) {
        SimulatorContacts.populateContacts(context);
        SimulatorCallLog.populateCallLog(context);
        SimulatorVoicemail.populateVoicemail(context);
        return null;
      }
    }.execute();
  }
}
