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

package com.android.dialer.calllog.calllogcache;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;

/**
 * Modified version of {@link com.android.dialer.calllog.calllogcache.CallLogCache} to be used in
 * tests that allows injecting the voicemail number.
 *
 * NOTE: This tests the pre-LMR1 version because currently none of the tests involve multi-SIM,
 * but...
 * TODO: write tests to test multi-SIM functionality in TelecomCallLogCache.
 */
public class TestTelecomCallLogCache extends CallLogCache {
    private CharSequence mVoicemailNumber;
    private String mAccountLabel;

    public TestTelecomCallLogCache(Context context, CharSequence voicemailNumber,
            String accountLabel) {
        super(context);
        mVoicemailNumber = voicemailNumber;
        mAccountLabel = accountLabel;
    }

    @Override
    public boolean isVoicemailNumber(PhoneAccountHandle accountHandle, CharSequence number) {
        return mVoicemailNumber.equals(number);
    }

    @Override
    public String getAccountLabel(PhoneAccountHandle accountHandle) {
        return mAccountLabel;
    }

    public void setAccountLabel(String accountLabel) {
        mAccountLabel = accountLabel;
    }

    @Override
    public int getAccountColor(PhoneAccountHandle accountHandle) {
        return PhoneAccount.NO_HIGHLIGHT_COLOR;
    }

    @Override
    public boolean doesAccountSupportCallSubject(PhoneAccountHandle accountHandle) {
        return false;
    }

    @Override
    public Drawable getAccountIcon(PhoneAccountHandle accountHandle) {
        return null;
    }
}
