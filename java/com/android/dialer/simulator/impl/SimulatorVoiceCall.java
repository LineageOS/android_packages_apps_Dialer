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
import android.support.v7.app.AppCompatActivity;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.view.ActionProvider;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.simulator.Simulator;
import com.android.dialer.simulator.Simulator.Event;

/** Entry point in the simulator to create voice calls. */
final class SimulatorVoiceCall
    implements SimulatorConnectionService.Listener, SimulatorConnection.Listener {
  @NonNull private final Context context;
  @Nullable private String connectionTag;

  static ActionProvider getActionProvider(@NonNull AppCompatActivity activity) {
    return new SimulatorSubMenu(activity.getApplicationContext())
        .addItem(
            "Incoming call",
            () -> new SimulatorVoiceCall(activity.getApplicationContext()).addNewIncomingCall())
        .addItem(
            "Outgoing call",
            () -> new SimulatorVoiceCall(activity.getApplicationContext()).addNewOutgoingCall())
        .addItem(
            "Customized incoming call",
            () ->
                new SimulatorVoiceCall(activity.getApplicationContext())
                    .addNewIncomingCall(activity))
        .addItem(
            "Customized outgoing call",
            () ->
                new SimulatorVoiceCall(activity.getApplicationContext())
                    .addNewOutgoingCall(activity))
        .addItem(
            "Spam incoming call",
            () -> new SimulatorVoiceCall(activity.getApplicationContext()).addSpamIncomingCall())
        .addItem(
            "Emergency call back",
            () ->
                new SimulatorVoiceCall(activity.getApplicationContext()).addNewEmergencyCallBack())
        .addItem(
            "GSM conference",
            () ->
                new SimulatorConferenceCreator(
                        activity.getApplicationContext(), Simulator.CONFERENCE_TYPE_GSM)
                    .start(5))
        .addItem(
            "VoLTE conference",
            () ->
                new SimulatorConferenceCreator(
                        activity.getApplicationContext(), Simulator.CONFERENCE_TYPE_VOLTE)
                    .start(5));
  }

  private SimulatorVoiceCall(@NonNull Context context) {
    this.context = Assert.isNotNull(context);
    SimulatorConnectionService.addListener(this);
    SimulatorConnectionService.addListener(
        new SimulatorConferenceCreator(context, Simulator.CONFERENCE_TYPE_GSM));
  }

  private void addNewIncomingCall() {
    String callerId = "+44 (0) 20 7031 3000" /* Google London office */;
    connectionTag =
        SimulatorSimCallManager.addNewIncomingCall(context, callerId, false /* isVideo */);
  }

  private void addNewOutgoingCall() {
    String callerId = "+55-31-2128-6800"; // Brazil office.
    connectionTag =
        SimulatorSimCallManager.addNewOutgoingCall(context, callerId, false /* isVideo */);
  }

  private void addNewIncomingCall(AppCompatActivity activity) {
    SimulatorDialogFragment.newInstance(
            (callerId, callerIdPresentation) -> {
              Bundle extras = new Bundle();
              extras.putInt(Simulator.PRESENTATION_CHOICE, callerIdPresentation);
              connectionTag =
                  SimulatorSimCallManager.addNewIncomingCall(
                      context, callerId, false /* isVideo */, extras);
            })
        .show(activity.getSupportFragmentManager(), "SimulatorDialog");
  }

  private void addNewOutgoingCall(AppCompatActivity activity) {
    SimulatorDialogFragment.newInstance(
            (callerId, callerIdPresentation) -> {
              Bundle extras = new Bundle();
              extras.putInt(Simulator.PRESENTATION_CHOICE, callerIdPresentation);
              connectionTag =
                  SimulatorSimCallManager.addNewOutgoingCall(
                      context, callerId, false /* isVideo */, extras);
            })
        .show(activity.getSupportFragmentManager(), "SimulatorDialog");
  }

  private void addSpamIncomingCall() {
    String callerId = "+1-661-778-3020"; /* Blacklisted custom spam number */
    connectionTag =
        SimulatorSimCallManager.addNewIncomingCall(context, callerId, false /* isVideo */);
  }

  private void addNewEmergencyCallBack() {
    String callerId = "911";
    connectionTag = SimulatorSimCallManager.addNewIncomingCall(context, callerId, false);
  }

  @Override
  public void onNewOutgoingConnection(@NonNull SimulatorConnection connection) {
    if (isMyConnection(connection)) {
      LogUtil.i("SimulatorVoiceCall.onNewOutgoingConnection", "connection created");
      handleNewConnection(connection);

      // Telecom will force the connection to switch to Dialing when we return it. Wait until after
      // we're returned it before changing call state.
      ThreadUtil.postOnUiThread(connection::setActive);
    }
  }

  @Override
  public void onNewIncomingConnection(@NonNull SimulatorConnection connection) {
    if (isMyConnection(connection)) {
      LogUtil.i("SimulatorVoiceCall.onNewIncomingConnection", "connection created");
      handleNewConnection(connection);
    }
  }

  @Override
  public void onConference(
      @NonNull SimulatorConnection connection1, @NonNull SimulatorConnection connection2) {}

  private void handleNewConnection(@NonNull SimulatorConnection connection) {
    connection.addListener(this);
    connection.setConnectionCapabilities(
        connection.getConnectionCapabilities()
            | Connection.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL
            | Connection.CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL);
  }

  private boolean isMyConnection(@NonNull Connection connection) {
    return connection.getExtras().getBoolean(connectionTag);
  }

  @Override
  public void onEvent(@NonNull SimulatorConnection connection, @NonNull Event event) {
    switch (event.type) {
      case Event.NONE:
        throw Assert.createIllegalStateFailException();
      case Event.ANSWER:
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
        LogUtil.i("SimulatorVoiceCall.onEvent", "unexpected event: " + event.type);
        break;
    }
  }

  private interface DialogCallback {
    void callback(String callerId, int callerIdPresentation);
  }
}
