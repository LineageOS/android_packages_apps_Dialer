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
import android.content.Intent;
import android.os.AsyncTask;
import android.provider.VoicemailContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.ActionProvider;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutors;
import com.android.dialer.enrichedcall.simulator.EnrichedCallSimulatorActivity;
import com.android.dialer.persistentlog.PersistentLogger;

/** Implements the simulator submenu. */
final class SimulatorActionProvider extends ActionProvider {
  @NonNull private final Context context;

  private static class ShareLogWorker implements Worker<Void, String> {

    @Nullable
    @Override
    public String doInBackground(Void unused) {
      return PersistentLogger.dumpLogToString();
    }
  }

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
    subMenu
        .add("Sync Voicemail")
        .setOnMenuItemClickListener(
            (item) -> {
              Intent intent = new Intent(VoicemailContract.ACTION_SYNC_VOICEMAIL);
              context.sendBroadcast(intent);
              return true;
            });

    subMenu
        .add("Share persistent log")
        .setOnMenuItemClickListener(
            (item) -> {
              DialerExecutors.createNonUiTaskBuilder(new ShareLogWorker())
                  .onSuccess(
                      (String log) -> {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, log);
                        if (intent.resolveActivity(context.getPackageManager()) != null) {
                          context.startActivity(intent);
                        }
                      })
                  .build()
                  .executeSerial(null);
              return true;
            });
    subMenu
        .add("Enriched call simulator")
        .setOnMenuItemClickListener(
            (item) -> {
              context.startActivity(EnrichedCallSimulatorActivity.newIntent(context));
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
