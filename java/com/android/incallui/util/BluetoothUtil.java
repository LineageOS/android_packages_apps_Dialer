/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.incallui.util;

import android.bluetooth.BluetoothDevice;

public class BluetoothUtil {
    public static String getAliasName(BluetoothDevice bluetoothDevice) {
        try {
            return bluetoothDevice == null ? "" : bluetoothDevice.getAlias();
        } catch (SecurityException ignored) {
            return "";
        }
    }
}
