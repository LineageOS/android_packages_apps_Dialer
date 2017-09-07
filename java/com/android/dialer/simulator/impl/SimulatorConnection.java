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
import android.telecom.Connection;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.simulator.Simulator.Event;
import java.util.ArrayList;
import java.util.List;

/** Represents a single phone call on the device. */
public final class SimulatorConnection extends Connection {
  private final List<Listener> listeners = new ArrayList<>();
  private final List<Event> events = new ArrayList<>();
  private int currentState = STATE_NEW;

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
  public void onAnswer() {
    LogUtil.enterBlock("SimulatorConnection.onAnswer");
    onEvent(new Event(Event.ANSWER));
  }

  @Override
  public void onReject() {
    LogUtil.enterBlock("SimulatorConnection.onReject");
    onEvent(new Event(Event.REJECT));
  }

  @Override
  public void onHold() {
    LogUtil.enterBlock("SimulatorConnection.onHold");
    onEvent(new Event(Event.HOLD));
  }

  @Override
  public void onUnhold() {
    LogUtil.enterBlock("SimulatorConnection.onUnhold");
    onEvent(new Event(Event.UNHOLD));
  }

  @Override
  public void onDisconnect() {
    LogUtil.enterBlock("SimulatorConnection.onDisconnect");
    onEvent(new Event(Event.DISCONNECT));
  }

  @Override
  public void onStateChanged(int newState) {
    LogUtil.enterBlock("SimulatorConnection.onStateChanged");
    onEvent(new Event(Event.STATE_CHANGE, stateToString(currentState), stateToString(newState)));
    currentState = newState;
  }

  @Override
  public void onPlayDtmfTone(char c) {
    LogUtil.enterBlock("SimulatorConnection.onPlayDtmfTone");
    onEvent(new Event(Event.DTMF, Character.toString(c), null));
  }

  private void onEvent(@NonNull Event event) {
    events.add(Assert.isNotNull(event));
    for (Listener listener : listeners) {
      listener.onEvent(this, event);
    }
  }

  /** Callback for when a new event arrives. */
  public interface Listener {
    void onEvent(@NonNull SimulatorConnection connection, @NonNull Event event);
  }
}
