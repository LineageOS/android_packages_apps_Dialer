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
import android.support.annotation.NonNull;
import android.telecom.Conferenceable;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import com.android.dialer.common.LogUtil;
import com.android.dialer.simulator.Simulator;
import com.android.dialer.simulator.Simulator.ConferenceType;
import com.android.dialer.simulator.Simulator.Event;
import com.android.dialer.simulator.SimulatorConnectionsBank;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.inject.Inject;

/** Wraps a list of connection tags and common methods around the connection tags list. */
public class SimulatorConnectionsBankImpl
    implements SimulatorConnectionsBank, SimulatorConference.Listener {
  private final List<String> connectionTags = new ArrayList<>();

  @Inject
  public SimulatorConnectionsBankImpl() {}

  @Override
  public List<String> getConnectionTags() {
    return connectionTags;
  }

  @Override
  public void add(Connection connection) {
    connectionTags.add(SimulatorSimCallManager.getConnectionTag(connection));
  }

  @Override
  public void remove(Connection connection) {
    connectionTags.remove(SimulatorSimCallManager.getConnectionTag(connection));
  }

  @Override
  public void mergeAllConnections(@ConferenceType int conferenceType, Context context) {
    SimulatorConference simulatorConference = null;
    if (conferenceType == Simulator.CONFERENCE_TYPE_GSM) {
      simulatorConference =
          SimulatorConference.newGsmConference(
              SimulatorSimCallManager.getSystemPhoneAccountHandle(context));
    } else if (conferenceType == Simulator.CONFERENCE_TYPE_VOLTE) {
      simulatorConference =
          SimulatorConference.newVoLteConference(
              SimulatorSimCallManager.getSystemPhoneAccountHandle(context));
    }
    Collection<Connection> connections =
        SimulatorConnectionService.getInstance().getAllConnections();
    for (Connection connection : connections) {
      simulatorConference.addConnection(connection);
    }
    simulatorConference.addListener(this);
    SimulatorConnectionService.getInstance().addConference(simulatorConference);
  }

  @Override
  public void disconnectAllConnections() {
    Collection<Connection> connections =
        SimulatorConnectionService.getInstance().getAllConnections();
    for (Connection connection : connections) {
      connection.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
    }
  }

  @Override
  public void updateConferenceableConnections() {
    LogUtil.enterBlock("SimulatorConferenceCreator.updateConferenceableConnections");
    for (String connectionTag : connectionTags) {
      SimulatorConnection connection = SimulatorSimCallManager.findConnectionByTag(connectionTag);
      List<Conferenceable> conferenceables = getSimulatorConferenceables();
      conferenceables.remove(connection);
      conferenceables.remove(connection.getConference());
      connection.setConferenceables(conferenceables);
    }
  }

  private List<Conferenceable> getSimulatorConferenceables() {
    List<Conferenceable> conferenceables = new ArrayList<>();
    for (String connectionTag : connectionTags) {
      SimulatorConnection connection = SimulatorSimCallManager.findConnectionByTag(connectionTag);
      conferenceables.add(connection);
      if (connection.getConference() != null
          && !conferenceables.contains(connection.getConference())) {
        conferenceables.add(connection.getConference());
      }
    }
    return conferenceables;
  }

  @Override
  public boolean isSimulatorConnection(@NonNull Connection connection) {
    for (String connectionTag : connectionTags) {
      if (connection.getExtras().getBoolean(connectionTag)) {
        return true;
      }
    }
    return false;
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
