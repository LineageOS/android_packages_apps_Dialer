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

import android.content.ContentValues;
import android.net.Uri;
import android.test.InstrumentationTestCase;
import android.test.mock.MockContentResolver;

import com.android.contacts.common.test.mocks.MockContentProvider;
import com.android.contacts.common.test.mocks.MockContentProvider.Query;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler.OnBlockNumberListener;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler.OnCheckBlockedListener;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler.OnHasBlockedNumbersListener;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberColumns;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberSources;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberTypes;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FilteredNumberAsyncQueryHandlerTest extends InstrumentationTestCase {

    private static final String E164_NUMBER = "+16502530000";
    private static final String NUMBER = "6502530000";
    private static final String COUNTRY_ISO = "US";
    private static final Integer ID = 1;
    private static final Uri BLOCKED_NUMBER_URI =
            Uri.withAppendedPath(FilteredNumberContract.AUTHORITY_URI, "filtered_numbers_table");
    private static final Uri EXPECTED_URI = Uri.fromParts("android", "google", "dialer");

    private final MockContentResolver mContentResolver = new MockContentResolver();
    private final MockContentProvider mContentProvider = new MockContentProvider();

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mContentResolver.addProvider(FilteredNumberContract.AUTHORITY, mContentProvider);
    }

    public void testIsBlockedNumber_NoResults() throws Throwable {
        newIsBlockedNumberExpectedQueryM().returnEmptyCursor();
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
        newIsBlockedNumberExpectedQueryM().returnRow(ID, FilteredNumberTypes.BLOCKED_NUMBER);
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

    public void testHasBlockedNumbers_NoResults() throws Throwable {
        newHasBlockedNumbersExpectedQueryM().returnEmptyCursor();
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
        newHasBlockedNumbersExpectedQueryM().returnRow(ID);
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

    private Query newIsBlockedNumberExpectedQueryM() {
        return mContentProvider.expectQuery(BLOCKED_NUMBER_URI)
                .withProjection(FilteredNumberColumns._ID, FilteredNumberColumns.TYPE)
                .withSelection(FilteredNumberColumns.NORMALIZED_NUMBER + " = ?", E164_NUMBER);
    }

    private Query newHasBlockedNumbersExpectedQueryM() {
        return mContentProvider.expectQuery(BLOCKED_NUMBER_URI).withProjection(
                FilteredNumberColumns._ID)
                .withSelection(FilteredNumberColumns.TYPE + "="
                        + FilteredNumberTypes.BLOCKED_NUMBER, null);
    }

    public void testBlockNumber() throws Throwable {
        mContentProvider.expectInsert(BLOCKED_NUMBER_URI, newBlockNumberContentValues(),
                EXPECTED_URI);
        final BlockingArgumentCaptorOnBlockNumberListener listener =
                new BlockingArgumentCaptorOnBlockNumberListener();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                new FilteredNumberAsyncQueryHandler(mContentResolver).blockNumber(listener,
                        E164_NUMBER, NUMBER, COUNTRY_ISO);
            }
        });
        assertTrue(listener.onBlockCompleteCalled.await(5000, TimeUnit.MILLISECONDS));
        assertSame(EXPECTED_URI, listener.uri);
        mContentProvider.verify();
    }

    public void testBlockNumber_NullNormalizedNumber() throws Throwable {
        mContentProvider.expectInsert(BLOCKED_NUMBER_URI, newBlockNumberContentValues(),
                EXPECTED_URI);
        final BlockingArgumentCaptorOnBlockNumberListener listener =
                new BlockingArgumentCaptorOnBlockNumberListener();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                new FilteredNumberAsyncQueryHandler(mContentResolver).blockNumber(listener,
                        NUMBER, COUNTRY_ISO);
            }
        });
        assertTrue(listener.onBlockCompleteCalled.await(5000, TimeUnit.MILLISECONDS));
        assertSame(EXPECTED_URI, listener.uri);
        mContentProvider.verify();
    }

    private ContentValues newBlockNumberContentValues() {
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

    private class BlockingArgumentCaptorOnBlockNumberListener implements OnBlockNumberListener {
        public final CountDownLatch onBlockCompleteCalled;
        public Uri uri;

        public BlockingArgumentCaptorOnBlockNumberListener() {
            onBlockCompleteCalled = new CountDownLatch(1);
        }

        @Override
        public void onBlockComplete(Uri uri) {
            this.uri = uri;
            onBlockCompleteCalled.countDown();
        }
    }
}
