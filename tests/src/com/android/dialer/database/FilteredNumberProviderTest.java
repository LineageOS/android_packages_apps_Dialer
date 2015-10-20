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
 * limitations under the License
 */

package com.android.dialer.database;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.test.ProviderTestCase2;
import android.test.suitebuilder.annotation.MediumTest;

@MediumTest
public class FilteredNumberProviderTest extends
        ProviderTestCase2<FilteredNumberProviderTest.TestFilteredNumberProvider> {
    private ContentResolver mResolver;

    private static final String TIME_ZONE_AMERICA_LOS_ANGELES = "America/Los_Angeles";
    private static final String DEFAULT_TIMEZONE = TIME_ZONE_AMERICA_LOS_ANGELES;
    private static final String DEFAULT_COUNTRY_ISO = "US";
    private static final String TEST_NUMBER = "234567890";
    private static final String TEST_NORMALIZED_NUMBER = "+1234567890";
    private static final long TEST_TIME = 1439936706;

    public FilteredNumberProviderTest () {
        super(TestFilteredNumberProvider.class, FilteredNumberContract.AUTHORITY);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResolver = getMockContentResolver();
    }

    public void testInsert() {
        // Insert row
        Uri uri = mResolver.insert(
                FilteredNumberContract.FilteredNumber.CONTENT_URI,
                getTestValues(null));
        assertNotNull(uri);
        long id = ContentUris.parseId(uri);
        assertTrue(id > 0);
    }

    public void testQuery() {
        Cursor cursor = mResolver.query(
                FilteredNumberContract.FilteredNumber.CONTENT_URI, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(cursor.getCount(), 0);
        cursor.close();
    }

    public void testInsertAndQuery() {
        // Insert row
        ContentValues testValues = getTestValues(null);
        Uri uri = mResolver.insert(FilteredNumberContract.FilteredNumber.CONTENT_URI, testValues);

        // Query
        Cursor cursor = mResolver.query(uri, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(cursor.getCount(), 1);

        cursor.moveToFirst();
        assertCursorValues(cursor, testValues);
        cursor.close();
    }

    public void testIllegalUri() {
        try {
            mResolver.query(
                    Uri.withAppendedPath(
                            FilteredNumberContract.FilteredNumber.CONTENT_URI,
                            "ILLEGAL"), null, null, null, null);
            fail("Expecting exception but none was thrown.");
        } catch (IllegalArgumentException e) {
        }
    }

    public void testQueryWithId() {
        // Insert row
        ContentValues testValues = getTestValues(null);
        Uri uri = mResolver.insert(FilteredNumberContract.FilteredNumber.CONTENT_URI, testValues);
        long id = ContentUris.parseId(uri);

        // Query
        Cursor cursor = mResolver.query(
                ContentUris.withAppendedId(
                        FilteredNumberContract.FilteredNumber.CONTENT_URI,
                        id), null, null, null, null);
        assertNotNull(cursor);
        assertEquals(cursor.getCount(), 1);

        cursor.moveToFirst();
        assertCursorValues(cursor, testValues);
        cursor.close();
    }

    public void testDelete() {
        // Insert row
        Uri uri = mResolver.insert(
                FilteredNumberContract.FilteredNumber.CONTENT_URI,
                getTestValues(null));
        long id = ContentUris.parseId(uri);

        // Delete row
        int rows = mResolver.delete(
                FilteredNumberContract.FilteredNumber.CONTENT_URI,
                FilteredNumberContract.FilteredNumberColumns._ID + " = ?",
                new String[]{Long.toString(id)});
        assertEquals(rows, 1);

        // Query
        Cursor cursor =  mResolver.query(uri, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(cursor.getCount(), 0);
        cursor.close();
    }

    public void testUpdate() {
        // Insert row
        Uri uri = mResolver.insert(
                FilteredNumberContract.FilteredNumber.CONTENT_URI,
                getTestValues(null));

        // Update row
        ContentValues v = new ContentValues();
        v.put(FilteredNumberContract.FilteredNumberColumns.TIMES_FILTERED, 3);
        v.put(FilteredNumberContract.FilteredNumberColumns.LAST_TIME_FILTERED, TEST_TIME);
        int rows = mResolver.update(FilteredNumberContract.FilteredNumber.CONTENT_URI, v,
                FilteredNumberContract.FilteredNumberColumns.NORMALIZED_NUMBER + " = ?",
                new String[]{TEST_NORMALIZED_NUMBER});
        assertEquals(rows, 1);

        ContentValues expected = getTestValues(TEST_TIME);
        expected.put(FilteredNumberContract.FilteredNumberColumns.TIMES_FILTERED, 3);
        expected.put(FilteredNumberContract.FilteredNumberColumns.LAST_TIME_FILTERED, TEST_TIME);

        // Re-query
        Cursor cursor =  mResolver.query(uri, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(cursor.getCount(), 1);
        cursor.moveToFirst();
        assertCursorValues(cursor, expected);
        cursor.close();
    }

    public void testInsertDefaultValues() {
        // Insert row
        ContentValues v = getTestValues(null);
        Uri uri = mResolver.insert(FilteredNumberContract.FilteredNumber.CONTENT_URI, v);
        assertNotNull(uri);
        long id = ContentUris.parseId(uri);
        assertTrue(id > 0);

        // Query
        Cursor cursor =  mResolver.query(uri, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(cursor.getCount(), 1);

        int creationTimeIndex =
                cursor.getColumnIndex(FilteredNumberContract.FilteredNumberColumns.CREATION_TIME);
        cursor.moveToFirst();
        assertEquals(cursor.getLong(creationTimeIndex), TEST_TIME);
        cursor.close();
    }

    @Override
    protected void tearDown() throws Exception {
        getProvider().closeDb();
        super.tearDown();
    }

    private ContentValues getTestValues(Long timeNow) {
        ContentValues v = new ContentValues();
        v.putNull(FilteredNumberContract.FilteredNumberColumns._ID);
        v.put(FilteredNumberContract.FilteredNumberColumns.NORMALIZED_NUMBER,
                TEST_NORMALIZED_NUMBER);
        v.put(FilteredNumberContract.FilteredNumberColumns.NUMBER, TEST_NUMBER);
        v.put(FilteredNumberContract.FilteredNumberColumns.COUNTRY_ISO, DEFAULT_COUNTRY_ISO);
        v.put(FilteredNumberContract.FilteredNumberColumns.TIMES_FILTERED, 0);
        v.putNull(FilteredNumberContract.FilteredNumberColumns.LAST_TIME_FILTERED);
        v.put(FilteredNumberContract.FilteredNumberColumns.CREATION_TIME, timeNow);
        v.put(FilteredNumberContract.FilteredNumberColumns.SOURCE, 1);
        v.put(FilteredNumberContract.FilteredNumberColumns.TYPE, 1);
        return v;
    }

    private void assertCursorValues(Cursor cursor, ContentValues expectedValues) {
        ContentValues v = new ContentValues();
        DatabaseUtils.cursorRowToContentValues(cursor, v);
        v.remove(FilteredNumberContract.FilteredNumberColumns._ID);
        expectedValues.remove(FilteredNumberContract.FilteredNumberColumns._ID);
        assertEquals(v.toString(), expectedValues.toString());
    }

    public static class TestFilteredNumberProvider extends FilteredNumberProvider {
        private DialerDatabaseHelper mDialerDatabaseHelper;

        @Override
        protected DialerDatabaseHelper getDatabaseHelper(Context context) {
            if (mDialerDatabaseHelper == null) {
                mDialerDatabaseHelper = DialerDatabaseHelper.getNewInstanceForTest(context);
            }
            return mDialerDatabaseHelper;
        }

        protected void closeDb() {
            mDialerDatabaseHelper.close();
        }

        @Override
        protected long getCurrentTimeMs() {
            return TEST_TIME;
        }
    }
}
