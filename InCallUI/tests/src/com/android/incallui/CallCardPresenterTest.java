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
public class CallCardPresenterTest extends AndroidTestCase {

    private static final String NAME_PRIMARY = "Full Name";
    private static final String NAME_ALTERNATIVE = "Name, Full";
    private static final String LOCATION = "US";
    private static final String NUMBER = "8006459001";

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

    public void testGetNameForCall_Unlocked() {
        ContactsPreferencesFactory.setTestInstance(mContactsPreferences);
        CallCardPresenter presenter = new CallCardPresenter();
        presenter.init(getContext(), null);

        assertEquals(NAME_PRIMARY, presenter.getNameForCall(mUnlockedContactInfo));
    }

    public void testGetNameForCall_Locked() {
        ContactsPreferencesFactory.setTestInstance(null);
        CallCardPresenter presenter = new CallCardPresenter();
        presenter.init(getContext(), null);

        assertEquals(NUMBER, presenter.getNameForCall(mLockedContactInfo));
    }

    public void testGetNameForCall_EmptyPreferredName() {
        ContactCacheEntry contactInfo = new ContactCacheEntry();
        contactInfo.number = NUMBER;

        ContactsPreferencesFactory.setTestInstance(null);
        CallCardPresenter presenter = new CallCardPresenter();
        presenter.init(getContext(), null);

        assertEquals(NUMBER, presenter.getNameForCall(contactInfo));
    }

    public void testGetNumberForCall_Unlocked() {
        ContactsPreferencesFactory.setTestInstance(mContactsPreferences);
        CallCardPresenter presenter = new CallCardPresenter();
        presenter.init(getContext(), null);

        assertEquals(NUMBER, presenter.getNumberForCall(mUnlockedContactInfo));
    }

    public void testGetNumberForCall_Locked() {
        ContactsPreferencesFactory.setTestInstance(null);
        CallCardPresenter presenter = new CallCardPresenter();
        presenter.init(getContext(), null);

        assertEquals(LOCATION, presenter.getNumberForCall(mLockedContactInfo));
    }

    public void testGetNumberForCall_EmptyPreferredName() {
        ContactCacheEntry contactInfo = new ContactCacheEntry();
        contactInfo.location = LOCATION;

        ContactsPreferencesFactory.setTestInstance(null);
        CallCardPresenter presenter = new CallCardPresenter();
        presenter.init(getContext(), null);

        assertEquals(LOCATION, presenter.getNumberForCall(contactInfo));
    }
}
