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

package com.android.voicemailomtp;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.os.PersistableBundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import java.lang.reflect.Method;

/**
 * Temporary stub for public APIs that should be added into telephony manager.
 *
 * <p>TODO(b/32637799) remove this.
 */
@TargetApi(VERSION_CODES.CUR_DEVELOPMENT)
public class TelephonyManagerStub {

    private static final String TAG = "TelephonyManagerStub";

    public static void showVoicemailNotification(int voicemailCount) {

    }

    /**
     * Dismisses the message waiting (voicemail) indicator.
     *
     * @param subId the subscription id we should dismiss the notification for.
     */
    public static void clearMwiIndicator(int subId) {

    }

    public static void setShouldCheckVisualVoicemailConfigurationForMwi(int subId,
            boolean enabled) {

    }

    public static int getSubIdForPhoneAccount(Context context, PhoneAccount phoneAccount) {
        // Hidden
        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        try {
            Method method = TelephonyManager.class
                    .getMethod("getSubIdForPhoneAccount", PhoneAccount.class);
            return (int) method.invoke(telephonyManager, phoneAccount);
        } catch (Exception e) {
            VvmLog.e(TAG, "reflection call to getSubIdForPhoneAccount failed:", e);
        }
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    public static String getNetworkSpecifierForPhoneAccountHandle(Context context,
            PhoneAccountHandle phoneAccountHandle) {
        return String.valueOf(SubscriptionManager.getDefaultDataSubscriptionId());
    }

    public static PersistableBundle getCarrirConfigForPhoneAccountHandle(Context context,
            PhoneAccountHandle phoneAccountHandle) {
        return context.getSystemService(CarrierConfigManager.class).getConfig();
    }
}
