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
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.android.contacts.common.util.PhoneNumberHelper;
import com.google.common.collect.Sets;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class PhoneNumberUtilsWrapper {
    private static final Set<String> LEGACY_UNKNOWN_NUMBERS = Sets.newHashSet("-1", "-2", "-3");
    private static final long MAX_VOICEMAIL_CACHE_AGE_IN_MS = 60 * 1000;  // 60 seconds
    private final Context mContext;

    // Keeps a cache of recently-made voicemail queries.  The entire point of this cache is to
    // reduce the number of cross-process requests to TelecomManager.
    // Maps from a phone-account/number pair to a boolean because multiple numbers could return true
    // for the voicemail number if those numbers are not pre-normalized.
    //
    // TODO: Dialer should be fixed so as not to check isVoicemail() so often but at the time of
    // this writing, that was a much larger undertaking than creating this cache.
    private final Map<Pair<PhoneAccountHandle, CharSequence>, Boolean> mVoicemailQueryCache =
            new HashMap<>();
    private long mVoicemailCacheTimestamp = 0;

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
        if (TextUtils.isEmpty(number)) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        // check the age of the voicemail cache first.
        if (currentTime - mVoicemailCacheTimestamp > MAX_VOICEMAIL_CACHE_AGE_IN_MS) {
            mVoicemailQueryCache.clear();

            // We set the timestamp of the voicemail cache to the point where the cache is recreated
            // instead of when an item is added.
            // 1) This is easier to write
            // 2) Ensures that the oldest entry is never older than MAX_VOICEMAIL_CACHE_AGE
            mVoicemailCacheTimestamp = currentTime;
        }

        Pair<PhoneAccountHandle, CharSequence> key = new Pair<>(accountHandle, number);
        if (mVoicemailQueryCache.containsKey(key)) {
            return mVoicemailQueryCache.get(key);
        } else {
            final TelecomManager telecomManager =
                    (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
            Boolean isVoicemail =
                    telecomManager.isVoiceMailNumber(accountHandle, number.toString());
            mVoicemailQueryCache.put(key, isVoicemail);
            return isVoicemail;
        }
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
