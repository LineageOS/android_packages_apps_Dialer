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
import android.content.Intent;
import android.os.Build.VERSION_CODES;
import android.os.UserManager;
import android.telecom.PhoneAccountHandle;
import android.telephony.VisualVoicemailService;
import android.telephony.VisualVoicemailSms;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.voicemail.VoicemailComponent;
import com.android.voicemail.impl.settings.VisualVoicemailSettingsUtil;
import com.android.voicemail.impl.sms.LegacyModeSmsHandler;
import com.android.voicemail.impl.sync.VvmAccountManager;

/** Implements {@link VisualVoicemailService} to receive visual voicemail events */
@TargetApi(VERSION_CODES.O)
public class OmtpService extends VisualVoicemailService {

  private static final String TAG = "VvmOmtpService";

  public static final String ACTION_SMS_RECEIVED = "com.android.vociemailomtp.sms.sms_received";

  public static final String EXTRA_VOICEMAIL_SMS = "extra_voicemail_sms";

  @Override
  public void onCellServiceConnected(
      VisualVoicemailTask task, final PhoneAccountHandle phoneAccountHandle) {
    VvmLog.i(TAG, "onCellServiceConnected");
    if (!isModuleEnabled()) {
      VvmLog.e(TAG, "onCellServiceConnected received when module is disabled");
      task.finish();
      return;
    }

    if (!isUserUnlocked()) {
      VvmLog.i(TAG, "onCellServiceConnected: user locked");
      task.finish();
      return;
    }

    if (!isServiceEnabled(phoneAccountHandle)) {
      task.finish();
      return;
    }

    Logger.get(this).logImpression(DialerImpression.Type.VVM_UNBUNDLED_EVENT_RECEIVED);
    ActivationTask.start(OmtpService.this, phoneAccountHandle, null);
    task.finish();
  }

  @Override
  public void onSmsReceived(VisualVoicemailTask task, final VisualVoicemailSms sms) {
    VvmLog.i(TAG, "onSmsReceived");
    if (!isModuleEnabled()) {
      VvmLog.e(TAG, "onSmsReceived received when module is disabled");
      task.finish();
      return;
    }

    if (!isUserUnlocked()) {
      LegacyModeSmsHandler.handle(this, sms);
      return;
    }

    if (!isServiceEnabled(sms.getPhoneAccountHandle())) {
      task.finish();
      return;
    }

    // isUserUnlocked() is not checked. OmtpMessageReceiver will handle the locked case.

    Logger.get(this).logImpression(DialerImpression.Type.VVM_UNBUNDLED_EVENT_RECEIVED);
    Intent intent = new Intent(ACTION_SMS_RECEIVED);
    intent.setPackage(getPackageName());
    intent.putExtra(EXTRA_VOICEMAIL_SMS, sms);
    sendBroadcast(intent);
    task.finish();
  }

  @Override
  public void onSimRemoved(
      final VisualVoicemailTask task, final PhoneAccountHandle phoneAccountHandle) {
    VvmLog.i(TAG, "onSimRemoved");
    if (!isModuleEnabled()) {
      VvmLog.e(TAG, "onSimRemoved called when module is disabled");
      task.finish();
      return;
    }

    if (!isUserUnlocked()) {
      VvmLog.i(TAG, "onSimRemoved: user locked");
      task.finish();
      return;
    }

    Logger.get(this).logImpression(DialerImpression.Type.VVM_UNBUNDLED_EVENT_RECEIVED);
    VvmAccountManager.removeAccount(this, phoneAccountHandle);
    task.finish();
  }

  @Override
  public void onStopped(VisualVoicemailTask task) {
    VvmLog.i(TAG, "onStopped");
    if (!isModuleEnabled()) {
      VvmLog.e(TAG, "onStopped called when module is disabled");
      task.finish();
      return;
    }
    if (!isUserUnlocked()) {
      VvmLog.i(TAG, "onStopped: user locked");
      task.finish();
      return;
    }
    Logger.get(this).logImpression(DialerImpression.Type.VVM_UNBUNDLED_EVENT_RECEIVED);
  }

  private boolean isModuleEnabled() {
    return VoicemailComponent.get(this).getVoicemailClient().isVoicemailModuleEnabled();
  }

  private boolean isServiceEnabled(PhoneAccountHandle phoneAccountHandle) {
    OmtpVvmCarrierConfigHelper config = new OmtpVvmCarrierConfigHelper(this, phoneAccountHandle);
    if (!config.isValid()) {
      VvmLog.i(TAG, "VVM not supported on " + phoneAccountHandle);
      return false;
    }
    if (!VisualVoicemailSettingsUtil.isEnabled(this, phoneAccountHandle)
        && !config.isLegacyModeEnabled()) {
      VvmLog.i(TAG, "VVM is disabled");
      return false;
    }
    return true;
  }

  private boolean isUserUnlocked() {
    UserManager userManager = getSystemService(UserManager.class);
    return userManager.isUserUnlocked();
  }
}
