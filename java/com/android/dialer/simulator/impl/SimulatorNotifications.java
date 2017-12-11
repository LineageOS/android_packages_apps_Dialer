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

import android.content.ContentValues;
import android.content.Context;
import android.provider.VoicemailContract.Voicemails;
import android.support.annotation.NonNull;
import android.view.ActionProvider;
import com.android.dialer.common.LogUtil;
import com.android.dialer.databasepopulator.VoicemailPopulator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Implements the simulator submenu. */
final class SimulatorNotifications {
  private static final int NOTIFICATION_COUNT = 12;

  static ActionProvider getActionProvider(@NonNull Context context) {
    return new SimulatorSubMenu(context)
        .addItem(
            "Missed calls", () -> new SimulatorMissedCallCreator(context).start(NOTIFICATION_COUNT))
        .addItem("Voicemails", () -> addVoicemailNotifications(context));
  }

  private static void addVoicemailNotifications(@NonNull Context context) {
    LogUtil.enterBlock("SimulatorNotifications.addVoicemailNotifications");
    List<ContentValues> voicemails = new ArrayList<>();
    for (int i = NOTIFICATION_COUNT; i > 0; i--) {
      VoicemailPopulator.Voicemail voicemail =
          VoicemailPopulator.Voicemail.builder()
              .setPhoneNumber(String.format("+%d", i))
              .setTranscription(String.format("Short transcript %d", i))
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
}
