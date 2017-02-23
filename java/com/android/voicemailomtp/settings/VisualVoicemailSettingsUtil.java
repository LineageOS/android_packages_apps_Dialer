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
package com.android.voicemailomtp.settings;

import android.content.Context;
import android.telecom.PhoneAccountHandle;

import com.android.voicemailomtp.OmtpVvmCarrierConfigHelper;
import com.android.voicemailomtp.VisualVoicemailPreferences;
import com.android.voicemailomtp.sync.OmtpVvmSourceManager;

/**
 * Save whether or not a particular account is enabled in shared to be retrieved later.
 */
public class VisualVoicemailSettingsUtil {

    private static final String IS_ENABLED_KEY = "is_enabled";


    public static void setEnabled(Context context, PhoneAccountHandle phoneAccount,
            boolean isEnabled) {
        new VisualVoicemailPreferences(context, phoneAccount).edit()
                .putBoolean(IS_ENABLED_KEY, isEnabled)
                .apply();
        OmtpVvmCarrierConfigHelper config = new OmtpVvmCarrierConfigHelper(context, phoneAccount);
        if (isEnabled) {
            OmtpVvmSourceManager.getInstance(context).addPhoneStateListener(phoneAccount);
            config.startActivation();
        } else {
            OmtpVvmSourceManager.getInstance(context).removeSource(phoneAccount);
            config.startDeactivation();
        }
    }

    public static boolean isEnabled(Context context,
            PhoneAccountHandle phoneAccount) {
        if (phoneAccount == null) {
            return false;
        }

        VisualVoicemailPreferences prefs = new VisualVoicemailPreferences(context, phoneAccount);
        if (prefs.contains(IS_ENABLED_KEY)) {
            // isEnableByDefault is a bit expensive, so don't use it as default value of
            // getBoolean(). The "false" here should never be actually used.
            return prefs.getBoolean(IS_ENABLED_KEY, false);
        }
        return new OmtpVvmCarrierConfigHelper(context, phoneAccount).isEnabledByDefault();
    }

    /**
     * Whether the client enabled status is explicitly set by user or by default(Whether carrier VVM
     * app is installed). This is used to determine whether to disable the client when the carrier
     * VVM app is installed. If the carrier VVM app is installed the client should give priority to
     * it if the settings are not touched.
     */
    public static boolean isEnabledUserSet(Context context,
            PhoneAccountHandle phoneAccount) {
        if (phoneAccount == null) {
            return false;
        }
        VisualVoicemailPreferences prefs = new VisualVoicemailPreferences(context, phoneAccount);
        return prefs.contains(IS_ENABLED_KEY);
    }
}
