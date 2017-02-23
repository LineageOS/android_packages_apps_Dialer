/*
 * Copyright (C) 2015 The Android Open Source Project
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

/**
 * Receives changes to the voicemail provider so they can be sent to the voicemail server.
 */
public class VoicemailProviderChangeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean isSelfChanged = intent.getBooleanExtra(VoicemailContract.EXTRA_SELF_CHANGE, false);
        OmtpVvmSourceManager vvmSourceManager =
                OmtpVvmSourceManager.getInstance(context);
        if (vvmSourceManager.getOmtpVvmSources().size() > 0 && !isSelfChanged) {
            for (PhoneAccountHandle source : OmtpVvmSourceManager.getInstance(context)
                    .getOmtpVvmSources()) {
                UploadTask.start(context, source);
            }
        }
    }
}
