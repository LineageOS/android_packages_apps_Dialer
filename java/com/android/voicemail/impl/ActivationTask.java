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

package com.android.voicemail.impl;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.telecom.PhoneAccountHandle;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.proguard.UsedByReflection;
import com.android.voicemail.VoicemailClient;
import com.android.voicemail.impl.protocol.VisualVoicemailProtocol;
import com.android.voicemail.impl.scheduling.BaseTask;
import com.android.voicemail.impl.scheduling.RetryPolicy;
import com.android.voicemail.impl.settings.VisualVoicemailSettingsUtil;
import com.android.voicemail.impl.sms.StatusMessage;
import com.android.voicemail.impl.sms.StatusSmsFetcher;
import com.android.voicemail.impl.sync.OmtpVvmSyncService;
import com.android.voicemail.impl.sync.SyncTask;
import com.android.voicemail.impl.sync.VvmAccountManager;
import com.android.voicemail.impl.utils.LoggerUtils;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Task to activate the visual voicemail service. A request to activate VVM will be sent to the
 * carrier, which will respond with a STATUS SMS. The credentials will be updated from the SMS. If
 * the user is not provisioned provisioning will be attempted. Activation happens when the phone
 * boots, the SIM is inserted, signal returned when VVM is not activated yet, and when the carrier
 * spontaneously sent a STATUS SMS.
 */
@TargetApi(VERSION_CODES.O)
@UsedByReflection(value = "Tasks.java")
public class ActivationTask extends BaseTask {

  private static final String TAG = "VvmActivationTask";

  private static final int RETRY_TIMES = 4;
  private static final int RETRY_INTERVAL_MILLIS = 5_000;

  private static final String EXTRA_MESSAGE_DATA_BUNDLE = "extra_message_data_bundle";

  private final RetryPolicy mRetryPolicy;

  private Bundle mMessageData;

  public ActivationTask() {
    super(TASK_ACTIVATION);
    mRetryPolicy = new RetryPolicy(RETRY_TIMES, RETRY_INTERVAL_MILLIS);
    addPolicy(mRetryPolicy);
  }

  /** Has the user gone through the setup wizard yet. */
  private static boolean isDeviceProvisioned(Context context) {
    return Settings.Global.getInt(
            context.getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 0)
        == 1;
  }

  /**
   * @param messageData The optional bundle from {@link android.provider.VoicemailContract#
   *     EXTRA_VOICEMAIL_SMS_FIELDS}, if the task is initiated by a status SMS. If null the task
   *     will request a status SMS itself.
   */
  public static void start(
      Context context, PhoneAccountHandle phoneAccountHandle, @Nullable Bundle messageData) {
    if (!isDeviceProvisioned(context)) {
      VvmLog.i(TAG, "Activation requested while device is not provisioned, postponing");
      // Activation might need information such as system language to be set, so wait until
      // the setup wizard is finished. The data bundle from the SMS will be re-requested upon
      // activation.
      DeviceProvisionedJobService.activateAfterProvisioned(context, phoneAccountHandle);
      return;
    }

    Intent intent = BaseTask.createIntent(context, ActivationTask.class, phoneAccountHandle);
    if (messageData != null) {
      intent.putExtra(EXTRA_MESSAGE_DATA_BUNDLE, messageData);
    }
    context.sendBroadcast(intent);
  }

  @Override
  public void onCreate(Context context, Bundle extras) {
    super.onCreate(context, extras);
    mMessageData = extras.getParcelable(EXTRA_MESSAGE_DATA_BUNDLE);
  }

  @Override
  public Intent createRestartIntent() {
    LoggerUtils.logImpressionOnMainThread(
        getContext(), DialerImpression.Type.VVM_AUTO_RETRY_ACTIVATION);
    Intent intent = super.createRestartIntent();
    // mMessageData is discarded, request a fresh STATUS SMS for retries.
    return intent;
  }

