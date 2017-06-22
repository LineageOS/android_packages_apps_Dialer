/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.UserManager;
import android.telecom.PhoneAccountHandle;
import android.telephony.VisualVoicemailSms;
import com.android.voicemail.impl.ActivationTask;
import com.android.voicemail.impl.OmtpConstants;
import com.android.voicemail.impl.OmtpService;
import com.android.voicemail.impl.OmtpVvmCarrierConfigHelper;
import com.android.voicemail.impl.Voicemail;
import com.android.voicemail.impl.Voicemail.Builder;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.protocol.VisualVoicemailProtocol;
import com.android.voicemail.impl.settings.VisualVoicemailSettingsUtil;
import com.android.voicemail.impl.sync.OmtpVvmSyncService;
import com.android.voicemail.impl.sync.SyncOneTask;
import com.android.voicemail.impl.sync.SyncTask;
import com.android.voicemail.impl.sync.VoicemailsQueryHelper;
import com.android.voicemail.impl.utils.VoicemailDatabaseUtil;

/** Receive SMS messages and send for processing by the OMTP visual voicemail source. */
@TargetApi(VERSION_CODES.O)
public class OmtpMessageReceiver extends BroadcastReceiver {

  private static final String TAG = "OmtpMessageReceiver";

  private Context mContext;

  @Override
  public void onReceive(Context context, Intent intent) {
    mContext = context;
    VisualVoicemailSms sms = intent.getExtras().getParcelable(OmtpService.EXTRA_VOICEMAIL_SMS);
    PhoneAccountHandle phone = sms.getPhoneAccountHandle();

    if (phone == null) {
      // This should never happen
      VvmLog.i(TAG, "Received message for null phone account");
      return;
    }

    if (!context.getSystemService(UserManager.class).isUserUnlocked()) {
      VvmLog.i(TAG, "Received message on locked device");
      // LegacyModeSmsHandler can handle new message notifications without storage access
      LegacyModeSmsHandler.handle(context, sms);
      // A full sync will happen after the device is unlocked, so nothing else need to be
      // done.
      return;
    }

    OmtpVvmCarrierConfigHelper helper = new OmtpVvmCarrierConfigHelper(mContext, phone);
    if (!helper.isValid()) {
      VvmLog.e(TAG, "vvm config no longer valid");
      return;
    }
    if (!VisualVoicemailSettingsUtil.isEnabled(mContext, phone)) {
      if (helper.isLegacyModeEnabled()) {
        LegacyModeSmsHandler.handle(context, sms);
      } else {
        VvmLog.i(TAG, "Received vvm message for disabled vvm source.");
      }
      return;
    }

    String eventType = sms.getPrefix();
    Bundle data = sms.getFields();

    if (eventType == null || data == null) {
      VvmLog.e(TAG, "Unparsable VVM SMS received, ignoring");
      return;
    }

    if (eventType.equals(OmtpConstants.SYNC_SMS_PREFIX)) {
      SyncMessage message = new SyncMessage(data);

      VvmLog.v(
          TAG, "Received SYNC sms for " + phone + " with event " + message.getSyncTriggerEvent());
      processSync(phone, message);
    } else if (eventType.equals(OmtpConstants.STATUS_SMS_PREFIX)) {
      VvmLog.v(TAG, "Received Status sms for " + phone);
      // If the STATUS SMS is initiated by ActivationTask the TaskSchedulerService will reject
      // the follow request. Providing the data will also prevent ActivationTask from
      // requesting another STATUS SMS. The following task will only run if the carrier
      // spontaneous send a STATUS SMS, in that case, the VVM service should be reactivated.
      ActivationTask.start(context, phone, data);
    } else {
      VvmLog.w(TAG, "Unknown prefix: " + eventType);
      VisualVoicemailProtocol protocol = helper.getProtocol();
      if (protocol == null) {
        return;
      }
      Bundle statusData = helper.getProtocol().translateStatusSmsBundle(helper, eventType, data);
      if (statusData != null) {
        VvmLog.i(TAG, "Protocol recognized the SMS as STATUS, activating");
        ActivationTask.start(context, phone, data);
      }
    }
  }

  /**
   * A sync message has two purposes: to signal a new voicemail message, and to indicate the
   * voicemails on the server have changed remotely (usually through the TUI). Save the new message
   * to the voicemail provider if it is the former case and perform a full sync in the latter case.
   *
   * @param message The sync message to extract data from.
   */
  private void processSync(PhoneAccountHandle phone, SyncMessage message) {
    switch (message.getSyncTriggerEvent()) {
      case OmtpConstants.NEW_MESSAGE:
        if (!OmtpConstants.VOICE.equals(message.getContentType())) {
          VvmLog.i(
              TAG,
              "Non-voice message of type '" + message.getContentType() + "' received, ignoring");
          return;
        }

        Builder builder =
            Voicemail.createForInsertion(message.getTimestampMillis(), message.getSender())
                .setPhoneAccount(phone)
                .setSourceData(message.getId())
                .setDuration(message.getLength())
                .setSourcePackage(mContext.getPackageName());
        Voicemail voicemail = builder.build();

        VoicemailsQueryHelper queryHelper = new VoicemailsQueryHelper(mContext);
        if (queryHelper.isVoicemailUnique(voicemail)) {
          Uri uri = VoicemailDatabaseUtil.insert(mContext, voicemail);
          voicemail = builder.setId(ContentUris.parseId(uri)).setUri(uri).build();
          SyncOneTask.start(mContext, phone, voicemail);
        }
        break;
      case OmtpConstants.MAILBOX_UPDATE:
        SyncTask.start(mContext, phone, OmtpVvmSyncService.SYNC_DOWNLOAD_ONLY);
        break;
      case OmtpConstants.GREETINGS_UPDATE:
        // Not implemented in V1
        break;
      default:
        VvmLog.e(TAG, "Unrecognized sync trigger event: " + message.getSyncTriggerEvent());
        break;
    }
  }
}
