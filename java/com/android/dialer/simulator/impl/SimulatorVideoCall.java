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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.TelecomManager;
import android.widget.Toast;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.simulator.Simulator.Event;

/** Entry point in the simulator to create video calls. */
final class SimulatorVideoCall
    implements SimulatorConnectionService.Listener, SimulatorConnection.Listener {
  @NonNull private final Context context;
  private final int initialVideoCapability;
  private final int initialVideoState;
  @Nullable private String connectionTag;

  SimulatorVideoCall(@NonNull Context context, int initialVideoState) {
    this.context = Assert.isNotNull(context);
    this.initialVideoCapability =
        Connection.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL
            | Connection.CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL;
    this.initialVideoState = initialVideoState;
    SimulatorConnectionService.addListener(this);
  }

  void addNewIncomingCall() {
    if (!isVideoAccountEnabled()) {
      showVideoAccountSettings();
      return;
    }
    String callerId = "+44 (0) 20 7031 3000"; // Google London office
    connectionTag =
        SimulatorSimCallManager.addNewIncomingCall(
            context, callerId, SimulatorSimCallManager.CALL_TYPE_VIDEO);
  }

  void addNewOutgoingCall() {
    if (!isVideoAccountEnabled()) {
      showVideoAccountSettings();
      return;
    }
    String phoneNumber = "+44 (0) 20 7031 3000"; // Google London office
    connectionTag =
        SimulatorSimCallManager.addNewOutgoingCall(
            context, phoneNumber, SimulatorSimCallManager.CALL_TYPE_VIDEO);
  }

  @Override
  public void onNewOutgoingConnection(@NonNull SimulatorConnection connection) {
    if (connection.getExtras().getBoolean(connectionTag)) {
      LogUtil.i("SimulatorVideoCall.onNewOutgoingConnection", "connection created");
      handleNewConnection(connection);
      // Telecom will force the connection to switch to Dialing when we return it. Wait until after
      // we're returned it before changing call state.
      ThreadUtil.postOnUiThread(() -> connection.setActive());
    }
  }

  @Override
  public void onNewIncomingConnection(@NonNull SimulatorConnection connection) {
    if (connection.getExtras().getBoolean(connectionTag)) {
      LogUtil.i("SimulatorVideoCall.onNewIncomingConnection", "connection created");
      handleNewConnection(connection);
    }
  }

  @Override
  public void onConference(
      @NonNull SimulatorConnection connection1, @NonNull SimulatorConnection connection2) {}

  private boolean isVideoAccountEnabled() {
    SimulatorSimCallManager.register(context);
    return context
        .getSystemService(TelecomManager.class)
        .getPhoneAccount(SimulatorSimCallManager.getVideoProviderHandle(context))
        .isEnabled();
  }

  private void showVideoAccountSettings() {
    Intent intent = new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
    Toast.makeText(context, "Please enable simulator video provider", Toast.LENGTH_LONG).show();
  }

  private void handleNewConnection(@NonNull SimulatorConnection connection) {
    connection.addListener(this);
    connection.setConnectionCapabilities(
        connection.getConnectionCapabilities() | initialVideoCapability);
    connection.setVideoState(initialVideoState);
  }

  @Override
  public void onEvent(@NonNull SimulatorConnection connection, @NonNull Event event) {
    switch (event.type) {
      case Event.NONE:
        throw Assert.createIllegalStateFailException();
      case Event.ANSWER:
        connection.setVideoState(Integer.parseInt(event.data1));
        connection.setActive();
        break;
      case Event.REJECT:
        connection.setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
        break;
      case Event.HOLD:
        connection.setOnHold();
        break;
      case Event.UNHOLD:
        connection.setActive();
        break;
      case Event.DISCONNECT:
        connection.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        break;
      case Event.SESSION_MODIFY_REQUEST:
        ThreadUtil.postDelayedOnUiThread(() -> connection.handleSessionModifyRequest(event), 2000);
        break;
      default:
        LogUtil.i("SimulatorVideoCall.onEvent", "unexpected event: " + event.type);
        break;
    }
  }
}
