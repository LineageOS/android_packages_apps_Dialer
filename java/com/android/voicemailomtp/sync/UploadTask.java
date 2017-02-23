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

import android.content.Context;
import android.content.Intent;
import android.telecom.PhoneAccountHandle;

import com.android.voicemailomtp.VoicemailStatus;
import com.android.voicemailomtp.VvmLog;
import com.android.voicemailomtp.scheduling.BaseTask;
import com.android.voicemailomtp.scheduling.PostponePolicy;

/**
 * Upload task triggered by database changes. Will wait until the database has been stable for
 * {@link #POSTPONE_MILLIS} to execute.
 */
public class UploadTask extends BaseTask {

    private static final String TAG = "VvmUploadTask";

    private static final int POSTPONE_MILLIS = 5_000;

    public UploadTask() {
        super(TASK_UPLOAD);
        addPolicy(new PostponePolicy(POSTPONE_MILLIS));
    }

    public static void start(Context context, PhoneAccountHandle phoneAccountHandle) {
        Intent intent = BaseTask
                .createIntent(context, UploadTask.class, phoneAccountHandle);
        context.startService(intent);
    }

    @Override
    public void onCreate(Context context, Intent intent, int flags, int startId) {
        super.onCreate(context, intent, flags, startId);
    }

    @Override
    public void onExecuteInBackgroundThread() {
        OmtpVvmSyncService service = new OmtpVvmSyncService(getContext());

        PhoneAccountHandle phoneAccountHandle = getPhoneAccountHandle();
        if (phoneAccountHandle == null) {
            // This should never happen
            VvmLog.e(TAG, "null phone account for phoneAccountHandle " + getPhoneAccountHandle());
            return;
        }
        service.sync(this, OmtpVvmSyncService.SYNC_UPLOAD_ONLY,
                phoneAccountHandle, null,
                VoicemailStatus.edit(getContext(), phoneAccountHandle));
    }
}
