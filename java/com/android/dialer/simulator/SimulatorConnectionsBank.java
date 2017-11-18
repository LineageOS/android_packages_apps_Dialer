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

package com.android.dialer.simulator;

import android.content.Context;
import android.support.annotation.NonNull;
import android.telecom.Connection;
import com.android.dialer.simulator.Simulator.ConferenceType;
import java.util.List;

/**
 * Used to create a shared connections bank which contains methods to manipulate connections. This
 * is used mainly for conference calling.
 */
public interface SimulatorConnectionsBank {

  /** Add a connection into bank. */
  void add(Connection connection);

  /** Remove a connection from bank. */
  void remove(Connection connection);

  /** Merge all existing connections created by simulator into a conference. */
  void mergeAllConnections(@ConferenceType int conferenceType, Context context);

  /** Set all connections created by simulator to disconnected. */
  void disconnectAllConnections();

  /**
   * Update conferenceable connections for all connections in bank (usually after adding a new
   * connection). Before calling this method, make sure all connections are returned by
   * ConnectionService.
   */
  void updateConferenceableConnections();

  /** Determine whether a connection is created by simulator. */
  boolean isSimulatorConnection(@NonNull Connection connection);

  /** Get all connections tags from bank. */
  List<String> getConnectionTags();
}
