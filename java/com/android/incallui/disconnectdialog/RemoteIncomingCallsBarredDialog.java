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

public class RemoteIncomingCallsBarredDialog implements DisconnectDialog {
  @Override
  public boolean shouldShow(DialerCall call, DisconnectCause disconnectCause) {
    return call.missedBecauseIncomingCallsBarredRemotely();
  }

  @Override
  public Pair<Dialog, CharSequence> createDialog(final @NonNull Context context, DialerCall call) {
    CharSequence message = context.getString(R.string.callFailed_incoming_cb_enabled);
    Dialog dialog =
        new AlertDialog.Builder(context)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .create();
    return new Pair<>(dialog, message);
  }
}
