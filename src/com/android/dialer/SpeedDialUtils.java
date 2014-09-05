/*
 * Copyright (C) 2013, The Linux Foundation. All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are
 met:
         * Redistributions of source code must retain the above copyright
           notice, this list of conditions and the following disclaimer.
         * Redistributions in binary form must reproduce the above
           copyright notice, this list of conditions and the following
           disclaimer in the documentation and/or other materials provided
           with the distribution.
         * Neither the name of The Linux Foundation nor the names of its
           contributors may be used to endorse or promote products derived
           from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.dialer;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.Locale;

public class SpeedDialUtils {
    private static final String NUMBER_KEY_PATTERN = "num%d_key";
    private static final String CONTACT_KEY_PATTERN = "contact%d_key";

    public static class SpeedDialRecord {
        public String number;
        public long contactId;

        public SpeedDialRecord(String number, long contactId) {
            this.number = number;
            this.contactId = contactId;
        }
    }

    private Context mContext;
    private SharedPreferences mPrefs;

    public SpeedDialUtils(Context context) {
        mContext = context;
        mPrefs = mContext.getSharedPreferences("speedDial_Num", context.MODE_PRIVATE);
    }

    public void saveRecord(int number, SpeedDialRecord record) {
        if (number < 2 || number > 9) {
            return;
        }
        SharedPreferences.Editor editor = mPrefs.edit();
        String numberKey = String.format(Locale.US, NUMBER_KEY_PATTERN, number);
        String contactKey = String.format(Locale.US, CONTACT_KEY_PATTERN, number);
        if (record == null) {
            editor.remove(numberKey).remove(contactKey);
        } else {
            editor.putString(numberKey, record.number);
            editor.putLong(contactKey, record.contactId);
        }
        editor.commit();
    }

    public SpeedDialRecord getRecord(int number) {
        if (number < 2 || number > 9) {
            return null;
        }
        String numberKey = String.format(Locale.US, NUMBER_KEY_PATTERN, number);
        String contactKey = String.format(Locale.US, CONTACT_KEY_PATTERN, number);
        String phoneNumber = mPrefs.getString(numberKey, null);
        if (TextUtils.isEmpty(phoneNumber)) {
            return null;
        }
        return new SpeedDialRecord(phoneNumber, mPrefs.getLong(contactKey, -1));
    }
}
