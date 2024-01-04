/*
 * Copyright (C) 2024 The LineageOS Project
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
package com.android.voicemail.impl.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import com.android.dialer.telecom.TelecomUtil;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import com.android.voicemail.impl.sync.SyncTask;

/** Receive data SMS messages for the ADVVM protocol used by AT&T and others. */
public class AdvvmSmsReceiver extends BroadcastReceiver {

  private static final String TAG = "AdvvmSmsReceiver";

  @Override
  public void onReceive(Context context, Intent intent) {
    SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
    for (SmsMessage msg: messages) {
      // in order to query parameters we need to have a `scheme://`
      Uri bodyUri = Uri.parse("advvm://" + msg.getMessageBody());
      PhoneAccountHandle phone =
          TelecomUtil.getDefaultOutgoingPhoneAccount(context, PhoneAccount.SCHEME_VOICEMAIL);
      SyncTask.start(context, phone);
    }
  }
}
