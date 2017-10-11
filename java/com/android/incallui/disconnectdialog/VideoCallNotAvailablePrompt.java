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
import android.content.Intent;
import android.support.annotation.NonNull;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.util.Pair;
import com.android.contacts.common.compat.telecom.TelecomManagerCompat;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.LogUtil;
import com.android.dialer.util.DialerUtils;
import com.android.incallui.call.DialerCall;

/** Prompt user to make voice call if video call is not currently available. */
public class VideoCallNotAvailablePrompt implements DisconnectDialog {

  @Override
  public boolean shouldShow(DialerCall call, DisconnectCause disconnectCause) {
    if (disconnectCause.getCode() == DisconnectCause.ERROR
        && TelecomManagerCompat.REASON_IMS_ACCESS_BLOCKED.equals(disconnectCause.getReason())) {
      LogUtil.i(
          "VideoCallNotAvailablePrompt.shouldShowPrompt",
          "showing prompt for disconnect cause: %s",
          disconnectCause.getReason());
      return true;
    } else {
      return false;
    }
  }

  @Override
  public Pair<Dialog, CharSequence> createDialog(@NonNull Context context, DialerCall call) {
    CharSequence title = context.getString(R.string.video_call_not_available_title);

    Dialog dialog =
        new AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(context.getString(R.string.video_call_not_available_message))
            .setPositiveButton(
                R.string.voice_call,
                (dialog1, which) ->
                    makeVoiceCall(context, call.getNumber(), call.getAccountHandle()))
            .setNegativeButton(android.R.string.cancel, null)
            .create();
    return new Pair<>(dialog, title);
  }

  private void makeVoiceCall(Context context, String number, PhoneAccountHandle accountHandle) {
    LogUtil.enterBlock("VideoCallNotAvailablePrompt.makeVoiceCall");
    Intent intent =
        new CallIntentBuilder(number, CallInitiationType.Type.IMS_VIDEO_BLOCKED_FALLBACK_TO_VOICE)
            .setPhoneAccountHandle(accountHandle)
            .build();
    DialerUtils.startActivityWithErrorToast(context, intent);
  }
}
