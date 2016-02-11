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

import com.android.dialer.compat.UserManagerCompat;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.common.preference.ContactsPreferences;

import org.mockito.Mockito;

@SmallTest
public class ContactsPreferencesFactoryTest extends AndroidTestCase {

    public void testNewContactsPreferences_Unlocked() {
        if (!UserManagerCompat.isUserUnlocked(getContext())) {
            return;
        }
        assertNotNull(ContactsPreferencesFactory.newContactsPreferences(getContext()));
    }

    public void testNewContactsPreferences_Locked() {
        if (UserManagerCompat.isUserUnlocked(getContext())) {
            return;
        }
        assertNull(ContactsPreferencesFactory.newContactsPreferences(getContext()));
    }

    public void testNewContactsPreferences_TestInstance() {
        ContactsPreferences testInstance = Mockito.mock(ContactsPreferences.class);
        ContactsPreferencesFactory.setTestInstance(testInstance);
        // Assert that it returns the same object always
        assertSame(testInstance, ContactsPreferencesFactory.newContactsPreferences(getContext()));
        assertSame(testInstance, ContactsPreferencesFactory.newContactsPreferences(getContext()));
    }
}
