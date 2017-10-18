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

import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.OperationApplicationException;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.CallLog;
import android.support.annotation.NonNull;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.spam.Spam;
import com.android.dialer.spam.SpamBindingsStub;
import java.util.ArrayList;

/**
 * Creates many spam call notifications by adding new incoming calls one at a time and disconnecting
 * them.
 */
final class SimulatorSpamCallCreator implements SimulatorConnectionService.Listener {
  private static final String EXTRA_CALL_COUNT = "call_count";
  private static final String EXTRA_IS_SPAM_CALL_CONNECTION = "is_spam_call_connection";
  private static final int DISCONNECT_DELAY_MILLIS = 1000;

  private final Context context;
  private final boolean isSpam;

  SimulatorSpamCallCreator(@NonNull Context context, boolean isSpam) {
    this.context = Assert.isNotNull(context);
    this.isSpam = isSpam;
  }

  public void start(int callCount) {
    Spam.setForTesting(new SimulatorSpamBindings(isSpam));
    SimulatorConnectionService.addListener(this);
    addNextIncomingCall(callCount);
  }

  @Override
  public void onNewOutgoingConnection(@NonNull SimulatorConnection connection) {}

  @Override
  public void onNewIncomingConnection(@NonNull SimulatorConnection connection) {
    if (!isSpamCallConnection(connection)) {
      return;
    }
    ThreadUtil.postDelayedOnUiThread(
        () -> {
          LogUtil.i("SimulatorSpamCallCreator.onNewIncomingConnection", "disconnecting");
          connection.setActive();
          connection.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
          ThreadUtil.postDelayedOnUiThread(
              () -> addNextIncomingCall(getCallCount(connection)), DISCONNECT_DELAY_MILLIS);
        },
        DISCONNECT_DELAY_MILLIS);
  }

  @Override
  public void onConference(
      @NonNull SimulatorConnection connection1, @NonNull SimulatorConnection connection2) {}

  private void addNextIncomingCall(int callCount) {
    if (callCount <= 0) {
      LogUtil.i("SimulatorSpamCallCreator.addNextIncomingCall", "done adding calls");
      SimulatorConnectionService.removeListener(this);
      Spam.setForTesting(null);
      return;
    }

    // Caller ID must be e.164 formatted and unique.
    String callerId = String.format("+1-650-234%04d", callCount);
    Bundle extras = new Bundle();
    extras.putInt(EXTRA_CALL_COUNT, callCount - 1);
    extras.putBoolean(EXTRA_IS_SPAM_CALL_CONNECTION, true);

    // We need to clear the call log because spam notifications are only shown for new calls.
    clearCallLog(context);

    SimulatorSimCallManager.addNewIncomingCall(context, callerId, false /* isVideo */, extras);
  }

  private static boolean isSpamCallConnection(@NonNull Connection connection) {
    return connection.getExtras().getBoolean(EXTRA_IS_SPAM_CALL_CONNECTION);
  }

  private static int getCallCount(@NonNull Connection connection) {
    return connection.getExtras().getInt(EXTRA_CALL_COUNT);
  }

  private static void clearCallLog(@NonNull Context context) {
    try {
      ArrayList<ContentProviderOperation> operations = new ArrayList<>();
      operations.add(ContentProviderOperation.newDelete(CallLog.Calls.CONTENT_URI).build());
      context.getContentResolver().applyBatch(CallLog.AUTHORITY, operations);
    } catch (RemoteException | OperationApplicationException e) {
      Assert.fail("failed to clear call log: " + e);
    }
  }

  /**
   * Custom spam bindings that allow us to override which phone numbers are considered to be spam.
   * Also disables throttling of spam notifications.
   */
  private static class SimulatorSpamBindings extends SpamBindingsStub {
    private final boolean isSpam;

    SimulatorSpamBindings(boolean isSpam) {
      this.isSpam = isSpam;
    }

    @Override
    public boolean isSpamEnabled() {
      return true;
    }

    @Override
    public boolean isSpamNotificationEnabled() {
      return true;
    }

    @Override
    public int percentOfSpamNotificationsToShow() {
      return 100;
    }

    @Override
    public int percentOfNonSpamNotificationsToShow() {
      return 100;
    }

    @Override
    public void checkSpamStatus(String number, String countryIso, Listener listener) {
      listener.onComplete(isSpam);
    }
  }
}
