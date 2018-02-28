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

import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.widget.Toast;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.simulator.Simulator;
import com.android.dialer.simulator.SimulatorComponent;
import com.android.dialer.simulator.SimulatorConnectionsBank;
import java.util.ArrayList;
import java.util.List;

/** Simple connection provider to create phone calls. This is useful for emulators. */
public class SimulatorConnectionService extends ConnectionService {
  private static final List<Listener> listeners = new ArrayList<>();
  private static SimulatorConnectionService instance;
  private SimulatorConnectionsBank simulatorConnectionsBank;

  public static SimulatorConnectionService getInstance() {
    return instance;
  }

  public static void addListener(@NonNull Listener listener) {
    listeners.add(Assert.isNotNull(listener));
  }

  public static void removeListener(@NonNull Listener listener) {
    listeners.remove(Assert.isNotNull(listener));
  }

  @Override
  public void onCreate() {
    super.onCreate();
    instance = this;
    simulatorConnectionsBank = SimulatorComponent.get(this).getSimulatorConnectionsBank();
  }

  @Override
  public void onDestroy() {
    LogUtil.enterBlock("SimulatorConnectionService.onDestroy");
    instance = null;
    simulatorConnectionsBank = null;
    super.onDestroy();
  }

  @Override
  public Connection onCreateOutgoingConnection(
      PhoneAccountHandle phoneAccount, ConnectionRequest request) {
    LogUtil.enterBlock("SimulatorConnectionService.onCreateOutgoingConnection");
    if (!SimulatorComponent.get(this).getSimulator().isSimulatorMode()
        && !SimulatorSimCallManager.isSimulatorConnectionRequest(request)) {
      LogUtil.i(
          "SimulatorConnectionService.onCreateOutgoingConnection",
          "outgoing call not from simulator, unregistering");
      Toast.makeText(this, "Unregistering simulator, making a real phone call", Toast.LENGTH_LONG)
          .show();
      SimulatorSimCallManager.unregister(this);
      return null;
    }
    SimulatorConnection connection = new SimulatorConnection(this, request);
    if (SimulatorSimCallManager.isSimulatorConnectionRequest(request)) {
      simulatorConnectionsBank.add(connection);
      connection.setAddress(
          request.getAddress(),
          request
              .getExtras()
              .getInt(Simulator.PRESENTATION_CHOICE, TelecomManager.PRESENTATION_ALLOWED));
      connection.setDialing();
      ThreadUtil.postOnUiThread(
          () ->
              SimulatorComponent.get(instance)
                  .getSimulatorConnectionsBank()
                  .updateConferenceableConnections());
      for (Listener listener : listeners) {
        listener.onNewOutgoingConnection(connection);
      }
    } else {
      connection.setAddress(request.getAddress(), 1);
      Bundle extras = connection.getExtras();
      extras.putString("connection_tag", "SimulatorMode");
      connection.putExtras(extras);
      simulatorConnectionsBank.add(connection);
      connection.addListener(new NonSimulatorConnectionListener());
      connection.setDialing();
      ThreadUtil.postOnUiThread(connection::setActive);
    }
    return connection;
  }

  @Override
  public Connection onCreateIncomingConnection(
      PhoneAccountHandle phoneAccount, ConnectionRequest request) {
    LogUtil.enterBlock("SimulatorConnectionService.onCreateIncomingConnection");
    if (!SimulatorSimCallManager.isSimulatorConnectionRequest(request)) {
      LogUtil.i(
          "SimulatorConnectionService.onCreateIncomingConnection",
          "incoming call not from simulator, unregistering");
      Toast.makeText(this, "Unregistering simulator, got a real incoming call", Toast.LENGTH_LONG)
          .show();
      SimulatorSimCallManager.unregister(this);
      return null;
    }
    SimulatorConnection connection = new SimulatorConnection(this, request);
    connection.setAddress(
        getPhoneNumber(request),
        request
            .getExtras()
            .getInt(Simulator.PRESENTATION_CHOICE, TelecomManager.PRESENTATION_ALLOWED));
    connection.setRinging();
    simulatorConnectionsBank.add(connection);
    ThreadUtil.postOnUiThread(
        () ->
            SimulatorComponent.get(instance)
                .getSimulatorConnectionsBank()
                .updateConferenceableConnections());
    for (Listener listener : listeners) {
      listener.onNewIncomingConnection(connection);
    }
    return connection;
  }

  @Override
  public void onConference(Connection connection1, Connection connection2) {
    LogUtil.i(
        "SimulatorConnectionService.onConference",
        "connection1: "
            + SimulatorSimCallManager.getConnectionTag(connection1)
            + ", connection2: "
            + SimulatorSimCallManager.getConnectionTag(connection2));
    for (Listener listener : listeners) {
      listener.onConference((SimulatorConnection) connection1, (SimulatorConnection) connection2);
    }
  }

  /** Callback used to notify listeners when a new connection has been added. */
  public interface Listener {
    void onNewOutgoingConnection(@NonNull SimulatorConnection connection);

    void onNewIncomingConnection(@NonNull SimulatorConnection connection);

    void onConference(
        @NonNull SimulatorConnection connection1, @NonNull SimulatorConnection connection2);
  }

  private static Uri getPhoneNumber(ConnectionRequest request) {
    String phoneNumber = request.getExtras().getString(TelephonyManager.EXTRA_INCOMING_NUMBER);
    return Uri.fromParts(PhoneAccount.SCHEME_TEL, phoneNumber, null);
  }
}
