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
import android.util.Pair;
import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.lookup.ContactBuilder;
import com.android.dialer.lookup.ReverseLookup;

public class CyngnChineseReverseLookup extends ReverseLookup {

    private static final String TAG = CyngnChineseReverseLookup.class.getSimpleName();

    private static final int COMMON_CHINESE_PHONE_NUMBER_LENGTH = 11;
    private static final int COMMON_CHINESE_PHONE_NUMBER_AREANO_START = 2;
    private static final int COMMON_CHINESE_PHONE_NUMBER_AREANO_END = 5;

    private static final boolean DEBUG = false;

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
    public Pair<ContactInfo, Object> lookupNumber(Context context,
                                                  String normalizedNumber, String formattedNumber) {
        String displayName;

        displayName = queryProvider(context, normalizedNumber);

        if (displayName == null) {
            return null;
        }

        if (DEBUG) Log.d(TAG, "Reverse lookup returned name: " + displayName);

        String number = formattedNumber != null
                ? formattedNumber : normalizedNumber;

        ContactBuilder builder = new ContactBuilder(
                ContactBuilder.REVERSE_LOOKUP,
                normalizedNumber, formattedNumber);

        ContactBuilder.Name n = new ContactBuilder.Name();
        n.displayName = displayName;
        builder.setName(n);

        ContactBuilder.PhoneNumber pn = new ContactBuilder.PhoneNumber();
        pn.number = number;
        pn.type = ContactsContract.CommonDataKinds.Phone.TYPE_MAIN;
        builder.addPhoneNumber(pn);

        builder.setPhotoUri(ContactBuilder.PHOTO_URI_BUSINESS);

        return Pair.create(builder.build(), null);
    }

    private String queryProvider(Context context, String normalizedNumber) {
        if (normalizedNumber.length() < COMMON_CHINESE_PHONE_NUMBER_LENGTH) {
            return null;
        }

        //trim carrier code, and get area prefix
        String areaPrefix = normalizedNumber.substring(COMMON_CHINESE_PHONE_NUMBER_AREANO_START,
                COMMON_CHINESE_PHONE_NUMBER_AREANO_END);

        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = resolver.query(Uri.parse("content://com.cyngn.chineselocationlookup.provider"),
                null, null, new String[] { areaPrefix }, null);

        String city = null;

        try {
            if (cursor.moveToFirst()) {
                city = cursor.getString(2);
            }
        } catch (NullPointerException e) {
            return null;
        } finally {
            cursor.close();
            return city;
        }
    }
}
