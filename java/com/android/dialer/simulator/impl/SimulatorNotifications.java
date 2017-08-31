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
import android.provider.VoicemailContract.Voicemails;
import android.support.annotation.NonNull;
import android.view.ActionProvider;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.databasepopulator.VoicemailPopulator;
import java.util.concurrent.TimeUnit;

/** Implements the simulator submenu. */
final class SimulatorNotifications {
  private static final int NOTIFICATION_COUNT = 12;

  static ActionProvider getActionProvider(@NonNull Context context) {
    return new NotificationsActionProvider(context);
  }

  private static class NotificationsActionProvider extends ActionProvider {
    @NonNull private final Context context;

    public NotificationsActionProvider(@NonNull Context context) {
      super(Assert.isNotNull(context));
      this.context = context;
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
    public void onPrepareSubMenu(@NonNull SubMenu subMenu) {
      LogUtil.enterBlock("NotificationsActionProvider.onPrepareSubMenu");
      Assert.isNotNull(subMenu);
      super.onPrepareSubMenu(subMenu);

      subMenu.clear();
      subMenu
          .add("Missed Calls")
          .setOnMenuItemClickListener(
              (item) -> {
                new SimulatorMissedCallCreator(context).start(NOTIFICATION_COUNT);
                return true;
              });
      subMenu
          .add("Voicemails")
          .setOnMenuItemClickListener(
              (item) -> {
                addVoicemailNotifications(context);
                return true;
              });
      subMenu
          .add("Non spam")
          .setOnMenuItemClickListener(
              (item) -> {
                new SimulatorSpamCallCreator(context, false /* isSpam */).start(NOTIFICATION_COUNT);
                return true;
              });
      subMenu
          .add("Confirm spam")
          .setOnMenuItemClickListener(
              (item) -> {
                new SimulatorSpamCallCreator(context, true /* isSpam */).start(NOTIFICATION_COUNT);
                return true;
              });
    }
  }

  private static void addVoicemailNotifications(@NonNull Context context) {
    LogUtil.enterBlock("SimulatorNotifications.addVoicemailNotifications");
    for (int i = NOTIFICATION_COUNT; i > 0; i--) {
      VoicemailPopulator.Voicemail voicemail =
          VoicemailPopulator.Voicemail.builder()
              .setPhoneNumber(String.format("+%d", i))
              .setTranscription(String.format("Short transcript %d", i))
              .setDurationSeconds(60)
              .setIsRead(false)
              .setTimeMillis(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(i))
              .build();
      context
          .getContentResolver()
          .insert(
              Voicemails.buildSourceUri(context.getPackageName()),
              voicemail.getAsContentValues(context));
    }
  }
}
