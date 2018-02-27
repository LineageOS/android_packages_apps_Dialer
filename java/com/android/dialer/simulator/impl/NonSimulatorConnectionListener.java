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

package com.android.dialer.simulator.impl;

import android.support.annotation.NonNull;
import android.telecom.Connection.RttModifyStatus;
import android.telecom.DisconnectCause;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.simulator.Simulator.Event;

/** Listen to call backs from Connections not made by simulator. */
final class NonSimulatorConnectionListener implements SimulatorConnection.Listener {

  @Override
  public void onEvent(@NonNull SimulatorConnection connection, @NonNull Event event) {
    switch (event.type) {
      case Event.STATE_CHANGE:
        LogUtil.i(
            "SimulatorVoiceCall.onEvent",
            String.format("state changed from %s to %s ", event.data1, event.data2));
        break;
      case Event.REJECT:
        connection.setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
        break;
      case Event.HOLD:
        connection.setOnHold();
        break;
      case Event.ANSWER:
      case Event.UNHOLD:
        connection.setActive();
        break;
      case Event.DISCONNECT:
        connection.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        break;
      case Event.SESSION_MODIFY_REQUEST:
        ThreadUtil.postDelayedOnUiThread(() -> connection.handleSessionModifyRequest(event), 2000);
        break;
      case Event.START_RTT:
        boolean accept = true;
        if (accept) {
          connection.sendRttInitiationSuccess();
        } else {
          connection.sendRttInitiationFailure(RttModifyStatus.SESSION_MODIFY_REQUEST_FAIL);
        }
        break;
      case Event.NONE:
      default:
        LogUtil.i("SimulatorVoiceCall.onEvent", "unexpected event: " + event.type);
        throw Assert.createIllegalStateFailException();
    }
  }
}
