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

package com.android.voicemail.impl;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telecom.PhoneAccountHandle;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.voicemail.VoicemailClient;
import com.android.voicemail.VoicemailComponent;
import com.android.voicemail.impl.sync.UploadTask;
import com.android.voicemail.impl.sync.VvmAccountManager;

/** Receiver for broadcasts in {@link VoicemailClient#ACTION_UPLOAD} */
public class VoicemailClientReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    if (!VoicemailComponent.get(context).getVoicemailClient().isVoicemailModuleEnabled()) {
      LogUtil.i(
          "VoicemailClientReceiver.onReceive", "module disabled, ignoring " + intent.getAction());
      return;
    }
    switch (intent.getAction()) {
      case VoicemailClient.ACTION_UPLOAD:
        doUpload(context);
        break;
      default:
        Assert.fail("Unexpected action " + intent.getAction());
        break;
    }
  }

  /** Upload local database changes to the server. */
  private static void doUpload(Context context) {
    LogUtil.i("VoicemailClientReceiver.onReceive", "ACTION_UPLOAD received");
    for (PhoneAccountHandle phoneAccountHandle : VvmAccountManager.getActiveAccounts(context)) {
      UploadTask.start(context, phoneAccountHandle);
    }
  }
}
