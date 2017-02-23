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

package com.android.incallui.wifi;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.telecom.DisconnectCause;
import android.util.Pair;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;

/** Prompts the user to enable Wi-Fi calling. */
public class EnableWifiCallingPrompt {
  // This is a hidden constant in android.telecom.DisconnectCause. Telecom sets this as a disconnect
  // reason if it wants us to prompt the user to enable Wi-Fi calling. In Android-O we might
  // consider using a more explicit way to signal this.
  private static final String REASON_WIFI_ON_BUT_WFC_OFF = "REASON_WIFI_ON_BUT_WFC_OFF";
  private static final String ACTION_WIFI_CALLING_SETTINGS =
      "android.settings.WIFI_CALLING_SETTINGS";
  private static final String ANDROID_SETTINGS_PACKAGE = "com.android.settings";

  public static boolean shouldShowPrompt(@NonNull DisconnectCause cause) {
    Assert.isNotNull(cause);
    if (cause.getReason() != null && cause.getReason().startsWith(REASON_WIFI_ON_BUT_WFC_OFF)) {
      LogUtil.i(
          "EnableWifiCallingPrompt.shouldShowPrompt",
          "showing prompt for disconnect cause: %s",
          cause);
      return true;
    }
    return false;
  }

  @NonNull
  public static Pair<Dialog, CharSequence> createDialog(
      final @NonNull Context context, @NonNull DisconnectCause cause) {
    Assert.isNotNull(context);
    Assert.isNotNull(cause);
    CharSequence message = cause.getDescription();
    Dialog dialog =
        new AlertDialog.Builder(context)
            .setMessage(message)
            .setPositiveButton(
                R.string.incall_enable_wifi_calling_button,
                new OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int which) {
                    openWifiCallingSettings(context);
                  }
                })
            .setNegativeButton(android.R.string.cancel, null)
            .create();
    return new Pair<Dialog, CharSequence>(dialog, message);
  }

  private static void openWifiCallingSettings(@NonNull Context context) {
    LogUtil.i("EnableWifiCallingPrompt.openWifiCallingSettings", "opening settings");
    context.startActivity(
        new Intent(ACTION_WIFI_CALLING_SETTINGS).setPackage(ANDROID_SETTINGS_PACKAGE));
  }

  private EnableWifiCallingPrompt() {}
}
