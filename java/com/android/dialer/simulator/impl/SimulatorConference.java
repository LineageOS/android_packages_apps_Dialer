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

import android.support.annotation.NonNull;
import android.telecom.CallAudioState;
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.PhoneAccountHandle;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.simulator.Simulator;
import com.android.dialer.simulator.Simulator.Event;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a conference call. When a user merges two phone calls we create an instance of this
 * conference object and add it to the connection service. All operations such as hold and DTMF are
 * then performed on this object.
 */
public final class SimulatorConference extends Conference implements SimulatorConnection.Listener {
  static final int PROPERTY_GENERIC_CONFERENCE = 1 << 1;

  private final List<Listener> listeners = new ArrayList<>();
  private final List<Event> events = new ArrayList<>();
  private final int conferenceType;

  private SimulatorConference(
      PhoneAccountHandle handle, @Simulator.ConferenceType int conferenceType) {
    super(handle);
    this.conferenceType = conferenceType;
    setActive();
  }

  static SimulatorConference newGsmConference(PhoneAccountHandle handle) {
    SimulatorConference simulatorConference =
        new SimulatorConference(handle, Simulator.CONFERENCE_TYPE_GSM);
    simulatorConference.setConnectionCapabilities(
        Connection.CAPABILITY_MUTE
            | Connection.CAPABILITY_SUPPORT_HOLD
            | Connection.CAPABILITY_HOLD
            | Connection.CAPABILITY_MANAGE_CONFERENCE);
    return simulatorConference;
  }

  static SimulatorConference newVoLteConference(PhoneAccountHandle handle) {
    SimulatorConference simulatorConference =
        new SimulatorConference(handle, Simulator.CONFERENCE_TYPE_VOLTE);
    simulatorConference.setConnectionCapabilities(
        Connection.CAPABILITY_MUTE
            | Connection.CAPABILITY_SUPPORT_HOLD
            | Connection.CAPABILITY_HOLD
            | Connection.CAPABILITY_MANAGE_CONFERENCE);
    return simulatorConference;
  }

  public void addListener(@NonNull Listener listener) {
    listeners.add(Assert.isNotNull(listener));
  }

  public void removeListener(@NonNull Listener listener) {
    listeners.remove(Assert.isNotNull(listener));
  }

  @NonNull
  public List<Event> getEvents() {
    return events;
  }

  @Override
  public void onCallAudioStateChanged(CallAudioState state) {
    LogUtil.enterBlock("SimulatorConference.onCallAudioStateChanged");
    onEvent(new Event(Event.CALL_AUDIO_STATE_CHANGED));
  }

  @Override
  public void onConnectionAdded(Connection connection) {
    LogUtil.enterBlock("SimulatorConference.onConnectionAdded");
    onEvent(
        new Event(
            Event.CONNECTION_ADDED, SimulatorSimCallManager.getConnectionTag(connection), null));
    ((SimulatorConnection) connection).addListener(this);
  }

  @Override
  public void onDisconnect() {
    LogUtil.enterBlock("SimulatorConference.onDisconnect");
    onEvent(new Event(Event.DISCONNECT));
  }

  @Override
  public void onHold() {
    LogUtil.enterBlock("SimulatorConference.onHold");
    onEvent(new Event(Event.HOLD));
  }

  @Override
  public void onMerge(Connection connection) {
    LogUtil.i("SimulatorConference.onMerge", "connection: " + connection);
    onEvent(new Event(Event.MERGE, SimulatorSimCallManager.getConnectionTag(connection), null));
  }

  @Override
  public void onMerge() {
    LogUtil.enterBlock("SimulatorConference.onMerge");
    onEvent(new Event(Event.MERGE));
  }

  @Override
  public void onPlayDtmfTone(char c) {
    LogUtil.enterBlock("SimulatorConference.onPlayDtmfTone");
    onEvent(new Event(Event.DTMF, Character.toString(c), null));
  }

  @Override
  public void onSeparate(Connection connection) {
    LogUtil.i("SimulatorConference.onSeparate", "connection: " + connection);
    onEvent(new Event(Event.SEPARATE, SimulatorSimCallManager.getConnectionTag(connection), null));
    // if there is only 1 connection in a gsm conference, destroy the conference.
    if (conferenceType == Simulator.CONFERENCE_TYPE_GSM && getConnections().size() == 1) {
      removeConnection(getConnections().get(0));
      destroy();
    }
  }

  @Override
  public void onSwap() {
    LogUtil.enterBlock("SimulatorConference.onSwap");
    onEvent(new Event(Event.SWAP));
  }

  @Override
  public void onUnhold() {
    LogUtil.enterBlock("SimulatorConference.onUnhold");
    onEvent(new Event(Event.UNHOLD));
  }

  @Override
  public void onEvent(@NonNull SimulatorConnection connection, @NonNull Event event) {
    if (conferenceType == Simulator.CONFERENCE_TYPE_GSM) {
      onGsmEvent(connection, event);
    }
  }

  private void onGsmEvent(@NonNull SimulatorConnection connection, @NonNull Event event) {
    if (event.type == Event.STATE_CHANGE
        && Connection.stateToString(Connection.STATE_DISCONNECTED).equals(event.data2)) {
      removeConnection(connection);
      connection.removeListener(this);
      if (getConnections().size() <= 1) {
        // When only one connection exists, it's not conference call anymore
        setDisconnected(connection.getDisconnectCause());
        destroy();
      }
    }
  }

  void onEvent(@NonNull Event event) {
    events.add(Assert.isNotNull(event));
    for (Listener listener : new ArrayList<>(listeners)) {
      listener.onEvent(this, event);
    }
  }

  /** Callback for when a new event arrives. */
  public interface Listener {
    void onEvent(@NonNull SimulatorConference conference, @NonNull Event event);
  }
}
