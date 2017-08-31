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
import android.support.annotation.NonNull;
import android.telecom.DisconnectCause;
import android.text.TextUtils;
import android.util.Pair;
import com.android.incallui.call.DialerCall;

/** Default error dialog shown to user after disconnect. */
public class DefaultErrorDialog implements DisconnectDialog {

  @Override
  public boolean shouldShow(DisconnectCause disconnectCause) {
    return !TextUtils.isEmpty(disconnectCause.getDescription())
        && (disconnectCause.getCode() == DisconnectCause.ERROR
            || disconnectCause.getCode() == DisconnectCause.RESTRICTED);
  }

  @Override
  public Pair<Dialog, CharSequence> createDialog(@NonNull Context context, DialerCall call) {
    DisconnectCause disconnectCause = call.getDisconnectCause();
    CharSequence message = disconnectCause.getDescription();

    Dialog dialog =
        new AlertDialog.Builder(context)
            .setMessage(message)
            .setPositiveButton(android.R.string.cancel, null)
            .create();
    return new Pair<>(dialog, message);
  }
}
