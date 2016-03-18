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

package com.android.dialer.calllog;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.provider.CallLog;
import android.test.AndroidTestCase;

import com.android.dialer.R;

/**
 * Unit tests for {@link CallLogNotificationsHelper}.
 */
public class CallLogNotificationsHelperTest extends AndroidTestCase {
    private static final String TEST_COUNTRY_ISO = "US";
    private static final String TEST_VALID_NUMBER = "14125555555";
    private static final String TEST_INVALID_NUMBER = "asdna128937123";
    private static final String TEST_FORMATTED_NUMBER = "1 412-555-5555";
    private static final String TEST_E164_NUMBER = "+14125555555";

    private final ContactInfoHelper mContactInfoHelper = mock(ContactInfoHelper.class);

    private CallLogNotificationsHelper mCallLogNotificationsHelper;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mCallLogNotificationsHelper = new CallLogNotificationsHelper(getContext(),
                null, null, mContactInfoHelper, TEST_COUNTRY_ISO);
    }

    public void testGetContactInfo_ValidNumberValidPresentationValidIso() {
        ContactInfo contactInfo = getContactInfo(
                TEST_VALID_NUMBER, CallLog.Calls.PRESENTATION_UNKNOWN, TEST_COUNTRY_ISO);
        assertEquals(TEST_VALID_NUMBER, contactInfo.number);
        assertEquals(mContext.getResources().getString(R.string.unknown), contactInfo.name);
        assertEquals(TEST_E164_NUMBER, contactInfo.normalizedNumber);
    }

    public void testGetContactInfo_ValidNumberInvalidPresentationValidIso() {
        ContactInfo contactInfo = getContactInfo(TEST_VALID_NUMBER, -1, TEST_COUNTRY_ISO);
        assertEquals(TEST_VALID_NUMBER, contactInfo.number);
        assertEquals(TEST_FORMATTED_NUMBER, contactInfo.name);
        assertEquals(TEST_E164_NUMBER, contactInfo.normalizedNumber);
    }

    public void testGetContactInfo_ValidNumberValidPresentationNullIso() {
        ContactInfo contactInfo = getContactInfo(
                TEST_VALID_NUMBER, CallLog.Calls.PRESENTATION_UNKNOWN, null);
        assertEquals(TEST_VALID_NUMBER, contactInfo.number);
        assertEquals(mContext.getResources().getString(R.string.unknown), contactInfo.name);
        assertEquals(TEST_E164_NUMBER, contactInfo.normalizedNumber);
    }

    public void testGetContactInfo_ValidNumberInvalidPresentationNullIso() {
        ContactInfo contactInfo = getContactInfo(
                TEST_VALID_NUMBER, -1, null);
        assertEquals(TEST_VALID_NUMBER, contactInfo.number);
        assertEquals(TEST_FORMATTED_NUMBER, contactInfo.name);
        assertEquals(TEST_E164_NUMBER, contactInfo.normalizedNumber);
    }

    public void testGetContactInfo_NullNumberValidPresentationValidIso() {
        ContactInfo contactInfo = getContactInfo(
                null, CallLog.Calls.PRESENTATION_UNKNOWN, TEST_COUNTRY_ISO);
        assertEquals("", contactInfo.number);
        assertEquals(mContext.getResources().getString(R.string.unknown), contactInfo.name);
        assertNull(contactInfo.normalizedNumber);
    }

    public void testGetContactInfo_NullNumberInvalidPresentationValidIso() {
        ContactInfo contactInfo = getContactInfo(null, -1, TEST_COUNTRY_ISO);
        assertEquals("", contactInfo.number);
        assertEquals(mContext.getResources().getString(R.string.unknown), contactInfo.name);
        assertNull(contactInfo.normalizedNumber);
    }

    public void testGetContactInfo_NullNumberValidPresentationNullIso() {
        ContactInfo contactInfo = getContactInfo(null, CallLog.Calls.PRESENTATION_RESTRICTED, null);
        assertEquals("", contactInfo.number);
        assertEquals(mContext.getResources().getString(R.string.private_num), contactInfo.name);
        assertNull(contactInfo.normalizedNumber);
    }

    public void testGetContactInfo_NullNumberInValidPresentationNullIso() {
        ContactInfo contactInfo = getContactInfo(null, -1, null);
        assertEquals("", contactInfo.number);
        assertEquals(mContext.getResources().getString(R.string.unknown), contactInfo.name);
        assertNull(contactInfo.normalizedNumber);
    }

    public void testGetContactInfo_InvalidNumberInValidPresentationNullIso() {
        ContactInfo contactInfo = getContactInfo(TEST_INVALID_NUMBER, -1, null);
        assertEquals(TEST_INVALID_NUMBER, contactInfo.name);
        assertEquals(TEST_INVALID_NUMBER, contactInfo.formattedNumber);
        assertEquals(null, contactInfo.normalizedNumber);
    }

    public void testGetContactInfo_NonNullCachedLookup() {
        when(mContactInfoHelper.lookupNumber(anyString(), anyString())).thenReturn(null);
        ContactInfo contactInfo = getContactInfo(TEST_VALID_NUMBER, -1, TEST_COUNTRY_ISO);
        assertEquals(TEST_VALID_NUMBER, contactInfo.number);
        assertEquals(TEST_FORMATTED_NUMBER, contactInfo.formattedNumber);
    }

    public void testGetContactInfo_NullCachedLookup() {
        ContactInfo cachedContactInfo = new ContactInfo();
        cachedContactInfo.number = TEST_VALID_NUMBER;
        cachedContactInfo.formattedNumber = TEST_FORMATTED_NUMBER;
        when(mContactInfoHelper.lookupNumber(anyString(), anyString()))
                .thenReturn(cachedContactInfo);
        ContactInfo contactInfo = getContactInfo(TEST_VALID_NUMBER, -1, TEST_COUNTRY_ISO);
        assertEquals(TEST_VALID_NUMBER, contactInfo.number);
        assertEquals(TEST_FORMATTED_NUMBER, contactInfo.name);
    }

    private ContactInfo getContactInfo(String number, int presentation, String countryIso) {
        return mCallLogNotificationsHelper.getContactInfo(number, presentation, countryIso);
    }
}
