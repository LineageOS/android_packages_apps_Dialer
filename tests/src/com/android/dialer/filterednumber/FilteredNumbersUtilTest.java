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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.content.Context;
import android.preference.PreferenceManager;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.test.mocks.ContactsMockContext;
import com.android.contacts.common.test.mocks.MockContentProvider.Query;
import com.android.dialer.compat.FilteredNumberCompat;
import com.android.dialer.database.FilteredNumberContract;
import com.android.dialer.database.FilteredNumberContract.FilteredNumber;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberColumns;

@SmallTest
public class FilteredNumbersUtilTest extends AndroidTestCase {

    private static final String COUNTRY_ISO = "US";

    // Wed Nov 11 2015 15:00:00
    private static final long EARLIER_TIME = 1447282800000L;

    // Wed Nov 11 2015 15:01:40
    private static final long LATER_TIME = 1447282900000L;

    private static final String[] FILTERED_NUMBER_PROJECTION = new String[] {
            FilteredNumberColumns.CREATION_TIME };

    private static final String NORMALIZED_NUMBER = "+16503903411";

    private static final long NULL_CREATION_TIME = -1;

    private ContactsMockContext mContext;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mContext = new ContactsMockContext(getContext(), FilteredNumberContract.AUTHORITY);

        // Reset whether an emergency number was dialed
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putLong(FilteredNumbersUtil.LAST_EMERGENCY_CALL_MS_PREF_KEY, 0)
                .apply();
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

    public void testShouldBlockVoicemail_AfterEmergencyCall() {
        // Just called emergency services
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putLong(FilteredNumbersUtil.LAST_EMERGENCY_CALL_MS_PREF_KEY,
                        System.currentTimeMillis())
                .apply();
        assertFalse(FilteredNumbersUtil.shouldBlockVoicemail(mContext, NORMALIZED_NUMBER,
                COUNTRY_ISO, 0));
    }

    public void testMaybeNotifyCallBlockingDisabled_Migrated() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        FilteredNumberCompat.setIsEnabledForTest(true);
        FilteredNumberCompat.setHasMigratedToNewBlocking(true);
        Context mockContext = mock(Context.class);

        FilteredNumbersUtil.maybeNotifyCallBlockingDisabled(mockContext);
        verifyZeroInteractions(mockContext);
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
