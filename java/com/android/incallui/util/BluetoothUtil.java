package com.android.incallui.util;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;

public class BluetoothUtil {
    @SuppressLint("MissingPermission")
    public static String getAliasName(BluetoothDevice bluetoothDevice) {
        return bluetoothDevice == null ? "" : bluetoothDevice.getAlias();
    }
}
