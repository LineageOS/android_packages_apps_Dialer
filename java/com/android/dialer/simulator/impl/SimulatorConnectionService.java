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

import android.content.ComponentName;
import android.content.Context;
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
import java.util.ArrayList;
import java.util.List;

/** Simple connection provider to create an incoming call. This is useful for emulators. */
public class SimulatorConnectionService extends ConnectionService {

  private static final String PHONE_ACCOUNT_ID = "SIMULATOR_ACCOUNT_ID";
  private static final String EXTRA_IS_SIMULATOR_CONNECTION = "is_simulator_connection";
  private static final List<Listener> listeners = new ArrayList<>();

  private static void register(@NonNull Context context) {
    LogUtil.enterBlock("SimulatorConnectionService.register");
    Assert.isNotNull(context);
    context.getSystemService(TelecomManager.class).registerPhoneAccount(buildPhoneAccount(context));
  }

  private static void unregister(@NonNull Context context) {
    LogUtil.enterBlock("SimulatorConnectionService.unregister");
    Assert.isNotNull(context);
    context
        .getSystemService(TelecomManager.class)
        .unregisterPhoneAccount(buildPhoneAccount(context).getAccountHandle());
  }

  public static void addNewIncomingCall(
      @NonNull Context context, @NonNull Bundle extras, @NonNull String callerId) {
    LogUtil.enterBlock("SimulatorConnectionService.addNewIncomingCall");
    Assert.isNotNull(context);
    Assert.isNotNull(extras);
    Assert.isNotNull(callerId);

    register(context);

    Bundle bundle = new Bundle(extras);
    bundle.putString(TelephonyManager.EXTRA_INCOMING_NUMBER, callerId);
    bundle.putBoolean(EXTRA_IS_SIMULATOR_CONNECTION, true);

    // Use the system's phone account so that these look like regular SIM call.
    TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
    PhoneAccountHandle systemPhoneAccount =
        telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL);
    telecomManager.addNewIncomingCall(systemPhoneAccount, bundle);
  }

  public static void addListener(@NonNull Listener listener) {
    Assert.isNotNull(listener);
    listeners.add(listener);
  }

  public static void removeListener(@NonNull Listener listener) {
    Assert.isNotNull(listener);
    listeners.remove(listener);
  }

  @NonNull
  private static PhoneAccount buildPhoneAccount(Context context) {
    PhoneAccount.Builder builder =
        new PhoneAccount.Builder(
            getConnectionServiceHandle(context), "Simulator connection service");
    List<String> uriSchemes = new ArrayList<>();
    uriSchemes.add(PhoneAccount.SCHEME_TEL);

    return builder
        .setCapabilities(
            PhoneAccount.CAPABILITY_CALL_PROVIDER | PhoneAccount.CAPABILITY_CONNECTION_MANAGER)
        .setShortDescription("Simulator Connection Service")
        .setSupportedUriSchemes(uriSchemes)
        .build();
  }

  public static PhoneAccountHandle getConnectionServiceHandle(Context context) {
    return new PhoneAccountHandle(
        new ComponentName(context, SimulatorConnectionService.class), PHONE_ACCOUNT_ID);
  }

  private static Uri getPhoneNumber(ConnectionRequest request) {
    String phoneNumber = request.getExtras().getString(TelephonyManager.EXTRA_INCOMING_NUMBER);
    return Uri.fromParts(PhoneAccount.SCHEME_TEL, phoneNumber, null);
  }

  @Override
  public Connection onCreateOutgoingConnection(
      PhoneAccountHandle phoneAccount, ConnectionRequest request) {
    LogUtil.enterBlock("SimulatorConnectionService.onCreateOutgoingConnection");
    if (!isSimulatorConnectionRequest(request)) {
      LogUtil.i(
          "SimulatorConnectionService.onCreateOutgoingConnection",
          "outgoing call not from simulator, unregistering");
      Toast.makeText(
              this, "Unregistering Dialer simulator, making a real phone call", Toast.LENGTH_LONG)
          .show();
      unregister(this);
      return null;
    }

    SimulatorConnection connection = new SimulatorConnection();
    connection.setActive();
    connection.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
    connection.setConnectionCapabilities(
        Connection.CAPABILITY_MUTE | Connection.CAPABILITY_SUPPORT_HOLD);
    connection.putExtras(request.getExtras());

    for (Listener listener : listeners) {
      listener.onNewOutgoingConnection(connection);
    }
    return connection;
  }

  @Override
  public Connection onCreateIncomingConnection(
      PhoneAccountHandle phoneAccount, ConnectionRequest request) {
    LogUtil.enterBlock("SimulatorConnectionService.onCreateIncomingConnection");
    if (!isSimulatorConnectionRequest(request)) {
      LogUtil.i(
          "SimulatorConnectionService.onCreateIncomingConnection",
          "incoming call not from simulator, unregistering");
      Toast.makeText(
              this, "Unregistering Dialer simulator, got a real incoming call", Toast.LENGTH_LONG)
          .show();
      unregister(this);
      return null;
    }

    SimulatorConnection connection = new SimulatorConnection();
    connection.setRinging();
    connection.setAddress(getPhoneNumber(request), TelecomManager.PRESENTATION_ALLOWED);
    connection.setConnectionCapabilities(
        Connection.CAPABILITY_MUTE | Connection.CAPABILITY_SUPPORT_HOLD);
    connection.putExtras(request.getExtras());

    for (Listener listener : listeners) {
      listener.onNewIncomingConnection(connection);
    }
    return connection;
  }

  private static boolean isSimulatorConnectionRequest(@NonNull ConnectionRequest request) {
    return request.getExtras() != null
        && request.getExtras().getBoolean(EXTRA_IS_SIMULATOR_CONNECTION);
  }

  /** Callback used to notify listeners when a new connection has been added. */
  public interface Listener {
    void onNewOutgoingConnection(SimulatorConnection connection);

    void onNewIncomingConnection(SimulatorConnection connection);
  }
}
