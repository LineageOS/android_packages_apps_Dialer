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
import android.provider.VoicemailContract;
import android.telecom.PhoneAccountHandle;
import android.telephony.SmsMessage;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.sync.SyncTask;

/** Receive data SMS messages for the ADVVM protocol used by AT&T and others. */
public class AdvvmSmsReceiver extends BroadcastReceiver {

  private static final String TAG = "AdvvmSmsReceiver";

  private Context context;

  @Override
  public void onReceive(Context context, Intent intent) {
    this.context = context;
    VvmLog.i(TAG, "Received data SMS on port 5499, printing...");
    SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
    for (SmsMessage msg: messages) {
			Uri bodyUri = Uri.parse(msg.getMessageBody());
			String queryString = bodyUri.getEncodedQuery();
			String phoneString = null;
			if (queryString != null) {
				String[] queryParams = queryString.split("&");
				for (String queryParam : queryParams) {
					String[] paramPair = queryParam.split("=");
					if (paramPair.length == 2) {
						String paramName = paramPair[0];
						String paramValue = paramPair[1];
						VvmLog.i(TAG, paramName + " = " + paramValue);
						if (paramName == "m") {
							phoneString = paramValue;
						}
					}
				}
			}
			PhoneAccountHandle phone = new PhoneAccountHandle(null, phoneString);
			SyncTask.start(context, phone);
      VvmLog.i (TAG, msg.getMessageBody());
    }
    VvmLog.i(TAG, "Received data SMS on port 5499, done printing...");
  }
}
