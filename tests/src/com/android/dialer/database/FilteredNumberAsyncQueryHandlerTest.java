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

package com.android.dialer.database;

import android.content.ContentUris;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.BlockedNumberContract;
import android.provider.BlockedNumberContract.BlockedNumbers;
import android.test.InstrumentationTestCase;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.test.mocks.MockContentProvider;
import com.android.contacts.common.test.mocks.MockContentProvider.Query;
import com.android.dialer.compat.FilteredNumberCompat;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler.OnBlockNumberListener;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler.OnCheckBlockedListener;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler.OnHasBlockedNumbersListener;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler.OnUnblockNumberListener;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberColumns;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberSources;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberTypes;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SmallTest
public class FilteredNumberAsyncQueryHandlerTest extends InstrumentationTestCase {

    private static final String E164_NUMBER = "+16502530000";
    private static final String NUMBER = "6502530000";
    private static final String COUNTRY_ISO = "US";
    private static final Integer ID = 1;
    private static final Integer ID2 = 2;
    private static final Uri BLOCKED_NUMBER_URI_N = CompatUtils.isNCompatible() ?
            Uri.withAppendedPath(BlockedNumberContract.AUTHORITY_URI, "blocked") : null;
    private static final Uri BLOCKED_NUMBER_URI_M =
            Uri.withAppendedPath(FilteredNumberContract.AUTHORITY_URI, "filtered_numbers_table");
    private static final Uri BLOCKED_NUMBER_URI = CompatUtils.isNCompatible() ? BLOCKED_NUMBER_URI_N
            : BLOCKED_NUMBER_URI_M;
    private static final Uri BLOCKED_NUMBER_URI_WITH_ID =
            ContentUris.withAppendedId(BLOCKED_NUMBER_URI, ID);
    private static final Uri EXPECTED_URI = Uri.fromParts("android", "google", "dialer");

    private final MockContentResolver mContentResolver = new MockContentResolver();
    private final MockContentProvider mContentProvider = new MockContentProvider();

    @Override
    public void setUp() throws Exception {
        super.setUp();
        FilteredNumberCompat.setIsEnabledForTest(true);
        if (CompatUtils.isNCompatible()) {
            mContentResolver.addProvider(BlockedNumberContract.AUTHORITY, mContentProvider);
        } else {
            mContentResolver.addProvider(FilteredNumberContract.AUTHORITY, mContentProvider);
        }
    }

