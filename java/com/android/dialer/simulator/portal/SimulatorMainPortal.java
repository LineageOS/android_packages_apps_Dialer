/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.simulator.portal;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.telecom.VideoProfile;
import android.view.ActionProvider;
import com.android.dialer.enrichedcall.simulator.EnrichedCallSimulatorActivity;
import com.android.dialer.simulator.Simulator;
import com.android.dialer.simulator.SimulatorComponent;
import com.android.dialer.simulator.impl.SimulatorConferenceCreator;
import com.android.dialer.simulator.impl.SimulatorMissedCallCreator;
import com.android.dialer.simulator.impl.SimulatorRttCall;
import com.android.dialer.simulator.impl.SimulatorSimCallManager;
import com.android.dialer.simulator.impl.SimulatorUtils;
import com.android.dialer.simulator.impl.SimulatorVideoCall;
import com.android.dialer.simulator.impl.SimulatorVoiceCall;
import com.google.common.collect.ImmutableMap;

/** Implements the top level simulator menu. */
public final class SimulatorMainPortal {

  private final Context context;
  private final AppCompatActivity activity;
  private SimulatorPortalEntryGroup simulatorMainPortal;

  public SimulatorMainPortal(AppCompatActivity activity) {
    this.activity = activity;
    this.context = activity.getApplicationContext();
    buildMainPortal();
  }

  private void buildMainPortal() {
    this.simulatorMainPortal =
        SimulatorPortalEntryGroup.builder()
            .setMethods(
                ImmutableMap.<String, Runnable>builder()
                    .put("Populate database", () -> SimulatorUtils.populateDatabase(context))
                    .put("Populate voicemail", () -> SimulatorUtils.populateVoicemail(context))
                    .put(
                        "Fast Populate database",
                        () -> SimulatorUtils.fastPopulateDatabase(context))
                    .put(
                        "Fast populate voicemail database",
                        () -> SimulatorUtils.populateVoicemailFast(context))
                    .put("Clean database", () -> SimulatorUtils.cleanDatabase(context))
                    .put("clear preferred SIM", () -> SimulatorUtils.clearPreferredSim(context))
                    .put("Sync voicemail", () -> SimulatorUtils.syncVoicemail(context))
                    .put("Share persistent log", () -> SimulatorUtils.sharePersistentLog(context))
                    .put(
                        "Enriched call simulator",
                        () ->
                            context.startActivity(EnrichedCallSimulatorActivity.newIntent(context)))
                    .put(
                        "Enable simulator mode",
                        () -> {
                          SimulatorComponent.get(context).getSimulator().enableSimulatorMode();
                          SimulatorSimCallManager.register(context);
                        })
                    .put(
                        "Disable simulator mode",
                        () -> {
                          SimulatorComponent.get(context).getSimulator().disableSimulatorMode();
                          SimulatorSimCallManager.unregister(context);
                        })
                    .build())
            .setSubPortals(
                ImmutableMap.of(
                    "VoiceCall",
                    buildSimulatorVoiceCallPortal(),
                    "VideoCall",
                    buildSimulatorVideoCallPortal(),
                    "RttCall",
                    buildSimulatorRttCallPortal(),
                    "Notifications",
                    buildSimulatorNotificationsPortal()))
            .build();
  }

  public SimulatorPortalEntryGroup buildSimulatorVoiceCallPortal() {
    return SimulatorPortalEntryGroup.builder()
        .setMethods(
            ImmutableMap.<String, Runnable>builder()
                .put("Incoming call", () -> new SimulatorVoiceCall(context).addNewIncomingCall())
                .put("Outgoing call", () -> new SimulatorVoiceCall(context).addNewOutgoingCall())
                .put(
                    "Customized incoming call",
                    () -> new SimulatorVoiceCall(context).addNewIncomingCall(activity))
                .put(
                    "Customized outgoing call",
                    () -> new SimulatorVoiceCall(context).addNewOutgoingCall(activity))
                .put(
                    "Incoming enriched call",
                    () -> new SimulatorVoiceCall(context).incomingEnrichedCall())
                .put(
                    "Outgoing enriched call",
                    () -> new SimulatorVoiceCall(context).outgoingEnrichedCall())
                .put(
                    "Spam incoming call",
                    () -> new SimulatorVoiceCall(context).addSpamIncomingCall())
                .put(
                    "Emergency call back",
                    () -> new SimulatorVoiceCall(context).addNewEmergencyCallBack())
                .put(
                    "GSM conference",
                    () ->
                        new SimulatorConferenceCreator(context, Simulator.CONFERENCE_TYPE_GSM)
                            .start(5))
                .put(
                    "VoLTE conference",
                    () ->
                        new SimulatorConferenceCreator(context, Simulator.CONFERENCE_TYPE_VOLTE)
                            .start(5))
                .build())
        .build();
  }

  private SimulatorPortalEntryGroup buildSimulatorVideoCallPortal() {
    return SimulatorPortalEntryGroup.builder()
        .setMethods(
            ImmutableMap.<String, Runnable>builder()
                .put(
                    "Incoming one way",
                    () ->
                        new SimulatorVideoCall(context, VideoProfile.STATE_RX_ENABLED)
                            .addNewIncomingCall())
                .put(
                    "Incoming two way",
                    () ->
                        new SimulatorVideoCall(context, VideoProfile.STATE_BIDIRECTIONAL)
                            .addNewIncomingCall())
                .put(
                    "Outgoing one way",
                    () ->
                        new SimulatorVideoCall(context, VideoProfile.STATE_TX_ENABLED)
                            .addNewOutgoingCall())
                .put(
                    "Outgoing two way",
                    () ->
                        new SimulatorVideoCall(context, VideoProfile.STATE_BIDIRECTIONAL)
                            .addNewOutgoingCall())
                .build())
        .build();
  }

  private SimulatorPortalEntryGroup buildSimulatorRttCallPortal() {
    return SimulatorPortalEntryGroup.builder()
        .setMethods(
            ImmutableMap.<String, Runnable>builder()
                .put("Incoming call", () -> new SimulatorRttCall(context).addNewIncomingCall(false))
                .put("Outgoing call", () -> new SimulatorRttCall(context).addNewOutgoingCall())
                .put("Emergency call", () -> new SimulatorRttCall(context).addNewEmergencyCall())
                .build())
        .build();
  }

  private SimulatorPortalEntryGroup buildSimulatorNotificationsPortal() {
    return SimulatorPortalEntryGroup.builder()
        .setMethods(
            ImmutableMap.<String, Runnable>builder()
                .put(
                    "Missed calls",
                    () ->
                        new SimulatorMissedCallCreator(context)
                            .start(SimulatorUtils.NOTIFICATION_COUNT))
                .put(
                    "Missed calls (few)",
                    () ->
                        new SimulatorMissedCallCreator(context)
                            .start(SimulatorUtils.NOTIFICATION_COUNT_FEW))
                .put(
                    "Voicemails",
                    () ->
                        SimulatorUtils.addVoicemailNotifications(
                            context, SimulatorUtils.NOTIFICATION_COUNT))
                .build())
        .build();
  }

  public ActionProvider getActionProvider() {
    return new SimulatorMenu(context, simulatorMainPortal);
  }
}
