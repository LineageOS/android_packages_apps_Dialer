/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.voicemail.impl.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.VoicemailContract;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import com.android.voicemail.VoicemailComponent;
import com.android.voicemail.impl.ActivationTask;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.settings.VisualVoicemailSettingsUtil;
import java.util.List;

public class OmtpVvmSyncReceiver extends BroadcastReceiver {

  private static final String TAG = "OmtpVvmSyncReceiver";

  @Override
  public void onReceive(final Context context, Intent intent) {
    if (!VoicemailComponent.get(context).getVoicemailClient().isVoicemailModuleEnabled()) {
      // ACTION_SYNC_VOICEMAIL is available pre-O, ignore if received.
      return;
    }

    if (VoicemailContract.ACTION_SYNC_VOICEMAIL.equals(intent.getAction())) {
      VvmLog.v(TAG, "Sync intent received");

      List<PhoneAccountHandle> accounts =
          context.getSystemService(TelecomManager.class).getCallCapablePhoneAccounts();
      for (PhoneAccountHandle phoneAccount : accounts) {
        if (!VisualVoicemailSettingsUtil.isEnabled(context, phoneAccount)) {
          continue;
        }
        if (!VvmAccountManager.isAccountActivated(context, phoneAccount)) {
          VvmLog.i(TAG, "Unactivated account " + phoneAccount + " found, activating");
          ActivationTask.start(context, phoneAccount, null);
        } else {
          SyncTask.start(context, phoneAccount, OmtpVvmSyncService.SYNC_FULL_SYNC);
        }
      }
    }
  }
}
