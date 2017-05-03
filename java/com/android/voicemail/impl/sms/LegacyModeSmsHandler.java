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

package com.android.voicemail.impl.sms;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import android.telephony.VisualVoicemailSms;
import com.android.voicemail.VoicemailClient;
import com.android.voicemail.impl.OmtpConstants;
import com.android.voicemail.impl.OmtpVvmCarrierConfigHelper;
import com.android.voicemail.impl.VvmLog;

/**
 * Class ot handle voicemail SMS under legacy mode
 *
 * @see OmtpVvmCarrierConfigHelper#isLegacyModeEnabled()
 */
@TargetApi(VERSION_CODES.O)
public class LegacyModeSmsHandler {

  private static final String TAG = "LegacyModeSmsHandler";

  private static final int CALL_VOICEMAIL_REQUEST_CODE = 1;
  private static final int LAUNCH_VOICEMAIL_SETTINGS_REQUEST_CODE = 2;

  public static void handle(Context context, VisualVoicemailSms sms) {
    VvmLog.i(TAG, "processing VVM SMS on legacy mode");
    String eventType = sms.getPrefix();
    Bundle data = sms.getFields();
    PhoneAccountHandle handle = sms.getPhoneAccountHandle();

    if (eventType.equals(OmtpConstants.SYNC_SMS_PREFIX)) {
      SyncMessage message = new SyncMessage(data);
      VvmLog.i(
          TAG, "Received SYNC sms for " + handle + " with event " + message.getSyncTriggerEvent());

      switch (message.getSyncTriggerEvent()) {
        case OmtpConstants.NEW_MESSAGE:
        case OmtpConstants.MAILBOX_UPDATE:
          sendLegacyVoicemailNotification(context, handle, message.getNewMessageCount());

          break;
        default:
          break;
      }
    }
  }

  private static void sendLegacyVoicemailNotification(
      Context context, PhoneAccountHandle phoneAccountHandle, int messageCount) {
    // The user has called into the voicemail and the new message count could
    // change.
    // For some carriers new message count could be set to 0 even if there are still
    // unread messages, to clear the message waiting indicator.

    VvmLog.i(TAG, "sending voicemail notification");
    Intent intent = new Intent(VoicemailClient.ACTION_SHOW_LEGACY_VOICEMAIL);
    intent.setPackage(context.getPackageName());
    intent.putExtra(TelephonyManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
    // Setting voicemail message count to non-zero will show the telephony voicemail
    // notification, and zero will clear it.
    intent.putExtra(TelephonyManager.EXTRA_NOTIFICATION_COUNT, messageCount);

    String voicemailNumber = getVoicemailNumber(context, phoneAccountHandle);
    PendingIntent callVoicemailPendingIntent = null;
    PendingIntent launchVoicemailSettingsPendingIntent = null;

    if (voicemailNumber != null) {
      callVoicemailPendingIntent =
          PendingIntent.getActivity(
              context,
              CALL_VOICEMAIL_REQUEST_CODE,
              new Intent(
                  Intent.ACTION_CALL, Uri.fromParts(PhoneAccount.SCHEME_VOICEMAIL, "", null)),
              PendingIntent.FLAG_UPDATE_CURRENT);
    } else {
      Intent launchVoicemailSettingsIntent =
          new Intent(TelephonyManager.ACTION_CONFIGURE_VOICEMAIL);
      launchVoicemailSettingsIntent.putExtra(TelephonyManager.EXTRA_HIDE_PUBLIC_SETTINGS, true);

      launchVoicemailSettingsPendingIntent =
          PendingIntent.getActivity(
              context,
              LAUNCH_VOICEMAIL_SETTINGS_REQUEST_CODE,
              launchVoicemailSettingsIntent,
              PendingIntent.FLAG_UPDATE_CURRENT);
    }

    intent.putExtra(TelephonyManager.EXTRA_VOICEMAIL_NUMBER, voicemailNumber);
    intent.putExtra(TelephonyManager.EXTRA_CALL_VOICEMAIL_INTENT, callVoicemailPendingIntent);
    intent.putExtra(
        TelephonyManager.EXTRA_LAUNCH_VOICEMAIL_SETTINGS_INTENT,
        launchVoicemailSettingsPendingIntent);

    context.sendBroadcast(intent);
  }

  @Nullable
  private static String getVoicemailNumber(Context context, PhoneAccountHandle phoneAccountHandle) {
    TelephonyManager telephonyManager =
        context
            .getSystemService(TelephonyManager.class)
            .createForPhoneAccountHandle(phoneAccountHandle);
    if (telephonyManager == null) {
      return null;
    }
    return telephonyManager.getVoiceMailNumber();
  }
}
