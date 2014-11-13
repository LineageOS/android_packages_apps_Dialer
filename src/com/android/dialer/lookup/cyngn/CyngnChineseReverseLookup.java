/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package com.android.dialer.lookup.cyngn;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;
import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.lookup.ContactBuilder;
import com.android.dialer.lookup.ReverseLookup;

public class CyngnChineseReverseLookup extends ReverseLookup {

    private static final String TAG = CyngnChineseReverseLookup.class.getSimpleName();

    private static final int COMMON_CHINESE_PHONE_NUMBER_LENGTH = 11;
    private static final int COMMON_CHINESE_PHONE_NUMBER_AREANO_START = 2;
    private static final int COMMON_CHINESE_PHONE_NUMBER_AREANO_END = 5;

    private static final boolean DEBUG = false;
    private static final Uri PROVIDER_URI =
            Uri.parse("content://com.cyngn.chineselocationlookup.provider");

    public CyngnChineseReverseLookup(Context context) {
    }

    /**
     * Perform phone number lookup.
     *
     * @param context          The application context
     * @param normalizedNumber The normalized phone number
     * @param formattedNumber  The formatted phone number
     * @return The phone number info object
     */
    public ContactInfo lookupNumber(Context context,
            String normalizedNumber, String formattedNumber) {
        String displayName = queryProvider(context, normalizedNumber);
        if (displayName == null) {
            return null;
        }

        if (DEBUG) Log.d(TAG, "Reverse lookup returned name: " + displayName);

        String number = formattedNumber != null
                ? formattedNumber : normalizedNumber;

        ContactBuilder builder = new ContactBuilder(
                ContactBuilder.REVERSE_LOOKUP,
                normalizedNumber, formattedNumber);
        builder.setName(ContactBuilder.Name.createDisplayName(displayName));
        builder.addPhoneNumber(ContactBuilder.PhoneNumber.createMainNumber(number));
        builder.setPhotoUri(ContactBuilder.PHOTO_URI_BUSINESS);

        return builder.build();
    }

    private String queryProvider(Context context, String normalizedNumber) {
        if (normalizedNumber.length() < COMMON_CHINESE_PHONE_NUMBER_LENGTH) {
            return null;
        }

        //trim carrier code, and get area prefix
        String areaPrefix = normalizedNumber.substring(COMMON_CHINESE_PHONE_NUMBER_AREANO_START,
                COMMON_CHINESE_PHONE_NUMBER_AREANO_END);

        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = context.getContentResolver().query(PROVIDER_URI,
                null, null, new String[] { areaPrefix }, null);
        if (cursor == null) {
            return null;
        }

        try {
            if (cursor.moveToFirst()) {
                return cursor.getString(2);
            }
        } finally {
            cursor.close();
        }
        return null;
    }
}
