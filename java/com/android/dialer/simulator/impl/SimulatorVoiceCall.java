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
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;

/** Utilities to simulate phone calls. */
final class SimulatorVoiceCall {
  public static void addNewIncomingCall(@NonNull Context context) {
    LogUtil.enterBlock("SimulatorVoiceCall.addNewIncomingCall");
    SimulatorConnectionService.register(context);

    Bundle bundle = new Bundle();
    // Set the caller ID to the Google London office.
    bundle.putString(TelephonyManager.EXTRA_INCOMING_NUMBER, "+44 (0) 20 7031 3000");
    try {
      context
          .getSystemService(TelecomManager.class)
          .addNewIncomingCall(
              SimulatorConnectionService.getConnectionServiceHandle(context), bundle);
    } catch (SecurityException e) {
      Assert.fail("unable to add call: " + e);
    }
  }

  private SimulatorVoiceCall() {}
}
