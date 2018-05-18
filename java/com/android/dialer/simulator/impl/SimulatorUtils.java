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

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.provider.VoicemailContract;
import android.provider.VoicemailContract.Voicemails;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.databasepopulator.BlockedBumberPopulator;
import com.android.dialer.databasepopulator.CallLogPopulator;
import com.android.dialer.databasepopulator.ContactsPopulator;
import com.android.dialer.databasepopulator.VoicemailPopulator;
import com.android.dialer.persistentlog.PersistentLogger;
import com.android.dialer.preferredsim.PreferredSimFallbackContract;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Contains utilities used often in test workflow. */
public class SimulatorUtils {

  public static final int NOTIFICATION_COUNT_FEW = 1;
  public static final int NOTIFICATION_COUNT = 12;

  /** Populates contacts database with predefined contacts entries. */
  public static void populateDatabase(@NonNull Context context) {
    DialerExecutorComponent.get(context)
        .dialerExecutorFactory()
        .createNonUiTaskBuilder(new PopulateDatabaseWorker())
        .build()
        .executeSerial(new PopulateDatabaseWorkerInput(context, false));
  }

  /** Populates voicemail database with predefined voicemail entries. */
  public static void populateVoicemail(@NonNull Context context) {
    DialerExecutorComponent.get(context)
        .dialerExecutorFactory()
        .createNonUiTaskBuilder(new PopulateVoicemailWorker())
        .build()
        .executeSerial(new PopulateDatabaseWorkerInput(context, false));
  }

  /** Populates voicemail database with only few predefined voicemail entries. */
  public static void populateVoicemailFast(@NonNull Context context) {
    DialerExecutorComponent.get(context)
        .dialerExecutorFactory()
        .createNonUiTaskBuilder(new PopulateVoicemailWorker())
        .build()
        .executeSerial(new PopulateDatabaseWorkerInput(context, true));
  }

  /** Populates contacts database with only few predefined contacts entries. */
  public static void fastPopulateDatabase(@NonNull Context context) {
    DialerExecutorComponent.get(context)
        .dialerExecutorFactory()
        .createNonUiTaskBuilder(new PopulateDatabaseWorker())
        .build()
        .executeSerial(new PopulateDatabaseWorkerInput(context, true));
  }

  /** Clean contacts database. */
  public static void cleanDatabase(@NonNull Context context) {
    DialerExecutorComponent.get(context)
        .dialerExecutorFactory()
        .createNonUiTaskBuilder(new CleanDatabaseWorker())
        .build()
        .executeSerial(context);
  }

  /** Clear preference over sim. */
  public static void clearPreferredSim(Context context) {
    DialerExecutorComponent.get(context)
        .dialerExecutorFactory()
        .createNonUiTaskBuilder(new ClearPreferredSimWorker())
        .build()
        .executeSerial(context);
  }

  /** Sync voicemail by sending intents to system. */
  public static void syncVoicemail(@NonNull Context context) {
    Intent intent = new Intent(VoicemailContract.ACTION_SYNC_VOICEMAIL);
    context.sendBroadcast(intent);
  }

  public static void sharePersistentLog(@NonNull Context context) {
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

  public static void addVoicemailNotifications(@NonNull Context context, int notificationNum) {
    LogUtil.enterBlock("SimulatorNotifications.addVoicemailNotifications");
    List<ContentValues> voicemails = new ArrayList<>();
    for (int i = notificationNum; i > 0; i--) {
      VoicemailPopulator.Voicemail voicemail =
          VoicemailPopulator.Voicemail.builder()
              .setPhoneNumber(String.format(Locale.ENGLISH, "+%d", i))
              .setTranscription(String.format(Locale.ENGLISH, "Short transcript %d", i))
              .setDurationSeconds(60)
              .setIsRead(false)
              .setPhoneAccountComponentName("")
              .setTimeMillis(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(i))
              .build();
      voicemails.add(voicemail.getAsContentValues(context));
    }
    context
        .getContentResolver()
        .bulkInsert(
            Voicemails.buildSourceUri(context.getPackageName()),
            voicemails.toArray(new ContentValues[voicemails.size()]));
  }

  private static class PopulateVoicemailWorker
      implements Worker<PopulateDatabaseWorkerInput, Void> {
    @Nullable
    @Override
    public Void doInBackground(PopulateDatabaseWorkerInput input) {
      VoicemailPopulator.populateVoicemail(input.context, input.fastMode);
      return null;
    }
  }

  private static class PopulateDatabaseWorker implements Worker<PopulateDatabaseWorkerInput, Void> {
    @Nullable
    @Override
    public Void doInBackground(PopulateDatabaseWorkerInput input) {
      ContactsPopulator.populateContacts(input.context, input.fastMode);
      CallLogPopulator.populateCallLog(input.context, false, input.fastMode);
      VoicemailPopulator.populateVoicemail(input.context, input.fastMode);
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
      BlockedBumberPopulator.deleteBlockedNumbers(context);
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

  private static class PopulateDatabaseWorkerInput {
    Context context;
    boolean fastMode;

    PopulateDatabaseWorkerInput(Context context, boolean fastMode) {
      this.context = context;
      this.fastMode = fastMode;
    }
  }
}
