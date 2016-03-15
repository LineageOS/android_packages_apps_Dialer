/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.filterednumber;

import android.content.ContentUris;
import android.content.ContentValues;
import android.provider.BlockedNumberContract;
import android.provider.BlockedNumberContract.BlockedNumbers;
import android.test.AndroidTestCase;
import android.test.mock.MockContentResolver;

import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.test.mocks.MockContentProvider;
import com.android.dialer.compat.FilteredNumberCompat;
import com.android.dialer.database.FilteredNumberContract;
import com.android.dialer.database.FilteredNumberContract.FilteredNumber;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberColumns;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BlockedNumbersMigratorTest extends AndroidTestCase {

    private static final String NUMBER = "6502530000";
    private static final String NUMBER1 = "6502530001";
    private static final String NUMBER2 = "6502530002";

    @Mock private BlockedNumbersMigrator.Listener mListener;
    private final MockContentResolver mContentResolver = new MockContentResolver();
    private final MockContentProvider mContentProvider = new MockContentProvider();
    private BlockedNumbersMigrator mMigrator;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        FilteredNumberCompat.setIsEnabledForTest(true);
        mContentResolver.addProvider(FilteredNumberContract.AUTHORITY, mContentProvider);
        mContentResolver.addProvider(BlockedNumberContract.AUTHORITY, mContentProvider);
        mMigrator = new BlockedNumbersMigrator(mContentResolver);
    }

    public void testConstructor_NullContentResolver() {
        try {
            new BlockedNumbersMigrator(null);
            fail();
        } catch (NullPointerException e) {}
    }

    public void testMigrate_M() {
        if (CompatUtils.isNCompatible()) {
            return;
        }
        assertFalse(mMigrator.migrate(mListener));
    }

    public void testMigrate_N_Disabled() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        FilteredNumberCompat.setIsEnabledForTest(false);
        assertFalse(mMigrator.migrate(mListener));
    }

    public void testMigrate_N_NullListener() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        try {
            mMigrator.migrate(null);
            fail();
        } catch (NullPointerException e) {}
    }

    public void testMigrate_N() throws InterruptedException {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        mContentProvider.expectQuery(FilteredNumber.CONTENT_URI)
                .withProjection(FilteredNumberColumns.NUMBER).returnRow(NUMBER).returnRow(NUMBER1)
                .returnRow(NUMBER2);

        setUpNewBlockedNumberExpectations(mContentProvider, NUMBER, 0);
        setUpNewBlockedNumberExpectations(mContentProvider, NUMBER1, 1);
        setUpNewBlockedNumberExpectations(mContentProvider, NUMBER2, 2);

        MigrationListener listener = new MigrationListener();
        assertTrue(mMigrator.migrate(listener));
        listener.waitForCallback();
        assertTrue(FilteredNumberCompat.hasMigratedToNewBlocking());
        mContentProvider.verify();
    }

    public void testMigrate_N_AlreadyBlocked() throws InterruptedException {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        mContentProvider.expectQuery(FilteredNumber.CONTENT_URI)
                .withProjection(FilteredNumberColumns.NUMBER).returnRow(NUMBER);
        mContentProvider.expectQuery(BlockedNumbers.CONTENT_URI)
                .withProjection(BlockedNumbers.COLUMN_ID)
                .withSelection(BlockedNumbers.COLUMN_ORIGINAL_NUMBER + " = ?", NUMBER).returnRow(0);
        // No expectation for insert into BlockedNumbers.CONTENT_URI because it's already there

        MigrationListener listener = new MigrationListener();
        assertTrue(mMigrator.migrate(listener));
        listener.waitForCallback();
        assertTrue(FilteredNumberCompat.hasMigratedToNewBlocking());
        mContentProvider.verify();
    }

    private void setUpNewBlockedNumberExpectations(MockContentProvider contentProvider,
            String number, int returnId) {
        contentProvider.expectQuery(BlockedNumbers.CONTENT_URI)
                .withProjection(BlockedNumbers.COLUMN_ID)
                .withSelection(BlockedNumbers.COLUMN_ORIGINAL_NUMBER + " = ?", number).returnEmptyCursor();
        contentProvider.expectInsert(BlockedNumbers.CONTENT_URI,
                createBlockedNumberInsertValues(number),
                ContentUris.withAppendedId(BlockedNumbers.CONTENT_URI, returnId));
    }

    private ContentValues createBlockedNumberInsertValues(String number) {
        ContentValues values = new ContentValues();
        values.put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number);
        return values;
    }

    private static class MigrationListener implements BlockedNumbersMigrator.Listener {

        private final CountDownLatch mOnCompleteCalled = new CountDownLatch(1);

        @Override
        public void onComplete() {
            mOnCompleteCalled.countDown();
        }

        public void waitForCallback() throws InterruptedException {
            if (!mOnCompleteCalled.await(5000, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Waiting on callback timed out.");
            }
        }
    }
}
