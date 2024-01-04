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
import android.provider.Telephony;
import android.telephony.SmsMessage;
import com.android.voicemail.impl.VvmLog;

/** Receive data SMS messages for the ADVVM protocol used by AT&T and others. */
public class AdvvmSmsReceiver extends BroadcastReceiver {

  private static final String TAG = "AdvvmSmsReceiver";

  private Context context;

  @Override
  public void onReceive(Context context, Intent intent) {
    this.context = context;
    VvmLog.i(TAG, "Received data sms on port 5499, printing...");
    SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
    for (SmsMessage msg: messages) {
      VvmLog.i (TAG, msg.getMessageBody());
    }
    VvmLog.i(TAG, "Received data sms on port 5499, done printing...");
  }
}
