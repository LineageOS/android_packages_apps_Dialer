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

package com.android.voicemail.impl;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import com.android.voicemail.VoicemailComponent;

/** Receives MY_PACKAGE_REPLACED to trigger VVM activation. */
public class PackageReplacedReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    VvmLog.i("PackageReplacedReceiver.onReceive", "package replaced, starting activation");

    if (!VoicemailComponent.get(context).getVoicemailClient().isVoicemailModuleEnabled()) {
      VvmLog.e("PackageReplacedReceiver.onReceive", "module disabled");
      return;
    }

    for (PhoneAccountHandle phoneAccountHandle :
        context.getSystemService(TelecomManager.class).getCallCapablePhoneAccounts()) {
      ActivationTask.start(context, phoneAccountHandle, null);
    }
  }
}
