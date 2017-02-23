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
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import com.android.dialer.common.LogUtil;
import java.util.ArrayList;
import java.util.List;

/** Simple connection provider to create an incoming call. This is useful for emulators. */
public final class SimulatorConnectionService extends ConnectionService {

  private static final String PHONE_ACCOUNT_ID = "SIMULATOR_ACCOUNT_ID";

  public static void register(Context context) {
    LogUtil.enterBlock("SimulatorConnectionService.register");
    context.getSystemService(TelecomManager.class).registerPhoneAccount(buildPhoneAccount(context));
  }

  private static PhoneAccount buildPhoneAccount(Context context) {
    PhoneAccount.Builder builder =
        new PhoneAccount.Builder(
            getConnectionServiceHandle(context), "Simulator connection service");
    List<String> uriSchemes = new ArrayList<>();
    uriSchemes.add(PhoneAccount.SCHEME_TEL);

    return builder
        .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
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
    LogUtil.i(
        "SimulatorConnectionService.onCreateOutgoingConnection",
        "outgoing calls not supported yet");
    return null;
  }

  @Override
  public Connection onCreateIncomingConnection(
      PhoneAccountHandle phoneAccount, ConnectionRequest request) {
    LogUtil.enterBlock("SimulatorConnectionService.onCreateIncomingConnection");
    SimulatorConnection connection = new SimulatorConnection();
    connection.setRinging();
    connection.setAddress(getPhoneNumber(request), TelecomManager.PRESENTATION_ALLOWED);
    connection.setConnectionCapabilities(
        Connection.CAPABILITY_MUTE | Connection.CAPABILITY_SUPPORT_HOLD);
    return connection;
  }
}
