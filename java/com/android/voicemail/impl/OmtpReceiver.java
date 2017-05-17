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

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build.VERSION_CODES;
import android.telecom.PhoneAccountHandle;
import android.telephony.VisualVoicemailSms;
import com.android.dialer.common.Assert;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.voicemail.VoicemailComponent;
import com.android.voicemail.impl.settings.VisualVoicemailSettingsUtil;
import com.android.voicemail.impl.sync.VvmAccountManager;

/** Listens to com.android.phone.vvm.ACTION_TEMP_VISUAL_VOICEMAIL_SERVICE_EVENT */
@TargetApi(VERSION_CODES.O)
public class OmtpReceiver extends BroadcastReceiver {

  private static final String TAG = "VvmOmtpReceiver";

  public static final String ACTION_SMS_RECEIVED = "com.android.vociemailomtp.sms.sms_received";

  public static final String EXTRA_VOICEMAIL_SMS = "extra_voicemail_sms";

  private static final String EXTRA_WHAT = "what";

  private static final int MSG_ON_CELL_SERVICE_CONNECTED = 1;

  private static final int MSG_ON_SMS_RECEIVED = 2;

  private static final int MSG_ON_SIM_REMOVED = 3;

  private static final int MSG_TASK_STOPPED = 5;

  private static final String DATA_PHONE_ACCOUNT_HANDLE = "data_phone_account_handle";

  private static final String DATA_SMS = "data_sms";

  @Override
  public void onReceive(Context context, Intent intent) {
    // ACTION_VISUAL_VOICEMAIL_SERVICE_EVENT is not a protected broadcast pre-O.
    if (!VoicemailComponent.get(context).getVoicemailClient().isVoicemailModuleEnabled()) {
      VvmLog.e(TAG, "ACTION_VISUAL_VOICEMAIL_SERVICE_EVENT received when module is disabled");
      return;
    }

    int what = intent.getIntExtra(EXTRA_WHAT, -1);
    PhoneAccountHandle phoneAccountHandle = intent.getParcelableExtra(DATA_PHONE_ACCOUNT_HANDLE);
    OmtpVvmCarrierConfigHelper config = new OmtpVvmCarrierConfigHelper(context, phoneAccountHandle);
    if (!config.isValid()) {
      VvmLog.i(TAG, "VVM not supported on " + phoneAccountHandle);
      return;
    }
    if (!VisualVoicemailSettingsUtil.isEnabled(context, phoneAccountHandle)
        && !config.isLegacyModeEnabled()) {
      VvmLog.i(TAG, "VVM is disabled");
      return;
    }
    switch (what) {
      case MSG_ON_CELL_SERVICE_CONNECTED:
        VvmLog.i(TAG, "onCellServiceConnected");
        Logger.get(context).logImpression(DialerImpression.Type.VVM_UNBUNDLED_EVENT_RECEIVED);
        ActivationTask.start(context, phoneAccountHandle, null);
        break;
      case MSG_ON_SMS_RECEIVED:
        VvmLog.i(TAG, "onSmsReceived");
        Logger.get(context).logImpression(DialerImpression.Type.VVM_UNBUNDLED_EVENT_RECEIVED);
        VisualVoicemailSms sms = intent.getParcelableExtra(DATA_SMS);
        Intent receivedIntent = new Intent(ACTION_SMS_RECEIVED);
        receivedIntent.setPackage(context.getPackageName());
        receivedIntent.putExtra(EXTRA_VOICEMAIL_SMS, sms);
        context.sendBroadcast(receivedIntent);
        break;
      case MSG_ON_SIM_REMOVED:
        VvmLog.i(TAG, "onSimRemoved");
        Logger.get(context).logImpression(DialerImpression.Type.VVM_UNBUNDLED_EVENT_RECEIVED);
        VvmAccountManager.removeAccount(context, phoneAccountHandle);
        break;
      case MSG_TASK_STOPPED:
        VvmLog.i(TAG, "onStopped");
        Logger.get(context).logImpression(DialerImpression.Type.VVM_UNBUNDLED_EVENT_RECEIVED);
        break;
      default:
        throw Assert.createIllegalStateFailException("unexpected what: " + what);
    }
  }
}
