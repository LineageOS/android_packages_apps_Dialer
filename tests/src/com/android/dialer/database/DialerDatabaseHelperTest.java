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
 * limitations under the License.
 */

package com.android.dialer.database;

import static com.android.dialer.database.DatabaseTestUtils.*;

import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.test.suitebuilder.annotation.SmallTest;
import android.test.suitebuilder.annotation.Suppress;
import android.test.AndroidTestCase;

import com.android.dialer.database.DialerDatabaseHelper;
import com.android.dialer.database.DialerDatabaseHelper.ContactNumber;
import com.android.dialer.dialpad.SmartDialNameMatcher;
import com.android.dialer.dialpad.SmartDialPrefix;

import java.lang.Exception;
import java.lang.Override;
import java.util.ArrayList;

/**
 * Validates the behavior of the smart dial database helper with regards to contact updates and
 * deletes.
 * To run this test, use the command:
 * adb shell am instrument -w -e class com.android.dialer.database.DialerDatabaseHelperTest /
 * com.android.dialer.tests/android.test.InstrumentationTestRunner
 */
@SmallTest
public class DialerDatabaseHelperTest extends AndroidTestCase {

    private DialerDatabaseHelper mTestHelper;
    private SQLiteDatabase mDb;

    @Override
    protected void setUp() {
        mTestHelper = DialerDatabaseHelper.getNewInstanceForTest(getContext());
        mDb = mTestHelper.getWritableDatabase();
    }

    @Override
    protected void tearDown() throws Exception {
        final SQLiteDatabase db = mTestHelper.getWritableDatabase();
        mTestHelper.removeAllContacts(db);
        super.tearDown();
    }

    /**
     * Verifies that a new contact added into the database is a match after the update.
     */
    public void testForNewContacts() {
        final MatrixCursor nameCursor =  constructNewNameCursor();
        final MatrixCursor contactCursor = constructNewContactCursor();

        mTestHelper.insertUpdatedContactsAndNumberPrefix(mDb, contactCursor, 0L);
        mTestHelper.insertNamePrefixes(mDb, nameCursor);
        assertEquals(0, getMatchesFromDb("5105272357").size());

        // Insert new contact
        constructNewContactWithDummyIds(contactCursor, nameCursor,
                "510-527-2357", 0,  "James");
        mTestHelper.insertUpdatedContactsAndNumberPrefix(mDb, contactCursor, 1L);
        mTestHelper.insertNamePrefixes(mDb, nameCursor);
        assertEquals(1, getMatchesFromDb("5105272357").size());
    }

    /**
     * Verifies that a contact that has its phone number changed is a match after the update.
     */
    public void testForUpdatedContacts() {
        final MatrixCursor nameCursor =  constructNewNameCursor();
        final MatrixCursor contactCursor = constructNewContactCursor();
        constructNewContactWithDummyIds(contactCursor, nameCursor,
                "510-527-2357", 0,  "James");
        mTestHelper.insertUpdatedContactsAndNumberPrefix(mDb, contactCursor, 0L);
        mTestHelper.insertNamePrefixes(mDb, nameCursor);
        assertEquals(1, getMatchesFromDb("5105272357").size());
        assertEquals(0, getMatchesFromDb("6501234567").size());

        // Update the database with the new contact information
        final MatrixCursor nameCursor2 =  constructNewNameCursor();
        final MatrixCursor contactCursor2 = constructNewContactCursor();
        constructNewContactWithDummyIds(contactCursor2, nameCursor2,
                "650-123-4567", 0,  "James");
        mTestHelper.removeUpdatedContacts(mDb, contactCursor2);
        mTestHelper.insertUpdatedContactsAndNumberPrefix(mDb, contactCursor2, 1L);
        mTestHelper.insertNamePrefixes(mDb, nameCursor2);

        // Now verify the matches are correct based on the new information
        assertEquals(0, getMatchesFromDb("5105272357").size());
        assertEquals(1, getMatchesFromDb("6501234567").size());
    }

    /**
     * Verifies that a contact that is deleted from CP2 is similarly deleted from the database
     */
    public void testForDeletedContacts() {
        final MatrixCursor nameCursor =  constructNewNameCursor();
        final MatrixCursor contactCursor = constructNewContactCursor();
        constructNewContactWithDummyIds(contactCursor, nameCursor,
                "510-527-2357", 0,  "James");
        mTestHelper.insertUpdatedContactsAndNumberPrefix(mDb, contactCursor, 0L);
        mTestHelper.insertNamePrefixes(mDb, nameCursor);
        assertEquals(1, getMatchesFromDb("5105272357").size());

        // Delete the contact and update its projection.
        final MatrixCursor deletedCursor =
                new MatrixCursor(DialerDatabaseHelper.DeleteContactQuery.PROJECTION);
        deletedCursor.addRow(new Object[] {0, 1L});
        mTestHelper.removeDeletedContacts(mDb, deletedCursor);
        assertEquals(0, getMatchesFromDb("5105272357").size());
    }

    /**
     * Verifies that when a contact's number is deleted (but not the entire contact), the
     * number is correctly deleted from the database.
     */
    public void testForDeletedNumber() {
        final MatrixCursor nameCursor =  constructNewNameCursor();
        final MatrixCursor contactCursor = constructNewContactCursor();
        constructNewContactWithDummyIds(contactCursor, nameCursor,
                "510-527-2357", 0,  "James");
        mTestHelper.insertUpdatedContactsAndNumberPrefix(mDb, contactCursor, 0L);
        mTestHelper.insertNamePrefixes(mDb, nameCursor);
        assertEquals(1, getMatchesFromDb("5105272357").size());

        // Match no longer exists after number was deleted from contact
        final MatrixCursor updatedContactCursor =
                new MatrixCursor(DialerDatabaseHelper.UpdatedContactQuery.PROJECTION);
        updatedContactCursor.addRow(new Object[] {0});
        mTestHelper.removeUpdatedContacts(mDb, updatedContactCursor);
        assertEquals(0, getMatchesFromDb("5105272357").size());
    }

    private ArrayList<ContactNumber> getMatchesFromDb(String query) {
        final SmartDialNameMatcher nameMatcher = new SmartDialNameMatcher(query,
                SmartDialPrefix.getMap(), getContext());
        return mTestHelper.getLooseMatches(query, nameMatcher);
    }
}
