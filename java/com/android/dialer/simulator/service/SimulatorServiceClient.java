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

package com.android.dialer.simulator.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

/** Contains the basic logic that a simulator service client needs to get access to the service. */
public abstract class SimulatorServiceClient {

  /** Initiates service connection. */
  public void connectionService(Context context) {
    Intent intent = new Intent(context, SimulatorService.class);
    SimulatorServiceConnection mConnection = new SimulatorServiceConnection();
    context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    mConnection.bindToClient(this);
  }

  /** Contains client logic using SimulatorService api defined in ISimulatorService.aidl. */
  public abstract void process(ISimulatorService service) throws RemoteException;

  private void onServiceConnected(ISimulatorService service) {
    try {
      process(service);
    } catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  private void onServiceDisconnected() {}

  static class SimulatorServiceConnection implements ServiceConnection {

    private SimulatorServiceClient client;
    private ISimulatorService simulatorService;

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      simulatorService = ISimulatorService.Stub.asInterface(service);
      client.onServiceConnected(simulatorService);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      client.onServiceDisconnected();
    }

    void bindToClient(SimulatorServiceClient client) {
      this.client = client;
    }
  }
}