    public void testHasBlockedNumbers_Disabled() throws Throwable {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        FilteredNumberCompat.setIsEnabledForTest(false);
        final MockContentResolver resolver = new MockContentResolver();
        MockContentProvider disabledProvider = new MockContentProvider();
        resolver.addProvider(FilteredNumberContract.AUTHORITY, disabledProvider);

        disabledProvider.expectQuery(BLOCKED_NUMBER_URI_M).withProjection(FilteredNumberColumns._ID)
                .withSelection(FilteredNumberColumns.TYPE + "="
                        + FilteredNumberTypes.BLOCKED_NUMBER, null).returnRow(ID);
        final HasBlockedNumbersListener listener = new HasBlockedNumbersListener();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                new FilteredNumberAsyncQueryHandler(resolver).hasBlockedNumbers(listener);
            }
        });
        assertTrue(listener.waitForCallback());
        disabledProvider.verify();
    }

    public void testHasBlockedNumbers_NoResults() throws Throwable {
        newHasBlockedNumbersExpectedQuery().returnEmptyCursor();
        final HasBlockedNumbersListener listener = new HasBlockedNumbersListener();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                new FilteredNumberAsyncQueryHandler(mContentResolver).hasBlockedNumbers(listener);
            }
        });
        assertFalse(listener.waitForCallback());
        mContentProvider.verify();
    }

    public void testHasBlockedNumbers() throws Throwable {
        newHasBlockedNumbersExpectedQuery().returnRow(ID);
        final HasBlockedNumbersListener listener = new HasBlockedNumbersListener();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                new FilteredNumberAsyncQueryHandler(mContentResolver).hasBlockedNumbers(listener);
            }
        });
        assertTrue(listener.waitForCallback());
        mContentProvider.verify();
    }

    public void testIsBlockedNumber_Disabled() throws Throwable {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        FilteredNumberCompat.setIsEnabledForTest(false);
        final MockContentResolver resolver = new MockContentResolver();
        MockContentProvider disabledProvider = new MockContentProvider();
        resolver.addProvider(FilteredNumberContract.AUTHORITY, disabledProvider);
        disabledProvider.expectQuery(BLOCKED_NUMBER_URI_M)
                .withProjection(FilteredNumberColumns._ID, FilteredNumberColumns.TYPE)
                .withSelection(FilteredNumberColumns.NORMALIZED_NUMBER + " = ?", E164_NUMBER)
                .returnRow(ID, FilteredNumberTypes.BLOCKED_NUMBER);
        final CheckBlockedListener listener = new CheckBlockedListener();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                new FilteredNumberAsyncQueryHandler(resolver)
                        .isBlockedNumber(listener, NUMBER, COUNTRY_ISO);
            }
        });
        assertEquals(ID, listener.waitForCallback());
        mContentProvider.verify();
    }

    public void testIsBlockedNumber_NoResults() throws Throwable {
        newIsBlockedNumberExpectedQuery().returnEmptyCursor();
        final CheckBlockedListener listener = new CheckBlockedListener();

        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                new FilteredNumberAsyncQueryHandler(mContentResolver)
                        .isBlockedNumber(listener, NUMBER, COUNTRY_ISO);
            }
        });
        assertNull(listener.waitForCallback());
        mContentProvider.verify();
    }

    public void testIsBlockedNumber() throws Throwable {
        if (CompatUtils.isNCompatible()) {
            newIsBlockedNumberExpectedQuery().returnRow(ID);
        } else {
            newIsBlockedNumberExpectedQuery().returnRow(ID, FilteredNumberTypes.BLOCKED_NUMBER);
        }
        final CheckBlockedListener listener = new CheckBlockedListener();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                new FilteredNumberAsyncQueryHandler(mContentResolver)
                        .isBlockedNumber(listener, NUMBER, COUNTRY_ISO);
           }
        });
        assertEquals(ID, listener.waitForCallback());
        mContentProvider.verify();
    }

    public void testIsBlockedNumber_MultipleResults() throws Throwable {
        if (CompatUtils.isNCompatible()) {
            newIsBlockedNumberExpectedQuery().returnRow(ID).returnRow(ID2);
        } else {
            newIsBlockedNumberExpectedQuery().returnRow(ID, FilteredNumberTypes.BLOCKED_NUMBER)
                    .returnRow(ID2, FilteredNumberTypes.BLOCKED_NUMBER);
        }
        final CheckBlockedListener listener = new CheckBlockedListener();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                new FilteredNumberAsyncQueryHandler(mContentResolver)
                        .isBlockedNumber(listener, NUMBER, COUNTRY_ISO);
            }
        });
        // When there are multiple matches, the first is returned
        assertEquals(ID, listener.waitForCallback());
        mContentProvider.verify();
    }

    public void testBlockNumber_Disabled() throws Throwable {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        FilteredNumberCompat.setIsEnabledForTest(false);
        final MockContentResolver resolver = new MockContentResolver();
        MockContentProvider disabledProvider = new MockContentProvider();
        resolver.addProvider(FilteredNumberContract.AUTHORITY, disabledProvider);

        disabledProvider.expectInsert(BLOCKED_NUMBER_URI_M, newBlockNumberContentValuesM(),
                EXPECTED_URI);
        final BlockNumberListener listener = new BlockNumberListener();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                new FilteredNumberAsyncQueryHandler(resolver).blockNumber(listener, E164_NUMBER,
                        NUMBER, COUNTRY_ISO);
            }
        });
        assertSame(EXPECTED_URI, listener.waitForCallback());
        disabledProvider.verify();
    }

    public void testBlockNumber() throws Throwable {
        mContentProvider.expectInsert(BLOCKED_NUMBER_URI, newBlockNumberContentValues(),
                EXPECTED_URI);
        final BlockNumberListener listener = new BlockNumberListener();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                new FilteredNumberAsyncQueryHandler(mContentResolver).blockNumber(listener,
                        E164_NUMBER, NUMBER, COUNTRY_ISO);
            }
        });
        assertSame(EXPECTED_URI, listener.waitForCallback());
        mContentProvider.verify();
    }

    public void testBlockNumber_NullNormalizedNumber() throws Throwable {
        mContentProvider.expectInsert(BLOCKED_NUMBER_URI, newBlockNumberContentValues(),
                EXPECTED_URI);
        final BlockNumberListener listener = new BlockNumberListener();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                new FilteredNumberAsyncQueryHandler(mContentResolver).blockNumber(listener,
                        NUMBER, COUNTRY_ISO);
            }
        });
        assertSame(EXPECTED_URI, listener.waitForCallback());
        mContentProvider.verify();
    }

    public void testUnblockNumber_Disabled() throws Throwable {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        FilteredNumberCompat.setIsEnabledForTest(false);
        final MockContentResolver resolver = new MockContentResolver();
        MockContentProvider disabledProvider = new MockContentProvider();
        resolver.addProvider(FilteredNumberContract.AUTHORITY, disabledProvider);

        Uri uriWithId = ContentUris.withAppendedId(BLOCKED_NUMBER_URI_M, ID);
        disabledProvider.expectQuery(uriWithId)
                .withProjection(null)
                .withDefaultProjection(FilteredNumberCompat.getIdColumnName())
                .withSelection(null, null)
                .withSortOrder(null)
                .returnRow(ID);
        disabledProvider.expectDelete(uriWithId).returnRowsAffected(1);
        final UnblockNumberListener listener = new UnblockNumberListener();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                new FilteredNumberAsyncQueryHandler(resolver).unblock(listener, ID);
            }
        });
        assertNotNull(listener.waitForCallback());
        disabledProvider.verify();
    }

    public void testUnblockNumber_NullId() {
        try {
            new FilteredNumberAsyncQueryHandler(mContentResolver).unblock(null, (Integer) null);
            fail();
        } catch (IllegalArgumentException e) {}
    }

    public void testUnblockNumber() throws Throwable {
        mContentProvider.expectQuery(BLOCKED_NUMBER_URI_WITH_ID)
                .withProjection(null)
                .withDefaultProjection(FilteredNumberCompat.getIdColumnName())
                .withSelection(null, null)
                .withSortOrder(null)
                .returnRow(ID);
        mContentProvider.expectDelete(BLOCKED_NUMBER_URI_WITH_ID).returnRowsAffected(1);
        final UnblockNumberListener listener = new UnblockNumberListener();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                new FilteredNumberAsyncQueryHandler(mContentResolver).unblock(listener, ID);
            }
        });
        assertNotNull(listener.waitForCallback());
        mContentProvider.verify();
    }

    private Query newIsBlockedNumberExpectedQuery() {
        if (CompatUtils.isNCompatible()) {
            return newIsBlockedNumberExpectedQueryN();
        }
        return newIsBlockedNumberExpectedQueryM();
    }

    private Query newIsBlockedNumberExpectedQueryN() {
        return mContentProvider.expectQuery(BLOCKED_NUMBER_URI)
                .withProjection(BlockedNumbers.COLUMN_ID)
                .withSelection(BlockedNumbers.COLUMN_E164_NUMBER + " = ?", E164_NUMBER);
    }

    private Query newIsBlockedNumberExpectedQueryM() {
        return mContentProvider.expectQuery(BLOCKED_NUMBER_URI)
                .withProjection(FilteredNumberColumns._ID, FilteredNumberColumns.TYPE)
                .withSelection(FilteredNumberColumns.NORMALIZED_NUMBER + " = ?", E164_NUMBER);
    }

    private Query newHasBlockedNumbersExpectedQuery() {
        if (CompatUtils.isNCompatible()) {
            return newHasBlockedNumbersExpectedQueryN();
        }
        return newHasBlockedNumbersExpectedQueryM();
    }

    private Query newHasBlockedNumbersExpectedQueryN() {
        return mContentProvider.expectQuery(BLOCKED_NUMBER_URI)
                .withProjection(BlockedNumbers.COLUMN_ID)
                .withSelection(null, null);
    }

    private Query newHasBlockedNumbersExpectedQueryM() {
        return mContentProvider.expectQuery(BLOCKED_NUMBER_URI).withProjection(
                FilteredNumberColumns._ID)
                .withSelection(FilteredNumberColumns.TYPE + "="
                        + FilteredNumberTypes.BLOCKED_NUMBER, null);
    }

    private ContentValues newBlockNumberContentValues() {
        if (CompatUtils.isNCompatible()) {
            return newBlockNumberContentValuesN();
        }
        return newBlockNumberContentValuesM();
    }

    private ContentValues newBlockNumberContentValuesN() {
        ContentValues contentValues = new ContentValues();
        contentValues.put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, NUMBER);
        return contentValues;
    }

    private ContentValues newBlockNumberContentValuesM() {
        ContentValues contentValues = new ContentValues();
        contentValues.put(FilteredNumberColumns.NORMALIZED_NUMBER, E164_NUMBER);
        contentValues.put(FilteredNumberColumns.NUMBER, NUMBER);
        contentValues.put(FilteredNumberColumns.COUNTRY_ISO, COUNTRY_ISO);
        contentValues.put(FilteredNumberColumns.TYPE, FilteredNumberTypes.BLOCKED_NUMBER);
        contentValues.put(FilteredNumberColumns.SOURCE, FilteredNumberSources.USER);
        return contentValues;
    }

    private class CheckBlockedListener implements OnCheckBlockedListener {
        public final CountDownLatch onCheckCompleteCalled;
        public Integer id;

        public CheckBlockedListener() {
            onCheckCompleteCalled = new CountDownLatch(1);
        }

        @Override
        public void onCheckComplete(Integer id) {
            this.id = id;
            onCheckCompleteCalled.countDown();
        }

        public Integer waitForCallback() throws InterruptedException {
            if (!onCheckCompleteCalled.await(5000, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Waiting on callback timed out.");
            }
            return id;
        }
    }

    private class HasBlockedNumbersListener implements OnHasBlockedNumbersListener {
        public final CountDownLatch onHasBlockedNumbersCalled;
        public boolean hasBlockedNumbers;

        public HasBlockedNumbersListener() {
            onHasBlockedNumbersCalled = new CountDownLatch(1);
        }

        @Override
        public void onHasBlockedNumbers(boolean hasBlockedNumbers) {
            this.hasBlockedNumbers = hasBlockedNumbers;
            onHasBlockedNumbersCalled.countDown();
        }

        public boolean waitForCallback() throws InterruptedException {
            if (!onHasBlockedNumbersCalled.await(5000, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Waiting on callback timed out.");
            }
            return hasBlockedNumbers;
        }
    }

    private class BlockNumberListener implements OnBlockNumberListener {
        public final CountDownLatch onBlockCompleteCalled;
        public Uri uri;

        public BlockNumberListener() {
            onBlockCompleteCalled = new CountDownLatch(1);
        }

        @Override
        public void onBlockComplete(Uri uri) {
            this.uri = uri;
            onBlockCompleteCalled.countDown();
        }

        public Uri waitForCallback() throws InterruptedException {
            if (!onBlockCompleteCalled.await(5000, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Waiting on callback timed out.");
            }
            return uri;
        }
    }

    private class UnblockNumberListener implements OnUnblockNumberListener {
        public final CountDownLatch onUnblockCompleteCalled;
        public Integer result;

        public UnblockNumberListener() {
            onUnblockCompleteCalled = new CountDownLatch(1);
        }

        @Override
        public void onUnblockComplete(int rows, ContentValues values) {
            result = rows;
            onUnblockCompleteCalled.countDown();
        }

        public Integer waitForCallback() throws InterruptedException {
            if (!onUnblockCompleteCalled.await(5000, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Waiting on callback timed out.");
            }
            return result;
        }
    }
}
