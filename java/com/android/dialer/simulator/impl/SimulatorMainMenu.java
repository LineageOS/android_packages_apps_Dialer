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
import android.provider.VoicemailContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.ActionProvider;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.databasepopulator.CallLogPopulator;
import com.android.dialer.databasepopulator.ContactsPopulator;
import com.android.dialer.databasepopulator.VoicemailPopulator;
import com.android.dialer.enrichedcall.simulator.EnrichedCallSimulatorActivity;
import com.android.dialer.persistentlog.PersistentLogger;
import com.android.dialer.preferredsim.PreferredSimFallbackContract;

/** Implements the top level simulator menu. */
final class SimulatorMainMenu {

  static ActionProvider getActionProvider(@NonNull Context context) {
    return new SimulatorSubMenu(context)
        .addItem("Voice call", SimulatorVoiceCall.getActionProvider(context))
        .addItem("IMS video", SimulatorVideoCall.getActionProvider(context))
        .addItem("Notifications", SimulatorNotifications.getActionProvider(context))
        .addItem("Populate database", () -> populateDatabase(context))
        .addItem("Clean database", () -> cleanDatabase(context))
        .addItem("clear preferred SIM", () -> clearPreferredSim(context))
        .addItem("Sync voicemail", () -> syncVoicemail(context))
        .addItem("Share persistent log", () -> sharePersistentLog(context))
        .addItem(
            "Enriched call simulator",
            () -> context.startActivity(EnrichedCallSimulatorActivity.newIntent(context)));
  }

  private static void populateDatabase(@NonNull Context context) {
    DialerExecutorComponent.get(context)
        .dialerExecutorFactory()
        .createNonUiTaskBuilder(new PopulateDatabaseWorker())
        .build()
        .executeSerial(context);
  }

  private static void cleanDatabase(@NonNull Context context) {
    DialerExecutorComponent.get(context)
        .dialerExecutorFactory()
        .createNonUiTaskBuilder(new CleanDatabaseWorker())
        .build()
        .executeSerial(context);
  }

  private static void clearPreferredSim(Context context) {
    DialerExecutorComponent.get(context)
        .dialerExecutorFactory()
        .createNonUiTaskBuilder(new ClearPreferredSimWorker())
        .build()
        .executeSerial(context);
  }

  private static void syncVoicemail(@NonNull Context context) {
    Intent intent = new Intent(VoicemailContract.ACTION_SYNC_VOICEMAIL);
    context.sendBroadcast(intent);
  }

  private static void sharePersistentLog(@NonNull Context context) {
    DialerExecutorComponent.get(context)
        .dialerExecutorFactory()
        .createNonUiTaskBuilder(new ShareLogWorker())
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
  }

  private SimulatorMainMenu() {}

  private static class PopulateDatabaseWorker implements Worker<Context, Void> {
    @Nullable
    @Override
    public Void doInBackground(Context context) {
      ContactsPopulator.populateContacts(context);
      CallLogPopulator.populateCallLog(context);
      VoicemailPopulator.populateVoicemail(context);
      return null;
    }
  }

  private static class CleanDatabaseWorker implements Worker<Context, Void> {
    @Nullable
    @Override
    public Void doInBackground(Context context) {
      ContactsPopulator.deleteAllContacts(context);
      CallLogPopulator.deleteAllCallLog(context);
      VoicemailPopulator.deleteAllVoicemail(context);
      return null;
    }
  }

  private static class ClearPreferredSimWorker implements Worker<Context, Void> {
    @Nullable
    @Override
    public Void doInBackground(Context context) {
      context.getContentResolver().delete(PreferredSimFallbackContract.CONTENT_URI, null, null);
      return null;
    }
  }

  private static class ShareLogWorker implements Worker<Void, String> {
    @Nullable
    @Override
    public String doInBackground(Void unused) {
      return PersistentLogger.dumpLogToString();
    }
  }
}
