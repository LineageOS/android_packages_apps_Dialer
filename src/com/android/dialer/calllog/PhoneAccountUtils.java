/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.content.ComponentName;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;

/**
 * Methods to help extract {@code PhoneAccount} information from database and Telecomm sources
 */
public class PhoneAccountUtils {
    /**
     * Generate account info from data in Telecomm database
     */
    public static PhoneAccountHandle getAccount(String componentString,
            String accountId) {
        if (TextUtils.isEmpty(componentString) || TextUtils.isEmpty(accountId)) {
            return null;
        }
        final ComponentName componentName = ComponentName.unflattenFromString(componentString);
        return new PhoneAccountHandle(componentName, accountId);
    }

    /**
     * Generate account icon from data in Telecomm database
     */
    public static Drawable getAccountIcon(Context context, PhoneAccountHandle phoneAccount) {
        final PhoneAccount account = getAccountOrNull(context, phoneAccount);
        if (account == null) {
            return null;
        }
        return account.getIcon(context);
    }

    /**
     * Generate account label from data in Telecomm database
     */
    public static String getAccountLabel(Context context, PhoneAccountHandle phoneAccount) {
        final PhoneAccount account = getAccountOrNull(context, phoneAccount);
        if (account == null) {
            return null;
        }
        return account.getLabel().toString();
    }

    /**
     * Retrieve the account metadata, but if the account does not exist or the device has only a
     * single registered and enabled account, return null.
     */
    private static PhoneAccount getAccountOrNull(Context context,
            PhoneAccountHandle phoneAccount) {
        final TelecomManager telecomManager =
                (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        final PhoneAccount account = telecomManager.getPhoneAccount(phoneAccount);
        if (account == null || !telecomManager.hasMultipleCallCapableAccounts()) {
            return null;
        }
        return account;
    }

}
