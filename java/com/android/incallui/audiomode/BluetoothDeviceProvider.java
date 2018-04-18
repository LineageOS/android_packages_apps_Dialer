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

package com.android.incallui.audiomode;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.ArraySet;
import com.android.dialer.common.LogUtil;
import com.android.dialer.inject.ApplicationContext;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Proxy class for getting and setting connected/active Bluetooth devices. */
@Singleton
public final class BluetoothDeviceProvider extends BroadcastReceiver {

  // TODO(yueg): use BluetoothHeadset.ACTION_ACTIVE_DEVICE_CHANGED when possible
  private static final String ACTION_ACTIVE_DEVICE_CHANGED =
      "android.bluetooth.headset.profile.action.ACTIVE_DEVICE_CHANGED";

  private final Context appContext;
  private final BluetoothProfileServiceListener bluetoothProfileServiceListener =
      new BluetoothProfileServiceListener();

  private final Set<BluetoothDevice> connectedBluetoothDeviceSet = new ArraySet<>();

  private BluetoothDevice activeBluetoothDevice;
  private BluetoothHeadset bluetoothHeadset;
  private boolean isSetUp;

  @Inject
  public BluetoothDeviceProvider(@ApplicationContext Context appContext) {
    this.appContext = appContext;
  }

  public void setUp() {
    if (BluetoothAdapter.getDefaultAdapter() == null) {
      // Bluetooth is not supported on this hardware platform
      return;
    }
    // Get Bluetooth service including the initial connected device list (should only contain one
    // device)
    BluetoothAdapter.getDefaultAdapter()
        .getProfileProxy(appContext, bluetoothProfileServiceListener, BluetoothProfile.HEADSET);
    // Get notified of Bluetooth device update
    IntentFilter filter = new IntentFilter();
    filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
    filter.addAction(ACTION_ACTIVE_DEVICE_CHANGED);
    appContext.registerReceiver(this, filter);

    isSetUp = true;
  }

  public void tearDown() {
    if (!isSetUp) {
      return;
    }
    appContext.unregisterReceiver(this);
    if (bluetoothHeadset != null) {
      BluetoothAdapter.getDefaultAdapter()
          .closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset);
    }
  }

  public Set<BluetoothDevice> getConnectedBluetoothDeviceSet() {
    return connectedBluetoothDeviceSet;
  }

  public BluetoothDevice getActiveBluetoothDevice() {
    return activeBluetoothDevice;
  }

  @SuppressLint("PrivateApi")
  public void setActiveBluetoothDevice(BluetoothDevice bluetoothDevice) {
    if (!connectedBluetoothDeviceSet.contains(bluetoothDevice)) {
      LogUtil.e("BluetoothProfileServiceListener.setActiveBluetoothDevice", "device is not in set");
      return;
    }
    // TODO(yueg): use BluetoothHeadset.setActiveDevice() when possible
    try {
      Method getActiveDeviceMethod =
          bluetoothHeadset.getClass().getDeclaredMethod("setActiveDevice", BluetoothDevice.class);
      getActiveDeviceMethod.setAccessible(true);
      getActiveDeviceMethod.invoke(bluetoothHeadset, bluetoothDevice);
    } catch (Exception e) {
      LogUtil.e(
          "BluetoothProfileServiceListener.setActiveBluetoothDevice",
          "failed to call setActiveDevice",
          e);
    }
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    if (BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(intent.getAction())) {
      handleActionConnectionStateChanged(intent);
    } else if (ACTION_ACTIVE_DEVICE_CHANGED.equals(intent.getAction())) {
      handleActionActiveDeviceChanged(intent);
    }
  }

  private void handleActionConnectionStateChanged(Intent intent) {
    if (!intent.hasExtra(BluetoothDevice.EXTRA_DEVICE)) {
      LogUtil.i(
          "BluetoothDeviceProvider.handleActionConnectionStateChanged",
          "extra BluetoothDevice.EXTRA_DEVICE not found");
      return;
    }
    BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    if (bluetoothDevice == null) {
      return;
    }

    int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
    if (state == BluetoothProfile.STATE_DISCONNECTED) {
      connectedBluetoothDeviceSet.remove(bluetoothDevice);
      LogUtil.i("BluetoothDeviceProvider.handleActionConnectionStateChanged", "device removed");
    } else if (state == BluetoothProfile.STATE_CONNECTED) {
      connectedBluetoothDeviceSet.add(bluetoothDevice);
      LogUtil.i("BluetoothDeviceProvider.handleActionConnectionStateChanged", "device added");
    }
  }

  private void handleActionActiveDeviceChanged(Intent intent) {
    if (!intent.hasExtra(BluetoothDevice.EXTRA_DEVICE)) {
      LogUtil.i(
          "BluetoothDeviceProvider.handleActionActiveDeviceChanged",
          "extra BluetoothDevice.EXTRA_DEVICE not found");
      return;
    }
    activeBluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    LogUtil.i(
        "BluetoothDeviceProvider.handleActionActiveDeviceChanged",
        (activeBluetoothDevice == null ? "null" : ""));
  }

  private final class BluetoothProfileServiceListener implements BluetoothProfile.ServiceListener {
    @Override
    @SuppressLint("PrivateApi")
    public void onServiceConnected(int profile, BluetoothProfile bluetoothProfile) {
      if (profile != BluetoothProfile.HEADSET) {
        return;
      }
      // Get initial connected device list
      bluetoothHeadset = (BluetoothHeadset) bluetoothProfile;
      List<BluetoothDevice> devices = bluetoothProfile.getConnectedDevices();
      for (BluetoothDevice device : devices) {
        connectedBluetoothDeviceSet.add(device);
        LogUtil.i(
            "BluetoothProfileServiceListener.onServiceConnected", "get initial connected device");
      }

      // Get initial active device
      // TODO(yueg): use BluetoothHeadset.getActiveDevice() when possible
      try {
        Method getActiveDeviceMethod =
            bluetoothHeadset.getClass().getDeclaredMethod("getActiveDevice");
        getActiveDeviceMethod.setAccessible(true);
        activeBluetoothDevice = (BluetoothDevice) getActiveDeviceMethod.invoke(bluetoothHeadset);
        LogUtil.i(
            "BluetoothProfileServiceListener.onServiceConnected",
            "get initial active device" + ((activeBluetoothDevice == null) ? " null" : ""));
      } catch (Exception e) {
        LogUtil.e(
            "BluetoothProfileServiceListener.onServiceConnected",
            "failed to call getAcitveDevice",
            e);
      }
    }

    @Override
    public void onServiceDisconnected(int profile) {
      LogUtil.enterBlock("BluetoothProfileServiceListener.onServiceDisconnected");
      if (profile == BluetoothProfile.HEADSET) {
        bluetoothHeadset = null;
      }
    }
  }
}
