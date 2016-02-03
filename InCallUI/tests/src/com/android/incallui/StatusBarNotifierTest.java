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
 * limitations under the License
 */

package com.android.incallui;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.contacts.common.preference.ContactsPreferences;
import com.android.incallui.ContactInfoCache.ContactCacheEntry;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@MediumTest
public class StatusBarNotifierTest extends AndroidTestCase {

    private static final String NAME_PRIMARY = "Full Name";
    private static final String NAME_ALTERNATIVE = "Name, Full";
    private static final String LOCATION = "US";
    private static final String NUMBER = "8006459001";

    @Mock private Call mCall;
    @Mock private ContactsPreferences mContactsPreferences;
    private ContactCacheEntry mUnlockedContactInfo;
    private ContactCacheEntry mLockedContactInfo;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);

        Mockito.when(mContactsPreferences.getDisplayOrder())
                .thenReturn(ContactsPreferences.DISPLAY_ORDER_PRIMARY);

        // Unlocked all contact info is available
        mUnlockedContactInfo = new ContactCacheEntry();
        mUnlockedContactInfo.namePrimary = NAME_PRIMARY;
        mUnlockedContactInfo.nameAlternative = NAME_ALTERNATIVE;
        mUnlockedContactInfo.location = LOCATION;
        mUnlockedContactInfo.number = NUMBER;

        // Locked only number and location are available
        mLockedContactInfo = new ContactCacheEntry();
        mLockedContactInfo .location = LOCATION;
        mLockedContactInfo .number = NUMBER;
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        ContactsPreferencesFactory.setTestInstance(null);
    }

    public void testGetContentTitle_ConferenceCall() {
        ContactsPreferencesFactory.setTestInstance(null);
        StatusBarNotifier statusBarNotifier = new StatusBarNotifier(mContext, null);

        Mockito.when(mCall.isConferenceCall()).thenReturn(true);
        Mockito.when(mCall.hasProperty(Mockito.anyInt())).thenReturn(false);

        assertEquals(mContext.getResources().getString(R.string.card_title_conf_call),
                statusBarNotifier.getContentTitle(null, mCall));
    }

    public void testGetContentTitle_Unlocked() {
        ContactsPreferencesFactory.setTestInstance(mContactsPreferences);
        StatusBarNotifier statusBarNotifier = new StatusBarNotifier(mContext, null);
        assertEquals(NAME_PRIMARY, statusBarNotifier.getContentTitle(mUnlockedContactInfo, mCall));
    }

    public void testGetContentTitle_Locked() {
        ContactsPreferencesFactory.setTestInstance(null);
        StatusBarNotifier statusBarNotifier = new StatusBarNotifier(mContext, null);
        assertEquals(NUMBER, statusBarNotifier.getContentTitle(mLockedContactInfo, mCall));
    }

    public void testGetContentTitle_EmptyPreferredName() {
        ContactCacheEntry contactCacheEntry = new ContactCacheEntry();
        contactCacheEntry.number = NUMBER;
        StatusBarNotifier statusBarNotifier = new StatusBarNotifier(mContext, null);
        assertEquals(NUMBER, statusBarNotifier.getContentTitle(contactCacheEntry, mCall));
    }
}
