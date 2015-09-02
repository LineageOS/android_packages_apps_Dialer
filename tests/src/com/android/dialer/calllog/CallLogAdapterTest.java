/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.dialer.calllog;

import android.content.Context;
import android.database.MatrixCursor;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.View;
import android.widget.LinearLayout;

import com.android.dialer.contactinfo.ContactInfoCache;
import com.android.dialer.contactinfo.ContactInfoCache.OnContactInfoChangedListener;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * Unit tests for {@link CallLogAdapter}.
 */
@SmallTest
public class CallLogAdapterTest extends AndroidTestCase {
    private static final String TEST_NUMBER_1 = "12345678";
    private static final String TEST_NUMBER_2 = "87654321";
    private static final String TEST_NUMBER_3 = "18273645";
    private static final String TEST_NAME = "name";
    private static final String TEST_NUMBER_LABEL = "label";
    private static final int TEST_NUMBER_TYPE = 1;
    private static final String TEST_COUNTRY_ISO = "US";

    /** The object under test. */
    private TestCallLogAdapter mAdapter;

    private MatrixCursor mCursor;
    private View mView;
    private CallLogListItemViewHolder mViewHolder;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Use a call fetcher that does not do anything.
        CallLogAdapter.CallFetcher fakeCallFetcher = new CallLogAdapter.CallFetcher() {
            @Override
            public void fetchCalls() {}
        };

        ContactInfoHelper fakeContactInfoHelper =
                new ContactInfoHelper(getContext(), TEST_COUNTRY_ISO) {
                    @Override
                    public ContactInfo lookupNumber(String number, String countryIso) {
                        ContactInfo info = new ContactInfo();
                        info.number = number;
                        info.formattedNumber = number;
                        return info;
                    }
                };

