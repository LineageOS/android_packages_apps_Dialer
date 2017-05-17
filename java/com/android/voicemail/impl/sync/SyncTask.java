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
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.proguard.UsedByReflection;
import com.android.voicemail.impl.scheduling.BaseTask;
import com.android.voicemail.impl.scheduling.MinimalIntervalPolicy;
import com.android.voicemail.impl.scheduling.RetryPolicy;
import com.android.voicemail.impl.utils.LoggerUtils;

/** System initiated sync request. */
@UsedByReflection(value = "Tasks.java")
public class SyncTask extends BaseTask {

  // Try sync for a total of 5 times, should take around 5 minutes before finally giving up.
  private static final int RETRY_TIMES = 4;
  private static final int RETRY_INTERVAL_MILLIS = 5_000;
  private static final int MINIMAL_INTERVAL_MILLIS = 60_000;

  private static final String EXTRA_PHONE_ACCOUNT_HANDLE = "extra_phone_account_handle";
  private static final String EXTRA_SYNC_TYPE = "extra_sync_type";

  private final RetryPolicy mRetryPolicy;

  private PhoneAccountHandle mPhone;
  private String mSyncType;

  public static void start(Context context, PhoneAccountHandle phone, String syncType) {
    Intent intent = BaseTask.createIntent(context, SyncTask.class, phone);
    intent.putExtra(EXTRA_PHONE_ACCOUNT_HANDLE, phone);
    intent.putExtra(EXTRA_SYNC_TYPE, syncType);
    context.sendBroadcast(intent);
  }

  public SyncTask() {
    super(TASK_SYNC);
    mRetryPolicy = new RetryPolicy(RETRY_TIMES, RETRY_INTERVAL_MILLIS);
    addPolicy(mRetryPolicy);
    addPolicy(new MinimalIntervalPolicy(MINIMAL_INTERVAL_MILLIS));
  }

  @Override
  public void onCreate(Context context, Bundle extras) {
    super.onCreate(context, extras);
    mPhone = extras.getParcelable(EXTRA_PHONE_ACCOUNT_HANDLE);
    mSyncType = extras.getString(EXTRA_SYNC_TYPE);
  }

  @Override
  public void onExecuteInBackgroundThread() {
    OmtpVvmSyncService service = new OmtpVvmSyncService(getContext());
    service.sync(this, mSyncType, mPhone, null, mRetryPolicy.getVoicemailStatusEditor());
  }

  @Override
  public Intent createRestartIntent() {
    LoggerUtils.logImpressionOnMainThread(getContext(), DialerImpression.Type.VVM_AUTO_RETRY_SYNC);
    Intent intent = super.createRestartIntent();
    intent.putExtra(EXTRA_PHONE_ACCOUNT_HANDLE, mPhone);
    intent.putExtra(EXTRA_SYNC_TYPE, mSyncType);
    return intent;
  }
}
