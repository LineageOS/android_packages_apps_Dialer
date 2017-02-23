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

package com.android.voicemailomtp.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.VoicemailContract;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import com.android.voicemailomtp.ActivationTask;
import com.android.voicemailomtp.VvmLog;
import com.android.voicemailomtp.settings.VisualVoicemailSettingsUtil;

import java.util.List;

public class OmtpVvmSyncReceiver extends BroadcastReceiver {

    private static final String TAG = "OmtpVvmSyncReceiver";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (VoicemailContract.ACTION_SYNC_VOICEMAIL.equals(intent.getAction())) {
            VvmLog.v(TAG, "Sync intent received");
            for (PhoneAccountHandle source : OmtpVvmSourceManager.getInstance(context)
                    .getOmtpVvmSources()) {
                SyncTask.start(context, source, OmtpVvmSyncService.SYNC_FULL_SYNC);
            }
            activateUnactivatedAccounts(context);
        }
    }

    private static void activateUnactivatedAccounts(Context context) {
        List<PhoneAccountHandle> accounts =
                context.getSystemService(TelecomManager.class).getCallCapablePhoneAccounts();
        for (PhoneAccountHandle phoneAccount : accounts) {
            if (!VisualVoicemailSettingsUtil.isEnabled(context, phoneAccount)) {
                continue;
            }
            if (!OmtpVvmSourceManager.getInstance(context).isVvmSourceRegistered(phoneAccount)) {
                VvmLog.i(TAG, "Unactivated account " + phoneAccount + " found, activating");
                ActivationTask.start(context, phoneAccount, null);
            }
        }
    }
}
