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

package com.android.incallui.disconnectdialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.telecom.DisconnectCause;
import android.util.Pair;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.incallui.call.DialerCall;

/** Prompts the user to enable Wi-Fi calling. */
public class EnableWifiCallingPrompt implements DisconnectDialog {
  // This is a hidden constant in android.telecom.DisconnectCause. Telecom sets this as a disconnect
  // reason if it wants us to prompt the user to enable Wi-Fi calling. In Android-O we might
  // consider using a more explicit way to signal this.
  private static final String REASON_WIFI_ON_BUT_WFC_OFF = "REASON_WIFI_ON_BUT_WFC_OFF";
  private static final String ACTION_WIFI_CALLING_SETTINGS =
      "android.settings.WIFI_CALLING_SETTINGS";
  private static final String ANDROID_SETTINGS_PACKAGE = "com.android.settings";

  @Override
  public boolean shouldShow(DialerCall call, DisconnectCause disconnectCause) {
    String reason = disconnectCause.getReason();
    if (reason != null && reason.startsWith(REASON_WIFI_ON_BUT_WFC_OFF)) {
      LogUtil.i(
          "EnableWifiCallingPrompt.shouldShowPrompt",
          "showing prompt for disconnect cause: %s",
          reason);
      return true;
    }
    return false;
  }

  @Override
  public Pair<Dialog, CharSequence> createDialog(final @NonNull Context context, DialerCall call) {
    Assert.isNotNull(context);
    DisconnectCause cause = call.getDisconnectCause();
    CharSequence message = cause.getDescription();
    Dialog dialog =
        new AlertDialog.Builder(context)
            .setMessage(message)
            .setPositiveButton(
                R.string.incall_enable_wifi_calling_button,
                (OnClickListener) (dialog1, which) -> openWifiCallingSettings(context))
            .setNegativeButton(android.R.string.cancel, null)
            .create();
    return new Pair<>(dialog, message);
  }

  private static void openWifiCallingSettings(@NonNull Context context) {
    LogUtil.i("EnableWifiCallingPrompt.openWifiCallingSettings", "opening settings");
    context.startActivity(
        new Intent(ACTION_WIFI_CALLING_SETTINGS).setPackage(ANDROID_SETTINGS_PACKAGE));
  }
}
