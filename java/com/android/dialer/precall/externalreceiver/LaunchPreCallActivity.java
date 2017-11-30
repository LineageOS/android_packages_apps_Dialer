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

package com.android.dialer.precall.externalreceiver;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import com.android.dialer.callintent.CallInitiationType.Type;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.configprovider.ConfigProvider;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.precall.PreCall;

/**
 * Activity that forwards to {@link PreCall#start(Context, CallIntentBuilder)} so the pre-call flow
 * can be initiated by external apps. This activity is exported but can only be started by apps with
 * {@link android.Manifest.permission#CALL_PHONE}. Keyguard will be triggered if phone is locked.
 *
 * @see CallIntentBuilder
 */
public class LaunchPreCallActivity extends Activity {

  public static final String ACTION_LAUNCH_PRE_CALL = "com.android.dialer.LAUNCH_PRE_CALL";

  public static final String EXTRA_PHONE_ACCOUNT_HANDLE = "phone_account_handle";

  public static final String EXTRA_IS_VIDEO_CALL = "is_video_call";

  public static final String EXTRA_CALL_SUBJECT = "call_subject";

  public static final String EXTRA_ALLOW_ASSISTED_DIAL = "allow_assisted_dial";

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Logger.get(this).logImpression(DialerImpression.Type.PRECALL_INITIATED_EXTERNAL);

    ConfigProvider configProvider = ConfigProviderBindings.get(getApplicationContext());
    Intent intent = getIntent();
    CallIntentBuilder builder = new CallIntentBuilder(intent.getData(), Type.EXTERNAL_INITIATION);

    PhoneAccountHandle phoneAccountHandle = intent.getParcelableExtra(EXTRA_PHONE_ACCOUNT_HANDLE);
    if (phoneAccountHandle == null) {
      phoneAccountHandle = intent.getParcelableExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE);
    }

    builder
        .setPhoneAccountHandle(phoneAccountHandle)
        .setIsVideoCall(intent.getBooleanExtra(EXTRA_IS_VIDEO_CALL, false))
        .setCallSubject(intent.getStringExtra(EXTRA_CALL_SUBJECT))
        .setAllowAssistedDial(
            intent.getBooleanExtra(
                EXTRA_ALLOW_ASSISTED_DIAL,
                configProvider.getBoolean("assisted_dialing_default_precall_state", false)));
    PreCall.start(this, builder);
    finish();
  }
}
