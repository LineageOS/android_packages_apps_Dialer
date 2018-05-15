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
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.ThreadUtil;

/**
 * Shows missed call notifications. Note, we explicilty create fake phone calls to trigger these
 * notifications instead of writing to the call log directly. This makes the simulator behave more
 * like the real application.
 */
final class SimulatorMissedCallCreator implements SimulatorConnectionService.Listener {
  private static final String EXTRA_CALL_COUNT = "call_count";
  private static final String EXTRA_IS_MISSED_CALL_CONNECTION = "is_missed_call_connection";
  private static final int DISCONNECT_DELAY_MILLIS = 1000;

  private final Context context;

  SimulatorMissedCallCreator(@NonNull Context context) {
    this.context = Assert.isNotNull(context);
  }

  public void start(int callCount) {
    SimulatorConnectionService.addListener(this);
    addNextIncomingCall(callCount);
  }

  @Override
  public void onNewOutgoingConnection(@NonNull SimulatorConnection connection) {}

  @Override
  public void onNewIncomingConnection(@NonNull SimulatorConnection connection) {
    if (!isMissedCallConnection(connection)) {
      return;
    }
    ThreadUtil.postDelayedOnUiThread(
        () -> {
          connection.setDisconnected(new DisconnectCause(DisconnectCause.MISSED));
          addNextIncomingCall(getCallCount(connection));
        },
        DISCONNECT_DELAY_MILLIS);
  }

  @Override
  public void onConference(
      @NonNull SimulatorConnection connection1, @NonNull SimulatorConnection connection2) {}

  private void addNextIncomingCall(int callCount) {
    if (callCount <= 0) {
      LogUtil.i("SimulatorMissedCallCreator.addNextIncomingCall", "done adding calls");
      SimulatorConnectionService.removeListener(this);
      return;
    }

    String callerId = String.format("+%d", callCount);
    Bundle extras = new Bundle();
    extras.putInt(EXTRA_CALL_COUNT, callCount - 1);
    extras.putBoolean(EXTRA_IS_MISSED_CALL_CONNECTION, true);

    SimulatorSimCallManager.addNewIncomingCall(
        context, callerId, SimulatorSimCallManager.CALL_TYPE_VOICE, extras);
  }

  private static boolean isMissedCallConnection(@NonNull Connection connection) {
    return connection.getExtras().getBoolean(EXTRA_IS_MISSED_CALL_CONNECTION);
  }

  private static int getCallCount(@NonNull Connection connection) {
    return connection.getExtras().getInt(EXTRA_CALL_COUNT);
  }
}
