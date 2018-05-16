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
import com.android.dialer.simulator.Simulator;
import com.android.dialer.simulator.Simulator.Event;
import com.android.dialer.simulator.SimulatorComponent;
import com.android.dialer.simulator.SimulatorConnectionsBank;
import java.util.ArrayList;
import java.util.Locale;

/** Creates a conference with a given number of participants. */
final class SimulatorConferenceCreator
    implements SimulatorConnectionService.Listener,
        SimulatorConnection.Listener,
        SimulatorConference.Listener {
  private static final String EXTRA_CALL_COUNT = "call_count";
  private static final String RECONNECT = "reconnect";
  @NonNull private final Context context;

  private final SimulatorConnectionsBank simulatorConnectionsBank;

  private boolean onNewIncomingConnectionEnabled = false;

  @Simulator.ConferenceType private final int conferenceType;

  SimulatorConferenceCreator(
      @NonNull Context context, @Simulator.ConferenceType int conferenceType) {
    this.context = Assert.isNotNull(context);
    this.conferenceType = conferenceType;
    simulatorConnectionsBank = SimulatorComponent.get(context).getSimulatorConnectionsBank();
  }

  /**
   * Starts to create certain number of calls to form a conference call.
   *
   * @param callCount the number of calls in conference to create.
   */
  void start(int callCount) {
    onNewIncomingConnectionEnabled = true;
    SimulatorConnectionService.addListener(this);
    if (conferenceType == Simulator.CONFERENCE_TYPE_VOLTE) {
      addNextCall(callCount, true);
    } else if (conferenceType == Simulator.CONFERENCE_TYPE_GSM) {
      addNextCall(callCount, false);
    }
  }
  /**
   * Add a call in a process of making a conference.
   *
   * @param callCount the remaining number of calls to make
   * @param reconnect whether all connections should reconnect once (connections are reconnected
   *     once in making VoLTE conference)
   */
  private void addNextCall(int callCount, boolean reconnect) {
    LogUtil.i("SimulatorConferenceCreator.addNextIncomingCall", "callCount: " + callCount);
    if (callCount <= 0) {
      LogUtil.i("SimulatorConferenceCreator.addNextCall", "done adding calls");
      if (reconnect) {
        simulatorConnectionsBank.disconnectAllConnections();
        addNextCall(simulatorConnectionsBank.getConnectionTags().size(), false);
      } else {
        simulatorConnectionsBank.mergeAllConnections(conferenceType, context);
        SimulatorConnectionService.removeListener(this);
      }
      return;
    }
    String callerId = String.format(Locale.US, "+1-650-234%04d", callCount);
    Bundle extras = new Bundle();
    extras.putInt(EXTRA_CALL_COUNT, callCount - 1);
    extras.putBoolean(RECONNECT, reconnect);
    addConferenceCall(callerId, extras);
  }

  private void addConferenceCall(String number, Bundle extras) {
    switch (conferenceType) {
      case Simulator.CONFERENCE_TYPE_VOLTE:
        extras.putBoolean(Simulator.IS_VOLTE, true);
        break;
      default:
        break;
    }
    SimulatorSimCallManager.addNewIncomingCall(
        context, number, SimulatorSimCallManager.CALL_TYPE_VOICE, extras);
  }

  @Override
  public void onNewIncomingConnection(@NonNull SimulatorConnection connection) {
    if (!onNewIncomingConnectionEnabled) {
      return;
    }
    if (!simulatorConnectionsBank.isSimulatorConnection(connection)) {
      LogUtil.i("SimulatorConferenceCreator.onNewOutgoingConnection", "unknown connection");
      return;
    }
    LogUtil.i("SimulatorConferenceCreator.onNewOutgoingConnection", "connection created");
    connection.addListener(this);
    // Once the connection is active, go ahead and conference it and add the next call.
    ThreadUtil.postDelayedOnUiThread(
        () -> {
          connection.setActive();
          addNextCall(getCallCount(connection), shouldReconnect(connection));
        },
        1000);
  }

  @Override
  public void onNewOutgoingConnection(@NonNull SimulatorConnection connection) {}

  /**
   * This is called when the user clicks the merge button. We create the initial conference
   * automatically but with this method we can let the user split and merge calls as desired.
   */
  @Override
  public void onConference(
      @NonNull SimulatorConnection connection1, @NonNull SimulatorConnection connection2) {
    LogUtil.enterBlock("SimulatorConferenceCreator.onConference");
    if (!simulatorConnectionsBank.isSimulatorConnection(connection1)
        || !simulatorConnectionsBank.isSimulatorConnection(connection2)) {
      LogUtil.i("SimulatorConferenceCreator.onConference", "unknown connections, ignoring");
      return;
    }

    if (connection1.getConference() != null) {
      connection1.getConference().addConnection(connection2);
    } else if (connection2.getConference() != null) {
      connection2.getConference().addConnection(connection1);
    } else {
      SimulatorConference conference =
          SimulatorConference.newGsmConference(
              SimulatorSimCallManager.getSystemPhoneAccountHandle(context));
      conference.addConnection(connection1);
      conference.addConnection(connection2);
      conference.addListener(this);
      SimulatorConnectionService.getInstance().addConference(conference);
    }
  }

  private static int getCallCount(@NonNull Connection connection) {
    return connection.getExtras().getInt(EXTRA_CALL_COUNT);
  }

  private static boolean shouldReconnect(@NonNull Connection connection) {
    return connection.getExtras().getBoolean(RECONNECT);
  }

  @Override
  public void onEvent(@NonNull SimulatorConnection connection, @NonNull Event event) {
    switch (event.type) {
      case Event.NONE:
        throw Assert.createIllegalStateFailException();
      case Event.HOLD:
        connection.setOnHold();
        break;
      case Event.UNHOLD:
        connection.setActive();
        break;
      case Event.DISCONNECT:
        connection.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        break;
      default:
        LogUtil.i(
            "SimulatorConferenceCreator.onEvent", "unexpected conference event: " + event.type);
        break;
    }
  }

  @Override
  public void onEvent(@NonNull SimulatorConference conference, @NonNull Event event) {
    switch (event.type) {
      case Event.MERGE:
        int capabilities = conference.getConnectionCapabilities();
        capabilities |= Connection.CAPABILITY_SWAP_CONFERENCE;
        conference.setConnectionCapabilities(capabilities);
        break;
      case Event.SEPARATE:
        SimulatorConnection connectionToRemove =
            SimulatorSimCallManager.findConnectionByTag(event.data1);
        conference.removeConnection(connectionToRemove);
        break;
      case Event.DISCONNECT:
        for (Connection connection : new ArrayList<>(conference.getConnections())) {
          connection.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        }
        conference.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        break;
      default:
        LogUtil.i(
            "SimulatorConferenceCreator.onEvent", "unexpected conference event: " + event.type);
        break;
    }
  }
}
