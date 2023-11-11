/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.incallui.util;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Context;

import com.android.dialer.util.PermissionsUtil;

public class BluetoothUtil {
    @SuppressLint("MissingPermission")
    public static String getAliasName(Context context, BluetoothDevice bluetoothDevice) {
        if (!PermissionsUtil.hasBluetoothConnectPermissions(context)) {
            return "";
        }
        return bluetoothDevice == null ? "" : bluetoothDevice.getAlias();
    }
}
