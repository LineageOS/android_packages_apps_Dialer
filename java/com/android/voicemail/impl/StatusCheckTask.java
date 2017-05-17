/**
 * Copyright (C) 2017 The Android Open Source Project
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.android.voicemail.impl;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.telecom.PhoneAccountHandle;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.proguard.UsedByReflection;
import com.android.voicemail.impl.scheduling.BaseTask;
import com.android.voicemail.impl.sms.StatusMessage;
import com.android.voicemail.impl.sms.StatusSmsFetcher;
import com.android.voicemail.impl.sync.VvmAccountManager;
import com.android.voicemail.impl.utils.LoggerUtils;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Task to verify the account status is still correct. This task is only for book keeping so any
 * error is ignored and will not retry. If the provision status sent by the carrier is "ready" the
 * access credentials will be updated (although it is not expected to change without the carrier
 * actively sending out an STATUS SMS which will be handled by {@link
 * com.android.voicemail.impl.sms.OmtpMessageReceiver}). If the provisioning status is not ready an
 * {@link ActivationTask} will be launched to attempt to correct it.
 */
@TargetApi(VERSION_CODES.O)
@UsedByReflection(value = "Tasks.java")
public class StatusCheckTask extends BaseTask {

  public StatusCheckTask() {
    super(TASK_STATUS_CHECK);
  }

  public static void start(Context context, PhoneAccountHandle phoneAccountHandle) {
    Intent intent = BaseTask.createIntent(context, StatusCheckTask.class, phoneAccountHandle);
    context.sendBroadcast(intent);
  }

  @Override
  public void onExecuteInBackgroundThread() {
    TelephonyManager telephonyManager =
        getContext()
            .getSystemService(TelephonyManager.class)
            .createForPhoneAccountHandle(getPhoneAccountHandle());

    if (telephonyManager == null) {
      VvmLog.w(
          "StatusCheckTask.onExecuteInBackgroundThread",
          getPhoneAccountHandle() + " no longer valid");
      return;
    }
    if (telephonyManager.getServiceState().getState() != ServiceState.STATE_IN_SERVICE) {
      VvmLog.i(
          "StatusCheckTask.onExecuteInBackgroundThread",
          getPhoneAccountHandle() + " not in service");
      return;
    }
    OmtpVvmCarrierConfigHelper config =
        new OmtpVvmCarrierConfigHelper(getContext(), getPhoneAccountHandle());
    if (!config.isValid()) {
      VvmLog.e(
          "StatusCheckTask.onExecuteInBackgroundThread",
          "config no longer valid for " + getPhoneAccountHandle());
      VvmAccountManager.removeAccount(getContext(), getPhoneAccountHandle());
      return;
    }

    Bundle data;
    try (StatusSmsFetcher fetcher = new StatusSmsFetcher(getContext(), getPhoneAccountHandle())) {
      config.getProtocol().requestStatus(config, fetcher.getSentIntent());
      // Both the fetcher and OmtpMessageReceiver will be triggered, but
      // OmtpMessageReceiver will just route the SMS back to ActivationTask, which will be
      // rejected because the task is still running.
      data = fetcher.get();
    } catch (TimeoutException e) {
      VvmLog.e("StatusCheckTask.onExecuteInBackgroundThread", "timeout requesting status");
      return;
    } catch (CancellationException e) {
      VvmLog.e("StatusCheckTask.onExecuteInBackgroundThread", "Unable to send status request SMS");
      return;
    } catch (InterruptedException | ExecutionException | IOException e) {
      VvmLog.e("StatusCheckTask.onExecuteInBackgroundThread", "can't get future STATUS SMS", e);
      return;
    }

    StatusMessage message = new StatusMessage(data);
    VvmLog.i(
        "StatusCheckTask.onExecuteInBackgroundThread",
        "STATUS SMS received: st="
            + message.getProvisioningStatus()
            + ", rc="
            + message.getReturnCode());
    if (message.getProvisioningStatus().equals(OmtpConstants.SUBSCRIBER_READY)) {
      VvmLog.i(
          "StatusCheckTask.onExecuteInBackgroundThread",
          "subscriber ready, no activation required");
      LoggerUtils.logImpressionOnMainThread(
          getContext(), DialerImpression.Type.VVM_STATUS_CHECK_READY);
      VvmAccountManager.addAccount(getContext(), getPhoneAccountHandle(), message);
    } else {
      VvmLog.i(
          "StatusCheckTask.onExecuteInBackgroundThread",
          "subscriber not ready, attempting reactivation");
      VvmAccountManager.removeAccount(getContext(), getPhoneAccountHandle());
      LoggerUtils.logImpressionOnMainThread(
          getContext(), DialerImpression.Type.VVM_STATUS_CHECK_REACTIVATION);
      ActivationTask.start(getContext(), getPhoneAccountHandle(), data);
    }
  }
}
