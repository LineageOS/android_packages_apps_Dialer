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
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.strictmode.StrictModeUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Utility to use the simulator connection service to add phone calls. To ensure that the added
 * calls are routed through the simulator we register ourselves as a SIM call manager using
 * CAPABILITY_CONNECTION_MANAGER. This ensures that all calls on the device must first go through
 * our connection service.
 *
 * <p>For video calls this will only work if the underlying telephony phone account also supports
 * video. To ensure that video always works we use a separate video account. The user must manually
 * enable this account in call settings for video calls to work.
 */
public class SimulatorSimCallManager {

  public static final int CALL_TYPE_VOICE = 1;
  public static final int CALL_TYPE_VIDEO = 2;
  public static final int CALL_TYPE_RTT = 3;

  /** Call type of a simulator call. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({CALL_TYPE_VOICE, CALL_TYPE_VIDEO, CALL_TYPE_RTT})
  public @interface CallType {}

  private static final String SIM_CALL_MANAGER_ACCOUNT_ID = "SIMULATOR_ACCOUNT_ID";
  private static final String VIDEO_PROVIDER_ACCOUNT_ID = "SIMULATOR_VIDEO_ACCOUNT_ID";
  private static final String EXTRA_IS_SIMULATOR_CONNECTION = "is_simulator_connection";
  private static final String EXTRA_CONNECTION_TAG = "connection_tag";
  private static final String EXTRA_CONNECTION_CALL_TYPE = "connection_call_type";

  public static void register(@NonNull Context context) {
    LogUtil.enterBlock("SimulatorSimCallManager.register");
    Assert.isNotNull(context);
    StrictModeUtils.bypass(
        () -> {
          TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
          telecomManager.registerPhoneAccount(buildSimCallManagerAccount(context));
          telecomManager.registerPhoneAccount(buildVideoProviderAccount(context));
        });
  }

  public static void unregister(@NonNull Context context) {
    LogUtil.enterBlock("SimulatorSimCallManager.unregister");
    Assert.isNotNull(context);
    StrictModeUtils.bypass(
        () -> {
          TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
          telecomManager.unregisterPhoneAccount(getSimCallManagerHandle(context));
          telecomManager.unregisterPhoneAccount(getVideoProviderHandle(context));
        });
  }

  @NonNull
  public static String addNewOutgoingCall(
      @NonNull Context context, @NonNull String phoneNumber, @CallType int callType) {
    return addNewOutgoingCall(context, phoneNumber, callType, new Bundle());
  }

  @NonNull
  public static String addNewOutgoingCall(
      @NonNull Context context,
      @NonNull String phoneNumber,
      @CallType int callType,
      @NonNull Bundle extras) {
    LogUtil.enterBlock("SimulatorSimCallManager.addNewOutgoingCall");
    Assert.isNotNull(context);
    Assert.isNotNull(extras);
    Assert.isNotNull(phoneNumber);
    Assert.isNotNull(extras);

    register(context);

    extras = new Bundle(extras);
    extras.putAll(createSimulatorConnectionExtras(callType));

    Bundle outgoingCallExtras = new Bundle();
    outgoingCallExtras.putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, extras);
    outgoingCallExtras.putParcelable(
        TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
        callType == CALL_TYPE_VIDEO
            ? getVideoProviderHandle(context)
            : getSimCallManagerHandle(context));
    if (callType == CALL_TYPE_RTT) {
      outgoingCallExtras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_RTT, true);
    }

    TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
    try {
      telecomManager.placeCall(
          Uri.fromParts(PhoneAccount.SCHEME_TEL, phoneNumber, null), outgoingCallExtras);
    } catch (SecurityException e) {
      throw Assert.createIllegalStateFailException("Unable to place call: " + e);
    }
    return extras.getString(EXTRA_CONNECTION_TAG);
  }

  @NonNull
  public static String addNewIncomingCall(
      @NonNull Context context, @NonNull String callerId, @CallType int callType) {
    return addNewIncomingCall(context, callerId, callType, new Bundle());
  }

  @NonNull
  public static String addNewIncomingCall(
      @NonNull Context context,
      @NonNull String callerId,
      @CallType int callType,
      @NonNull Bundle extras) {
    LogUtil.enterBlock("SimulatorSimCallManager.addNewIncomingCall");
    Assert.isNotNull(context);
    Assert.isNotNull(callerId);
    Assert.isNotNull(extras);

    register(context);

    extras = new Bundle(extras);
    extras.putString(TelephonyManager.EXTRA_INCOMING_NUMBER, callerId);
    extras.putAll(createSimulatorConnectionExtras(callType));

    TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
    telecomManager.addNewIncomingCall(
        callType == CALL_TYPE_VIDEO
            ? getVideoProviderHandle(context)
            : getSystemPhoneAccountHandle(context),
        extras);
    return extras.getString(EXTRA_CONNECTION_TAG);
  }

  @NonNull
  private static PhoneAccount buildSimCallManagerAccount(Context context) {
    return new PhoneAccount.Builder(getSimCallManagerHandle(context), "Simulator SIM call manager")
        .setCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER | PhoneAccount.CAPABILITY_RTT)
        .setShortDescription("Simulator SIM call manager")
        .setSupportedUriSchemes(Arrays.asList(PhoneAccount.SCHEME_TEL))
        .build();
  }

  @NonNull
  private static PhoneAccount buildVideoProviderAccount(Context context) {
    return new PhoneAccount.Builder(getVideoProviderHandle(context), "Simulator video provider")
        .setCapabilities(
            PhoneAccount.CAPABILITY_CALL_PROVIDER
                | PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING
                | PhoneAccount.CAPABILITY_VIDEO_CALLING)
        .setShortDescription("Simulator video provider")
        .setSupportedUriSchemes(Arrays.asList(PhoneAccount.SCHEME_TEL))
        .build();
  }

  @NonNull
  public static PhoneAccountHandle getSimCallManagerHandle(@NonNull Context context) {
    return new PhoneAccountHandle(
        new ComponentName(context, SimulatorConnectionService.class), SIM_CALL_MANAGER_ACCOUNT_ID);
  }

  @NonNull
  static PhoneAccountHandle getVideoProviderHandle(@NonNull Context context) {
    return new PhoneAccountHandle(
        new ComponentName(context, SimulatorConnectionService.class), VIDEO_PROVIDER_ACCOUNT_ID);
  }

  @NonNull
  public static PhoneAccountHandle getSystemPhoneAccountHandle(@NonNull Context context) {
    TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
    List<PhoneAccountHandle> handles;
    try {
      handles = telecomManager.getCallCapablePhoneAccounts();
    } catch (SecurityException e) {
      throw Assert.createIllegalStateFailException("Unable to get phone accounts: " + e);
    }
    for (PhoneAccountHandle handle : handles) {
      PhoneAccount account = telecomManager.getPhoneAccount(handle);
      if (account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
        return handle;
      }
    }
    throw Assert.createIllegalStateFailException("no SIM phone account available");
  }

  public static boolean isSimulatorConnectionRequest(@NonNull ConnectionRequest request) {
    return request.getExtras() != null
        && request.getExtras().getBoolean(EXTRA_IS_SIMULATOR_CONNECTION);
  }

  @NonNull
  public static String getConnectionTag(@NonNull Connection connection) {
    String connectionTag = connection.getExtras().getString(EXTRA_CONNECTION_TAG);
    return Assert.isNotNull(connectionTag);
  }

  @NonNull
  public static SimulatorConnection findConnectionByTag(@NonNull String connectionTag) {
    Assert.isNotNull(connectionTag);
    for (Connection connection : SimulatorConnectionService.getInstance().getAllConnections()) {
      if (connection.getExtras().getBoolean(connectionTag)) {
        return (SimulatorConnection) connection;
      }
    }
    throw Assert.createIllegalStateFailException();
  }

  @NonNull
  private static String createUniqueConnectionTag() {
    int callId = new Random().nextInt();
    return String.format("simulator_phone_call_%x", Math.abs(callId));
  }

  @NonNull
  static Bundle createSimulatorConnectionExtras(@CallType int callType) {
    Bundle extras = new Bundle();
    extras.putBoolean(EXTRA_IS_SIMULATOR_CONNECTION, true);
    String connectionTag = createUniqueConnectionTag();
    extras.putString(EXTRA_CONNECTION_TAG, connectionTag);
    extras.putBoolean(connectionTag, true);
    extras.putInt(EXTRA_CONNECTION_CALL_TYPE, callType);
    if (callType == CALL_TYPE_RTT) {
      extras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_RTT, true);
    }
    return extras;
  }

  private SimulatorSimCallManager() {}
}
