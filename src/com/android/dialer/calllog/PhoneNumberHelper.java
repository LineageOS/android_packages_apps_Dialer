/*
 * Copyright (C) 2011 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.dialer.calllog;

import android.content.res.Resources;
import android.provider.CallLog.Calls;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import com.android.dialer.R;

/**
 * Helper for formatting and managing phone numbers.
 */
public class PhoneNumberHelper {
    private final Resources mResources;

    public PhoneNumberHelper(Resources resources) {
        mResources = resources;
    }

    /** Returns true if it is possible to place a call to the given number. */
    public static boolean canPlaceCallsTo(CharSequence number, int presentation) {
        return presentation == Calls.PRESENTATION_ALLOWED
            && !TextUtils.isEmpty(number);
    }

    /** Returns true if it is possible to send an SMS to the given number. */
    public boolean canSendSmsTo(CharSequence number, int presentation) {
        return canPlaceCallsTo(number, presentation)
            && !isVoicemailNumber(number) && !isSipNumber(number);
    }

    /**
     * Returns the string to display for the given phone number.
     *
     * @param number the number to display
     * @param formattedNumber the formatted number if available, may be null
     */
    public CharSequence getDisplayNumber(CharSequence number,
            int presentation, CharSequence formattedNumber) {
        if (presentation == Calls.PRESENTATION_UNKNOWN) {
            return mResources.getString(R.string.unknown);
        }
        if (presentation == Calls.PRESENTATION_RESTRICTED) {
            return mResources.getString(R.string.private_num);
        }
        if (presentation == Calls.PRESENTATION_PAYPHONE) {
            return mResources.getString(R.string.payphone);
        }
        if (TextUtils.isEmpty(number)) {
            return "";
        }
        if (isVoicemailNumber(number)) {
            return mResources.getString(R.string.voicemail);
        }
        if (TextUtils.isEmpty(formattedNumber)) {
            return number;
        } else {
            return formattedNumber;
        }
    }

    /**
     * Returns true if the given number is the number of the configured voicemail.
     * To be able to mock-out this, it is not a static method.
     */
    public boolean isVoicemailNumber(CharSequence number) {
        return PhoneNumberUtils.isVoiceMailNumber(number.toString());
    }

    /**
     * Returns true if the given number is a SIP address.
     * To be able to mock-out this, it is not a static method.
     */
    public boolean isSipNumber(CharSequence number) {
        return PhoneNumberUtils.isUriNumber(number.toString());
    }
}
