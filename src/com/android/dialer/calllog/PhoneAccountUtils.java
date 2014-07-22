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
import android.telecomm.PhoneAccount;
import android.telecomm.PhoneAccountHandle;
import android.telecomm.TelecommManager;
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
        final PhoneAccount accountMetadata = TelecommManager.from(context)
                .getPhoneAccount(phoneAccount);
        if (accountMetadata == null) {
            return null;
        }
        return accountMetadata.getIcon(context);
    }
}
