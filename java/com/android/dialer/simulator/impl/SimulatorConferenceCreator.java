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
import android.support.annotation.Nullable;
import android.telecom.Conferenceable;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.simulator.Simulator;
import com.android.dialer.simulator.Simulator.Event;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Creates a conference with a given number of participants. */
final class SimulatorConferenceCreator
    implements SimulatorConnectionService.Listener,
        SimulatorConnection.Listener,
        SimulatorConference.Listener {
  private static final String EXTRA_CALL_COUNT = "call_count";

  @NonNull private final Context context;
  @NonNull private final List<String> connectionTags = new ArrayList<>();
  @Simulator.ConferenceType private final int conferenceType;

  public SimulatorConferenceCreator(
      @NonNull Context context, @Simulator.ConferenceType int conferenceType) {
    this.context = Assert.isNotNull(context);
    this.conferenceType = conferenceType;
  }

  void start(int callCount) {
    SimulatorConnectionService.addListener(this);
    addNextCall(callCount);
  }

  private void addNextCall(int callCount) {
    LogUtil.i("SimulatorConferenceCreator.addNextIncomingCall", "callCount: " + callCount);
    if (callCount <= 0) {
      LogUtil.i("SimulatorConferenceCreator.addNextCall", "done adding calls");
      return;
    }

    String callerId = String.format(Locale.US, "+1-650-234%04d", callCount);
    Bundle extras = new Bundle();
    extras.putInt(EXTRA_CALL_COUNT, callCount - 1);
    connectionTags.add(
        SimulatorSimCallManager.addNewIncomingCall(context, callerId, false /* isVideo */, extras));
  }

  @Override
  public void onNewIncomingConnection(@NonNull SimulatorConnection connection) {
    if (!isMyConnection(connection)) {
      LogUtil.i("SimulatorConferenceCreator.onNewOutgoingConnection", "unknown connection");
      return;
    }

    LogUtil.i("SimulatorConferenceCreator.onNewOutgoingConnection", "connection created");
    connection.addListener(this);

    // Telecom will force the connection to switch to DIALING when we return it. Wait until after
    // we're returned it before changing call state.
    ThreadUtil.postOnUiThread(() -> connection.setActive());

    // Once the connection is active, go ahead and conference it and add the next call.
    ThreadUtil.postDelayedOnUiThread(
        () -> {
          SimulatorConference conference = findCurrentConference();
          if (conference == null) {
            Assert.checkArgument(conferenceType == Simulator.CONFERENCE_TYPE_GSM);
            conference =
                SimulatorConference.newGsmConference(
                    SimulatorSimCallManager.getSystemPhoneAccountHandle(context));
            conference.addListener(this);
            SimulatorConnectionService.getInstance().addConference(conference);
          }
          updateConferenceableConnections();
          conference.addConnection(connection);
          addNextCall(getCallCount(connection));
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
    if (!isMyConnection(connection1) || !isMyConnection(connection2)) {
      LogUtil.i("SimulatorConferenceCreator.onConference", "unknown connections, ignoring");
      return;
    }

    if (connection1.getConference() != null) {
      connection1.getConference().addConnection(connection2);
    } else if (connection2.getConference() != null) {
      connection2.getConference().addConnection(connection1);
    } else {
      Assert.checkArgument(conferenceType == Simulator.CONFERENCE_TYPE_GSM);
      SimulatorConference conference =
          SimulatorConference.newGsmConference(
              SimulatorSimCallManager.getSystemPhoneAccountHandle(context));
      conference.addConnection(connection1);
      conference.addConnection(connection2);
      conference.addListener(this);
      SimulatorConnectionService.getInstance().addConference(conference);
    }
  }

  private boolean isMyConnection(@NonNull Connection connection) {
    for (String connectionTag : connectionTags) {
      if (connection.getExtras().getBoolean(connectionTag)) {
        return true;
      }
    }
    return false;
  }

  private void updateConferenceableConnections() {
    LogUtil.enterBlock("SimulatorConferenceCreator.updateConferenceableConnections");
    for (String connectionTag : connectionTags) {
      SimulatorConnection connection = SimulatorSimCallManager.findConnectionByTag(connectionTag);
      List<Conferenceable> conferenceables = getMyConferenceables();
      conferenceables.remove(connection);
      conferenceables.remove(connection.getConference());
      connection.setConferenceables(conferenceables);
    }
  }

  private List<Conferenceable> getMyConferenceables() {
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

  @Nullable
  private SimulatorConference findCurrentConference() {
    for (String connectionTag : connectionTags) {
      SimulatorConnection connection = SimulatorSimCallManager.findConnectionByTag(connectionTag);
      if (connection.getConference() != null) {
        return (SimulatorConference) connection.getConference();
      }
    }
    return null;
  }

  private static int getCallCount(@NonNull Connection connection) {
    return connection.getExtras().getInt(EXTRA_CALL_COUNT);
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
        break;
      default:
        LogUtil.i(
            "SimulatorConferenceCreator.onEvent", "unexpected conference event: " + event.type);
        break;
    }
  }
}