  @Override
  @WorkerThread
  public void onExecuteInBackgroundThread() {
    Assert.isNotMainThread();
    LoggerUtils.logImpressionOnMainThread(
        getContext(), DialerImpression.Type.VVM_ACTIVATION_STARTED);
    PhoneAccountHandle phoneAccountHandle = getPhoneAccountHandle();
    if (phoneAccountHandle == null) {
      // This should never happen
      VvmLog.e(TAG, "null PhoneAccountHandle");
      return;
    }

    PreOMigrationHandler.migrate(getContext(), phoneAccountHandle);

    if (!VisualVoicemailSettingsUtil.isEnabled(getContext(), phoneAccountHandle)) {
      VvmLog.i(TAG, "VVM is disabled");
      return;
    }

    OmtpVvmCarrierConfigHelper helper =
        new OmtpVvmCarrierConfigHelper(getContext(), phoneAccountHandle);
    if (!helper.isValid()) {
      VvmLog.i(TAG, "VVM not supported on phoneAccountHandle " + phoneAccountHandle);
      VvmAccountManager.removeAccount(getContext(), phoneAccountHandle);
      return;
    }

    // OmtpVvmCarrierConfigHelper can start the activation process; it will pass in a vvm
    // content provider URI which we will use.  On some occasions, setting that URI will
    // fail, so we will perform a few attempts to ensure that the vvm content provider has
    // a good chance of being started up.
    if (!VoicemailStatus.edit(getContext(), phoneAccountHandle)
        .setType(helper.getVvmType())
        .apply()) {
      VvmLog.e(TAG, "Failed to configure content provider - " + helper.getVvmType());
      fail();
    }
    VvmLog.i(TAG, "VVM content provider configured - " + helper.getVvmType());

    if (VvmAccountManager.isAccountActivated(getContext(), phoneAccountHandle)) {
      VvmLog.i(TAG, "Account is already activated");
      onSuccess(getContext(), phoneAccountHandle);
      return;
    }
    helper.handleEvent(
        VoicemailStatus.edit(getContext(), phoneAccountHandle), OmtpEvents.CONFIG_ACTIVATING);

    if (!hasSignal(getContext(), phoneAccountHandle)) {
      VvmLog.i(TAG, "Service lost during activation, aborting");
      // Restore the "NO SIGNAL" state since it will be overwritten by the CONFIG_ACTIVATING
      // event.
      helper.handleEvent(
          VoicemailStatus.edit(getContext(), phoneAccountHandle),
          OmtpEvents.NOTIFICATION_SERVICE_LOST);
      // Don't retry, a new activation will be started after the signal returned.
      return;
    }

    helper.activateSmsFilter();
    VoicemailStatus.Editor status = mRetryPolicy.getVoicemailStatusEditor();

    VisualVoicemailProtocol protocol = helper.getProtocol();

    Bundle data;
    if (mMessageData != null) {
      // The content of STATUS SMS is provided to launch this task, no need to request it
      // again.
      data = mMessageData;
    } else {
      try (StatusSmsFetcher fetcher = new StatusSmsFetcher(getContext(), phoneAccountHandle)) {
        protocol.startActivation(helper, fetcher.getSentIntent());
        // Both the fetcher and OmtpMessageReceiver will be triggered, but
        // OmtpMessageReceiver will just route the SMS back to ActivationTask, which will be
        // rejected because the task is still running.
        data = fetcher.get();
      } catch (TimeoutException e) {
        // The carrier is expected to return an STATUS SMS within STATUS_SMS_TIMEOUT_MILLIS
        // handleEvent() will do the logging.
        helper.handleEvent(status, OmtpEvents.CONFIG_STATUS_SMS_TIME_OUT);
        fail();
        return;
      } catch (CancellationException e) {
        VvmLog.e(TAG, "Unable to send status request SMS");
        fail();
        return;
      } catch (InterruptedException | ExecutionException | IOException e) {
        VvmLog.e(TAG, "can't get future STATUS SMS", e);
        fail();
        return;
      }
    }

    StatusMessage message = new StatusMessage(data);
    VvmLog.d(
        TAG,
        "STATUS SMS received: st="
            + message.getProvisioningStatus()
            + ", rc="
            + message.getReturnCode());
    if (message.getProvisioningStatus().equals(OmtpConstants.SUBSCRIBER_READY)) {
      VvmLog.d(TAG, "subscriber ready, no activation required");
      updateSource(getContext(), phoneAccountHandle, message);
    } else {
      if (helper.supportsProvisioning()) {
        VvmLog.i(TAG, "Subscriber not ready, start provisioning");
        helper.startProvisioning(this, phoneAccountHandle, status, message, data);

      } else if (message.getProvisioningStatus().equals(OmtpConstants.SUBSCRIBER_NEW)) {
        VvmLog.i(TAG, "Subscriber new but provisioning is not supported");
        // Ignore the non-ready state and attempt to use the provided info as is.
        // This is probably caused by not completing the new user tutorial.
        updateSource(getContext(), phoneAccountHandle, message);
      } else {
        VvmLog.i(TAG, "Subscriber not ready but provisioning is not supported");
        helper.handleEvent(status, OmtpEvents.CONFIG_SERVICE_NOT_AVAILABLE);
      }
    }
    LoggerUtils.logImpressionOnMainThread(
        getContext(), DialerImpression.Type.VVM_ACTIVATION_COMPLETED);
  }

  private static void updateSource(
      Context context, PhoneAccountHandle phone, StatusMessage message) {

    if (OmtpConstants.SUCCESS.equals(message.getReturnCode())) {
      // Save the IMAP credentials in preferences so they are persistent and can be retrieved.
      VvmAccountManager.addAccount(context, phone, message);
      onSuccess(context, phone);
    } else {
      VvmLog.e(TAG, "Visual voicemail not available for subscriber.");
    }
  }

  private static void onSuccess(Context context, PhoneAccountHandle phoneAccountHandle) {
    OmtpVvmCarrierConfigHelper helper = new OmtpVvmCarrierConfigHelper(context, phoneAccountHandle);
    helper.handleEvent(
        VoicemailStatus.edit(context, phoneAccountHandle),
        OmtpEvents.CONFIG_REQUEST_STATUS_SUCCESS);
    clearLegacyVoicemailNotification(context, phoneAccountHandle);
    SyncTask.start(context, phoneAccountHandle, OmtpVvmSyncService.SYNC_FULL_SYNC);
  }

  /** Sends a broadcast to the dialer UI to clear legacy voicemail notifications if any. */
  private static void clearLegacyVoicemailNotification(
      Context context, PhoneAccountHandle phoneAccountHandle) {
    Intent intent = new Intent(VoicemailClient.ACTION_SHOW_LEGACY_VOICEMAIL);
    intent.setPackage(context.getPackageName());
    intent.putExtra(TelephonyManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
    // Setting voicemail message count to zero will clear the notification.
    intent.putExtra(TelephonyManager.EXTRA_NOTIFICATION_COUNT, 0);
    context.sendBroadcast(intent);
  }

  private static boolean hasSignal(Context context, PhoneAccountHandle phoneAccountHandle) {
    TelephonyManager telephonyManager =
        context
            .getSystemService(TelephonyManager.class)
            .createForPhoneAccountHandle(phoneAccountHandle);
    return telephonyManager.getServiceState().getState() == ServiceState.STATE_IN_SERVICE;
  }
}
