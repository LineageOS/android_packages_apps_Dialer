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

import android.content.Context;
import android.provider.CallLog;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import com.android.contacts.common.util.PhoneNumberHelper;
import com.android.dialer.util.AgingCache;

import com.google.common.collect.Sets;

import java.util.Set;

/**
 *
 */
public class PhoneNumberUtilsWrapper {
    private static final Set<String> LEGACY_UNKNOWN_NUMBERS = Sets.newHashSet("-1", "-2", "-3");
    private final Context mContext;

    private AgingCache<PhoneAccountHandle, String> mVmNumberCache = new AgingCache<>(5000);

    public PhoneNumberUtilsWrapper(Context context) {
        mContext = context;
    }

    /** Returns true if it is possible to place a call to the given number. */
    public static boolean canPlaceCallsTo(CharSequence number, int presentation) {
        return presentation == CallLog.Calls.PRESENTATION_ALLOWED
            && !TextUtils.isEmpty(number) && !isLegacyUnknownNumbers(number);
    }

    /**
     * Returns true if the given number is the number of the configured voicemail. To be able to
     * mock-out this, it is not a static method.
     */
    public boolean isVoicemailNumber(PhoneAccountHandle accountHandle, CharSequence number) {
        if (number == null) {
            return false;
        }

        String vmNumber = mVmNumberCache.get(accountHandle);
        if (vmNumber == null) {
            final TelecomManager telecomManager =
                    (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
            vmNumber = telecomManager.getVoiceMailNumber(accountHandle);
            mVmNumberCache.put(accountHandle, vmNumber != null ? vmNumber : "");
        }

        final String actualVmNumber = PhoneNumberUtils.extractNetworkPortionAlt(vmNumber);
        return !TextUtils.isEmpty(actualVmNumber)
                && PhoneNumberUtils.compare(number.toString(), actualVmNumber);
    }

    /**
     * Returns true if the given number is a SIP address. To be able to mock-out this, it is not a
     * static method.
     */
    public static boolean isSipNumber(CharSequence number) {
        return number != null && PhoneNumberHelper.isUriNumber(number.toString());
    }

    public boolean isUnknownNumberThatCanBeLookedUp(PhoneAccountHandle accountHandle,
            CharSequence number, int presentation) {
        if (presentation == CallLog.Calls.PRESENTATION_UNKNOWN) {
            return false;
        }
        if (presentation == CallLog.Calls.PRESENTATION_RESTRICTED) {
            return false;
        }
        if (presentation == CallLog.Calls.PRESENTATION_PAYPHONE) {
            return false;
        }
        if (TextUtils.isEmpty(number)) {
            return false;
        }
        if (isVoicemailNumber(accountHandle, number)) {
            return false;
        }
        if (isLegacyUnknownNumbers(number)) {
            return false;
        }
        return true;
    }

    public static boolean isLegacyUnknownNumbers(CharSequence number) {
        return number != null && LEGACY_UNKNOWN_NUMBERS.contains(number.toString());
    }
}
