/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.dialer.database;

import android.database.MatrixCursor;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.TextUtils;

import com.android.dialer.database.DialerDatabaseHelper.ContactNumber;

public class DatabaseTestUtils {
    public static MatrixCursor constructNewNameCursor() {
        final MatrixCursor cursor = new MatrixCursor(new String[]{
                DialerDatabaseHelper.SmartDialDbColumns.DISPLAY_NAME_PRIMARY,
                DialerDatabaseHelper.SmartDialDbColumns.CONTACT_ID});
        return cursor;
    }

    public static MatrixCursor constructNewContactCursor() {
        final MatrixCursor cursor = new MatrixCursor(new String[]{
                    Phone._ID,                          // 0
                    Phone.TYPE,                         // 1
                    Phone.LABEL,                        // 2
                    Phone.NUMBER,                       // 3
                    Phone.CONTACT_ID,                   // 4
                    Phone.LOOKUP_KEY,                   // 5
                    Phone.DISPLAY_NAME_PRIMARY,         // 6
                    Phone.PHOTO_ID,                     // 7
                    Data.LAST_TIME_USED,                // 8
                    Data.TIMES_USED,                    // 9
                    Contacts.STARRED,                   // 10
                    Data.IS_SUPER_PRIMARY,              // 11
                    Contacts.IN_VISIBLE_GROUP,          // 12
                    Data.IS_PRIMARY,                    // 13
                    Data.CARRIER_PRESENCE});            // 14
        return cursor;
    }

    public static ContactNumber constructNewContactWithDummyIds(MatrixCursor contactCursor,
            MatrixCursor nameCursor, String number, int id, String displayName) {
        return constructNewContact(contactCursor, nameCursor, id, number, id, String.valueOf(id),
                displayName, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public static ContactNumber constructNewContact(MatrixCursor contactCursor,
            MatrixCursor nameCursor, int id, String number, int contactId, String lookupKey,
            String displayName, int photoId, int lastTimeUsed, int timesUsed, int starred,
            int isSuperPrimary, int inVisibleGroup, int isPrimary, int carrierPresence) {
        if (contactCursor == null || nameCursor == null) {
            throw new IllegalArgumentException("Provided MatrixCursors cannot be null");
        }

        if (TextUtils.isEmpty(number)) {
            // Add a dummy number, otherwise DialerDatabaseHelper simply ignores the entire
            // row if the number is empty
            number = "0";
        }

        contactCursor.addRow(new Object[]{id, "", "", number, contactId, lookupKey, displayName,
                photoId, lastTimeUsed, timesUsed, starred, isSuperPrimary, inVisibleGroup,
                isPrimary, carrierPresence});
        nameCursor.addRow(new Object[]{displayName, contactId});

        return new ContactNumber(contactId, id, displayName, number, lookupKey, 0, 0);
    }
}
