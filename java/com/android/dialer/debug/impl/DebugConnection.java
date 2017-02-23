/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.debug.impl;

import android.telecom.Connection;
import android.telecom.DisconnectCause;
import com.android.dialer.common.LogUtil;

class DebugConnection extends Connection {

  @Override
  public void onAnswer() {
    LogUtil.i("DebugConnection.onAnswer", null);
    setActive();
  }

  @Override
  public void onReject() {
    LogUtil.i("DebugConnection.onReject", null);
    setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
  }

  @Override
  public void onHold() {
    LogUtil.i("DebugConnection.onHold", null);
    setOnHold();
  }

  @Override
  public void onUnhold() {
    LogUtil.i("DebugConnection.onUnhold", null);
    setActive();
  }

  @Override
  public void onDisconnect() {
    LogUtil.i("DebugConnection.onDisconnect", null);
    setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
    destroy();
  }
}
