/*
 * Copyright (C) 2016 Google Inc. All Rights Reserved.
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

package com.android.voicemailomtp.sms;

import android.content.Context;
import android.os.Bundle;
import android.telecom.PhoneAccountHandle;
import android.telephony.VisualVoicemailSms;

import com.android.voicemailomtp.OmtpConstants;
import com.android.voicemailomtp.OmtpVvmCarrierConfigHelper;
import com.android.voicemailomtp.TelephonyManagerStub;
import com.android.voicemailomtp.VvmLog;

/**
 * Class ot handle voicemail SMS under legacy mode
 *
 * @see OmtpVvmCarrierConfigHelper#isLegacyModeEnabled()
 */
public class LegacyModeSmsHandler {

    private static final String TAG = "LegacyModeSmsHandler";

    public static void handle(Context context, VisualVoicemailSms sms) {
        VvmLog.v(TAG, "processing VVM SMS on legacy mode");
        String eventType = sms.getPrefix();
        Bundle data = sms.getFields();
        PhoneAccountHandle handle = sms.getPhoneAccountHandle();

        if (eventType.equals(OmtpConstants.SYNC_SMS_PREFIX)) {
            SyncMessage message = new SyncMessage(data);
            VvmLog.v(TAG, "Received SYNC sms for " + handle +
                    " with event " + message.getSyncTriggerEvent());

            switch (message.getSyncTriggerEvent()) {
                case OmtpConstants.NEW_MESSAGE:
                case OmtpConstants.MAILBOX_UPDATE:
                    // The user has called into the voicemail and the new message count could
                    // change.
                    // For some carriers new message count could be set to 0 even if there are still
                    // unread messages, to clear the message waiting indicator.
                    VvmLog.v(TAG, "updating MWI");

                    // Setting voicemail message count to non-zero will show the telephony voicemail
                    // notification, and zero will clear it.
                    TelephonyManagerStub.showVoicemailNotification(message.getNewMessageCount());
                    break;
                default:
                    break;
            }
        }
    }
}
