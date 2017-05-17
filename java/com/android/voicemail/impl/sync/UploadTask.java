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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telecom.PhoneAccountHandle;
import com.android.dialer.proguard.UsedByReflection;
import com.android.voicemail.impl.VoicemailStatus;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.scheduling.BaseTask;
import com.android.voicemail.impl.scheduling.PostponePolicy;

/**
 * Upload task triggered by database changes. Will wait until the database has been stable for
 * {@link #POSTPONE_MILLIS} to execute.
 */
@UsedByReflection(value = "Tasks.java")
public class UploadTask extends BaseTask {

  private static final String TAG = "VvmUploadTask";

  private static final int POSTPONE_MILLIS = 5_000;

  public UploadTask() {
    super(TASK_UPLOAD);
    addPolicy(new PostponePolicy(POSTPONE_MILLIS));
  }

  public static void start(Context context, PhoneAccountHandle phoneAccountHandle) {
    Intent intent = BaseTask.createIntent(context, UploadTask.class, phoneAccountHandle);
    context.sendBroadcast(intent);
  }

  @Override
  public void onCreate(Context context, Bundle extras) {
    super.onCreate(context, extras);
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
    service.sync(
        this,
        OmtpVvmSyncService.SYNC_UPLOAD_ONLY,
        phoneAccountHandle,
        null,
        VoicemailStatus.edit(getContext(), phoneAccountHandle));
  }
}
