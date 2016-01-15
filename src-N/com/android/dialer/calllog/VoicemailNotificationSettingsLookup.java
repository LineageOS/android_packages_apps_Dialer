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
package com.android.dialer.calllog;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;

import com.android.dialer.calllog.DefaultVoicemailNotifier.NewCall;

/**
 * Class to do lookup for voicemail ringtone and vibration. On N and above, this will look up
 * voicemail notification settings from Telephony.
 */
public class VoicemailNotificationSettingsLookup {
    public static Uri getVoicemailRingtoneUri(Context context, PhoneAccountHandle accountHandle) {
        return getTelephonyManager(context).getVoicemailRingtoneUri(accountHandle);
    }

    public static int getNotificationDefaults(Context context, PhoneAccountHandle accountHandle) {
        return getTelephonyManager(context).isVoicemailVibrationEnabled(accountHandle)
                ? Notification.DEFAULT_VIBRATE : 0;
    }

    private static TelephonyManager getTelephonyManager(Context context) {
        return (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }
}