        mAdapter = new TestCallLogAdapter(getContext(), fakeCallFetcher, fakeContactInfoHelper);
        // The cursor used in the tests to store the entries to display.
        mCursor = new MatrixCursor(CallLogQuery._PROJECTION);
        mCursor.moveToFirst();
        // The views into which to store the data.
        mView = new LinearLayout(getContext());
        mViewHolder = CallLogListItemViewHolder.createForTest(getContext());
        mView.setTag(mViewHolder);
    }

    @Override
    protected void tearDown() throws Exception {
        mAdapter = null;
        mCursor = null;
        mView = null;
        super.tearDown();
    }

    public void testBindView_NoCallLogCacheNorMemoryCache_EnqueueRequest() {
        mCursor.addRow(createCallLogEntry());

        // Bind the views of a single row.
        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        // There is one request for contact details.
        assertEquals(1, mAdapter.getContactInfoCache().requests.size());

        TestContactInfoCache.Request request = mAdapter.getContactInfoCache().requests.get(0);
        // It is for the number we need to show.
        assertEquals(TEST_NUMBER_1, request.number);
        // It has the right country.
        assertEquals(TEST_COUNTRY_ISO, request.countryIso);
        // Since there is nothing in the cache, it is an immediate request.
        assertTrue("should be immediate", request.immediate);
    }

    public void testBindView_CallLogCacheButNoMemoryCache_EnqueueRequest() {
        mCursor.addRow(createCallLogEntryWithCachedValues());

        // Bind the views of a single row.
        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        // There is one request for contact details.
        assertEquals(1, mAdapter.getContactInfoCache().requests.size());

        TestContactInfoCache.Request request = mAdapter.getContactInfoCache().requests.get(0);
        // The values passed to the request, match the ones in the call log cache.
        assertEquals(TEST_NAME, request.callLogInfo.name);
        assertEquals(1, request.callLogInfo.type);
        assertEquals(TEST_NUMBER_LABEL, request.callLogInfo.label);
    }


    public void testBindView_NoCallLogButMemoryCache_EnqueueRequest() {
        mCursor.addRow(createCallLogEntry());
        mAdapter.injectContactInfoForTest(TEST_NUMBER_1, TEST_COUNTRY_ISO, createContactInfo());

        // Bind the views of a single row.
        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        // There is one request for contact details.
        assertEquals(1, mAdapter.getContactInfoCache().requests.size());

        TestContactInfoCache.Request request = mAdapter.getContactInfoCache().requests.get(0);
        // Since there is something in the cache, it is not an immediate request.
        assertFalse("should not be immediate", request.immediate);
    }

    public void testBindView_BothCallLogAndMemoryCache_NoEnqueueRequest() {
        mCursor.addRow(createCallLogEntryWithCachedValues());
        mAdapter.injectContactInfoForTest(TEST_NUMBER_1, TEST_COUNTRY_ISO, createContactInfo());

        // Bind the views of a single row.
        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        // Cache and call log are up-to-date: no need to request update.
        assertEquals(0, mAdapter.getContactInfoCache().requests.size());
    }

    public void testBindView_MismatchBetwenCallLogAndMemoryCache_EnqueueRequest() {
        mCursor.addRow(createCallLogEntryWithCachedValues());

        // Contact info contains a different name.
        ContactInfo info = createContactInfo();
        info.name = "new name";
        mAdapter.injectContactInfoForTest(TEST_NUMBER_1, TEST_COUNTRY_ISO, info);

        // Bind the views of a single row.
        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        // There is one request for contact details.
        assertEquals(1, mAdapter.getContactInfoCache().requests.size());

        TestContactInfoCache.Request request = mAdapter.getContactInfoCache().requests.get(0);
        // Since there is something in the cache, it is not an immediate request.
        assertFalse("should not be immediate", request.immediate);
    }

    public void testBindVoicemailPromoCard() {
        mCursor.addRow(createCallLogEntry(TEST_NUMBER_1));
        mCursor.addRow(createCallLogEntry(TEST_NUMBER_1));
        mCursor.addRow(createCallLogEntry(TEST_NUMBER_2));
        mCursor.addRow(createCallLogEntry(TEST_NUMBER_2));
        mCursor.addRow(createCallLogEntry(TEST_NUMBER_2));
        mCursor.addRow(createCallLogEntry(TEST_NUMBER_3));

        // Bind the voicemail promo card.
        mAdapter.showVoicemailPromoCard(true);
        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(PromoCardViewHolder.createForTest(getContext()), 0);

        // Check that displaying the promo card does not affect the grouping or list display.
        mAdapter.onBindViewHolder(mViewHolder, 1);
        assertEquals(2, mAdapter.getGroupSize(1));
        assertEquals(TEST_NUMBER_1, mViewHolder.number);

        mAdapter.onBindViewHolder(mViewHolder, 2);
        assertEquals(3, mAdapter.getGroupSize(2));
        assertEquals(TEST_NUMBER_2, mViewHolder.number);

        mAdapter.onBindViewHolder(mViewHolder, 3);
        assertEquals(1, mAdapter.getGroupSize(3));
        assertEquals(TEST_NUMBER_3, mViewHolder.number);
    }

    /** Returns a contact info with default values. */
    private ContactInfo createContactInfo() {
        ContactInfo info = new ContactInfo();
        info.number = TEST_NUMBER_1;
        info.name = TEST_NAME;
        info.type = TEST_NUMBER_TYPE;
        info.label = TEST_NUMBER_LABEL;
        return info;
    }

    /** Returns a call log entry without cached values. */
    private Object[] createCallLogEntry() {
        return createCallLogEntry(TEST_NUMBER_1);
    }

    private Object[] createCallLogEntry(String testNumber) {
        Object[] values = CallLogQueryTestUtils.createTestValues();
        values[CallLogQuery.NUMBER] = testNumber;
        values[CallLogQuery.COUNTRY_ISO] = TEST_COUNTRY_ISO;
        return values;
    }

    /** Returns a call log entry with a cached values. */
    private Object[] createCallLogEntryWithCachedValues() {
        Object[] values = createCallLogEntry();
        values[CallLogQuery.CACHED_NAME] = TEST_NAME;
        values[CallLogQuery.CACHED_NUMBER_TYPE] = TEST_NUMBER_TYPE;
        values[CallLogQuery.CACHED_NUMBER_LABEL] = TEST_NUMBER_LABEL;
        return values;
    }

    /**
     * Subclass of {@link CallLogAdapter} used in tests to intercept certain calls.
     */
    private static final class TestCallLogAdapter extends CallLogAdapter {
        public TestCallLogAdapter(Context context, CallFetcher callFetcher,
                ContactInfoHelper contactInfoHelper) {
            super(context, callFetcher, contactInfoHelper, null, false);
            mContactInfoCache = new TestContactInfoCache(
                    contactInfoHelper, mOnContactInfoChangedListener);
        }

        public TestContactInfoCache getContactInfoCache() {
            return (TestContactInfoCache) mContactInfoCache;
        }

        public void showVoicemailPromoCard(boolean show) {
            mShowVoicemailPromoCard = show;
        }
    }

    private static final class TestContactInfoCache extends ContactInfoCache {
        public static class Request {
            public final String number;
            public final String countryIso;
            public final ContactInfo callLogInfo;
            public final boolean immediate;

            public Request(String number, String countryIso, ContactInfo callLogInfo,
                    boolean immediate) {
                this.number = number;
                this.countryIso = countryIso;
                this.callLogInfo = callLogInfo;
                this.immediate = immediate;
            }
        }

        public final List<Request> requests = Lists.newArrayList();

        public TestContactInfoCache(
                ContactInfoHelper contactInfoHelper, OnContactInfoChangedListener listener) {
            super(contactInfoHelper, listener);
        }

        @Override
        protected void enqueueRequest(String number, String countryIso, ContactInfo callLogInfo,
                boolean immediate) {
            requests.add(new Request(number, countryIso, callLogInfo, immediate));
        }
    }
}
