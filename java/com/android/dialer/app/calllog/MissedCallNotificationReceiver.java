/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.dialer.app.calllog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.util.Pair;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutors;
import me.leolin.shortcutbadger.ShortcutBadger;

/**
 * Receives broadcasts that should trigger a refresh of the missed call notification. This includes
 * both an explicit broadcast from Telecom and a reboot.
 */
public class MissedCallNotificationReceiver extends BroadcastReceiver {

  //TODO: Use compat class for these methods.
  public static final String ACTION_SHOW_MISSED_CALLS_NOTIFICATION =
      "android.telecom.action.SHOW_MISSED_CALLS_NOTIFICATION";

  public static final String EXTRA_NOTIFICATION_COUNT = "android.telecom.extra.NOTIFICATION_COUNT";

  public static final String EXTRA_NOTIFICATION_PHONE_NUMBER =
      "android.telecom.extra.NOTIFICATION_PHONE_NUMBER";

  @Override
  public void onReceive(Context context, Intent intent) {
    String action = intent.getAction();
    if (!ACTION_SHOW_MISSED_CALLS_NOTIFICATION.equals(action)) {
      return;
    }

    int count =
        intent.getIntExtra(
            EXTRA_NOTIFICATION_COUNT, CallLogNotificationsService.UNKNOWN_MISSED_CALL_COUNT);
    String phoneNumber = intent.getStringExtra(EXTRA_NOTIFICATION_PHONE_NUMBER);

    PendingResult pendingResult = goAsync();

    DialerExecutors.createNonUiTaskBuilder(MissedCallNotifier.getIstance(context))
        .onSuccess(
            output -> {
              LogUtil.i(
                  "MissedCallNotificationReceiver.onReceive",
                  "update missed call notifications successful");
              updateBadgeCount(context, count);
              pendingResult.finish();
            })
        .onFailure(
            throwable -> {
              LogUtil.i(
                  "MissedCallNotificationReceiver.onReceive",
                  "update missed call notifications failed");
              pendingResult.finish();
            })
        .build()
        .executeParallel(new Pair<>(count, phoneNumber));
  }

  private static void updateBadgeCount(Context context, int count) {
    boolean success = ShortcutBadger.applyCount(context, count);
    LogUtil.i(
        "MissedCallNotificationReceiver.updateBadgeCount",
        "update badge count: %d success: %b",
        count,
        success);
  }
}
