/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.dialer.util;

import android.Manifest.permission;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import com.android.dialer.common.LogUtil;

/** Utility class to help with runtime permissions. */
public class PermissionsUtil {

  private static final String PERMISSION_PREFERENCE = "dialer_permissions";
  private static final String CEQUINT_PERMISSION = "com.cequint.ecid.CALLER_ID_LOOKUP";

  public static boolean hasPhonePermissions(Context context) {
    return hasPermission(context, permission.CALL_PHONE);
  }

  public static boolean hasContactsReadPermissions(Context context) {
    return hasPermission(context, permission.READ_CONTACTS);
  }

  public static boolean hasLocationPermissions(Context context) {
    return hasPermission(context, permission.ACCESS_FINE_LOCATION);
  }

  public static boolean hasCameraPermissions(Context context) {
    return hasPermission(context, permission.CAMERA);
  }

  public static boolean hasMicrophonePermissions(Context context) {
    return hasPermission(context, permission.RECORD_AUDIO);
  }

  public static boolean hasCallLogReadPermissions(Context context) {
    return hasPermission(context, permission.READ_CALL_LOG);
  }

  public static boolean hasCallLogWritePermissions(Context context) {
    return hasPermission(context, permission.WRITE_CALL_LOG);
  }

  public static boolean hasCequintPermissions(Context context) {
    return hasPermission(context, CEQUINT_PERMISSION);
  }

  public static boolean hasReadVoicemailPermissions(Context context) {
    return hasPermission(context, permission.READ_VOICEMAIL);
  }

  public static boolean hasWriteVoicemailPermissions(Context context) {
    return hasPermission(context, permission.WRITE_VOICEMAIL);
  }

  public static boolean hasAddVoicemailPermissions(Context context) {
    return hasPermission(context, permission.ADD_VOICEMAIL);
  }

  public static boolean hasPermission(Context context, String permission) {
    return ContextCompat.checkSelfPermission(context, permission)
        == PackageManager.PERMISSION_GRANTED;
  }

  /**
   * Checks {@link android.content.SharedPreferences} if a permission has been requested before.
   *
   * <p>It is important to note that this method only works if you call {@link
   * PermissionsUtil#permissionRequested(Context, String)} in {@link
   * android.app.Activity#onRequestPermissionsResult(int, String[], int[])}.
   */
  public static boolean isFirstRequest(Context context, String permission) {
    return context
        .getSharedPreferences(PERMISSION_PREFERENCE, Context.MODE_PRIVATE)
        .getBoolean(permission, true);
  }

  /**
   * Records in {@link android.content.SharedPreferences} that the specified permission has been
   * requested at least once.
   *
   * <p>This method should be called in {@link android.app.Activity#onRequestPermissionsResult(int,
   * String[], int[])}.
   */
  public static void permissionRequested(Context context, String permission) {
    context
        .getSharedPreferences(PERMISSION_PREFERENCE, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(permission, false)
        .apply();
  }

  /**
   * Rudimentary methods wrapping the use of a LocalBroadcastManager to simplify the process of
   * notifying other classes when a particular fragment is notified that a permission is granted.
   *
   * <p>To be notified when a permission has been granted, create a new broadcast receiver and
   * register it using {@link #registerPermissionReceiver(Context, BroadcastReceiver, String)}
   *
   * <p>E.g.
   *
   * <p>final BroadcastReceiver receiver = new BroadcastReceiver() { @Override public void
   * onReceive(Context context, Intent intent) { refreshContactsView(); } }
   *
   * <p>PermissionsUtil.registerPermissionReceiver(getActivity(), receiver, READ_CONTACTS);
   *
   * <p>If you register to listen for multiple permissions, you can identify which permission was
   * granted by inspecting {@link Intent#getAction()}.
   *
   * <p>In the fragment that requests for the permission, be sure to call {@link
   * #notifyPermissionGranted(Context, String)} when the permission is granted so that any
   * interested listeners are notified of the change.
   */
  public static void registerPermissionReceiver(
      Context context, BroadcastReceiver receiver, String permission) {
    LogUtil.i("PermissionsUtil.registerPermissionReceiver", permission);
    final IntentFilter filter = new IntentFilter(permission);
    LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter);
  }

  public static void unregisterPermissionReceiver(Context context, BroadcastReceiver receiver) {
    LogUtil.i("PermissionsUtil.unregisterPermissionReceiver", null);
    LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver);
  }

  public static void notifyPermissionGranted(Context context, String permission) {
    LogUtil.i("PermissionsUtil.notifyPermissionGranted", permission);
    final Intent intent = new Intent(permission);
    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
  }
}
