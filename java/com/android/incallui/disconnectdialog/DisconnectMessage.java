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
 * limitations under the License.
 */

package com.android.incallui.disconnectdialog;

import android.app.Dialog;
import android.content.Context;
import android.telecom.DisconnectCause;
import android.util.Pair;
import com.android.incallui.call.DialerCall;
import java.util.Locale;

/**
 * Wrapper class around @Code{android.telecom.DisconnectCause} to provide more information to user.
 */
public class DisconnectMessage {

  // Disconnect dialog catalog. Default error dialog MUST be last one.
  private static final DisconnectDialog[] DISCONNECT_DIALOGS =
      new DisconnectDialog[] {
        new EnableWifiCallingPrompt(), new VideoCallNotAvailablePrompt(), new DefaultErrorDialog()
      };

  public final Dialog dialog;
  public final CharSequence toastMessage;
  private final DisconnectCause cause;

  public DisconnectMessage(Context context, DialerCall call) {
    cause = call.getDisconnectCause();

    for (DisconnectDialog disconnectDialog : DISCONNECT_DIALOGS) {
      if (disconnectDialog.shouldShow(cause)) {
        Pair<Dialog, CharSequence> pair = disconnectDialog.createDialog(context, call);
        dialog = pair.first;
        toastMessage = pair.second;
        return;
      }
    }
    dialog = null;
    toastMessage = null;
  }

  @Override
  public String toString() {
    return String.format(
        Locale.ENGLISH,
        "DisconnectMessage {code: %d, description: %s, reason: %s, message: %s}",
        cause.getCode(),
        cause.getDescription(),
        cause.getReason(),
        toastMessage);
  }
}
