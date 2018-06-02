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
import android.telecom.VideoProfile;
import com.android.dialer.callintent.CallInitiationType.Type;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProvider;
import com.android.dialer.configprovider.ConfigProviderComponent;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.precall.PreCall;
import com.google.common.collect.ImmutableList;

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

  private static final ImmutableList<String> HANDLED_INTENT_EXTRAS =
      ImmutableList.of(
          TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
          TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS,
          TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
          TelecomManager.EXTRA_CALL_SUBJECT,
          EXTRA_PHONE_ACCOUNT_HANDLE,
          EXTRA_IS_VIDEO_CALL,
          EXTRA_CALL_SUBJECT,
          EXTRA_ALLOW_ASSISTED_DIAL);

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Logger.get(this).logImpression(DialerImpression.Type.PRECALL_INITIATED_EXTERNAL);

    ConfigProvider configProvider =
        ConfigProviderComponent.get(getApplicationContext()).getConfigProvider();
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
    filterExtras(intent.getExtras(), builder);
    PreCall.start(this, builder);
    finish();
  }

  /**
   * Move key-value pairs that {@link CallIntentBuilder} can handle from {@code intentExtras} to
   * {@code builder}
   */
  private void filterExtras(@Nullable Bundle intentExtras, CallIntentBuilder builder) {
    if (intentExtras == null) {
      return;
    }
    Bundle bundle = new Bundle();
    bundle.putAll(intentExtras);

    if (intentExtras.containsKey(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE)) {
      int videoState = intentExtras.getInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE);
      switch (videoState) {
        case VideoProfile.STATE_BIDIRECTIONAL:
          builder.setIsVideoCall(true);
          break;
        case VideoProfile.STATE_AUDIO_ONLY:
          builder.setIsVideoCall(false);
          break;
        case VideoProfile.STATE_RX_ENABLED:
        case VideoProfile.STATE_TX_ENABLED:
          LogUtil.w(
              "LaunchPreCallActivity.filterExtras",
              "unsupported video state " + videoState + ", overriding to STATE_BIDIRECTIONAL");
          builder.setIsVideoCall(true);
          break;
        default:
          LogUtil.w("LaunchPreCallActivity.filterExtras", "unknown video state " + videoState);
          builder.setIsVideoCall(false);
      }
    }

    if (intentExtras.containsKey(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS)) {
      builder
          .getInCallUiIntentExtras()
          .putAll(intentExtras.getBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS));
    }

    if (intentExtras.containsKey(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE)) {
      builder.setPhoneAccountHandle(
          intentExtras.getParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE));
    }

    if (intentExtras.containsKey(TelecomManager.EXTRA_CALL_SUBJECT)) {
      builder.setCallSubject(intentExtras.getString(TelecomManager.EXTRA_CALL_SUBJECT));
    }

    for (String handledKey : HANDLED_INTENT_EXTRAS) {
      bundle.remove(handledKey);
    }
    builder.getPlaceCallExtras().putAll(bundle);
  }
}
