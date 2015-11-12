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
package com.android.dialer.filterednumber;

import android.test.AndroidTestCase;

import com.android.contacts.common.test.mocks.ContactsMockContext;
import com.android.contacts.common.test.mocks.MockContentProvider.Query;
import com.android.dialer.database.FilteredNumberContract;
import com.android.dialer.database.FilteredNumberContract.FilteredNumber;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberColumns;

public class FilteredNumbersUtilTest extends AndroidTestCase {

    private static final String COUNTRY_ISO = "US";

    // Wed Nov 11 2015 15:00:00
    private static final long EARLIER_TIME = 1447282800000l;

    // Wed Nov 11 2015 15:01:40
    private static final long LATER_TIME = 1447282900000l;

    private static final String[] FILTERED_NUMBER_PROJECTION = new String[] {
            FilteredNumberColumns.CREATION_TIME };

    private static final String NORMALIZED_NUMBER = "+16503903411";

    private static final long NULL_CREATION_TIME = -1;

    private ContactsMockContext mContext;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mContext = new ContactsMockContext(getContext(), FilteredNumberContract.AUTHORITY);
    }

    public void testShouldBlockVoicemail_NotBlocked() {
        setupShouldBlockVoicemailQuery(NULL_CREATION_TIME);
        assertFalse(FilteredNumbersUtil.shouldBlockVoicemail(mContext, NORMALIZED_NUMBER,
                COUNTRY_ISO, EARLIER_TIME));
    }

    public void testShouldBlockVoicemail_BlockedBeforeVoicemail() {
        setupShouldBlockVoicemailQuery(EARLIER_TIME);
        assertTrue(FilteredNumbersUtil.shouldBlockVoicemail(mContext, NORMALIZED_NUMBER,
                COUNTRY_ISO, LATER_TIME));
    }

    public void testShouldBlockVoicemail_BlockedAfterVoicemail() {
        setupShouldBlockVoicemailQuery(LATER_TIME);
        assertFalse(FilteredNumbersUtil.shouldBlockVoicemail(mContext, NORMALIZED_NUMBER,
                COUNTRY_ISO, EARLIER_TIME));
    }

    public void testShouldBlockVoicemail_BlockedSameTimeAsVoicemail() {
        setupShouldBlockVoicemailQuery(EARLIER_TIME);
        assertTrue(FilteredNumbersUtil.shouldBlockVoicemail(mContext, NORMALIZED_NUMBER,
                COUNTRY_ISO, EARLIER_TIME));
    }

    public void testShouldBlockVoicemail_BlockedInSameMinuteAsVoicemail() {
        setupShouldBlockVoicemailQuery(EARLIER_TIME);
        assertTrue(FilteredNumbersUtil.shouldBlockVoicemail(mContext, NORMALIZED_NUMBER,
                COUNTRY_ISO, EARLIER_TIME + 30000));
    }

    private void setupShouldBlockVoicemailQuery(long creationTimeMs) {
        Query query = mContext.getContactsProvider().expectQuery(FilteredNumber.CONTENT_URI)
                .withProjection(FILTERED_NUMBER_PROJECTION)
                .withAnySelection()
                .withAnySortOrder();
        if (creationTimeMs == NULL_CREATION_TIME) {
            query.returnEmptyCursor();
            return;
        }
        query.returnRow(creationTimeMs);
    }
}
