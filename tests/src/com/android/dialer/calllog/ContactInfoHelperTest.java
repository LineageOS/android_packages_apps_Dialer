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

package com.android.dialer.calllog;

import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.test.mocks.ContactsMockContext;
import com.android.contacts.common.test.mocks.MockContentProvider.Query;

import junit.framework.Assert;

@MediumTest
public class ContactInfoHelperTest extends AndroidTestCase {

    private static final String TEST_COUNTRY_ISO = "US";
    private static final String TEST_DISPLAY_NAME = "Display Name";
    private static final String TEST_DISPLAY_NAME_ALTERNATIVE = "Name, Display";
    private static final String[] TEST_DISPLAY_NAME_ALTERNATIVE_ROW = new String[]{
            TEST_DISPLAY_NAME_ALTERNATIVE};
    private static final String TEST_LOOKUP_KEY = "lookupKey";
    private static final String[] TEST_LOOKUP_ROW = new String[]{null, TEST_DISPLAY_NAME,
            null, null, null, null, null, TEST_LOOKUP_KEY, null};

    private Uri displayNameAlternativeUri;
    private ContactsMockContext mContext;
    private ContactInfo mContactInfo;
    private ContactInfoHelper mContactInfoHelper;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        displayNameAlternativeUri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI,
                TEST_LOOKUP_KEY);
        mContext = new ContactsMockContext(getContext());
        mContactInfo = new ContactInfo();
        mContactInfo.name = TEST_DISPLAY_NAME;
        mContactInfo.nameAlternative = TEST_DISPLAY_NAME_ALTERNATIVE;
        mContactInfoHelper = new ContactInfoHelper(mContext, TEST_COUNTRY_ISO);
    }

    public void testLookupContactFromUri_NullUri() {
        Assert.assertNull(mContactInfoHelper.lookupContactFromUri(null, false));
    }

    public void testLookupContactFromUri_NoResults() {
        setUpQueryExpectations(PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI,
                PhoneQuery.getPhoneLookupProjection(PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI));

        Assert.assertEquals(ContactInfo.EMPTY, mContactInfoHelper.lookupContactFromUri(
                PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI, false));
        mContext.verify();
    }

    public void testLookupContactFromUri_NoDisplayNameAlternative() {
        setUpQueryExpectations(PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI,
                PhoneQuery.getPhoneLookupProjection(PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI),
                TEST_LOOKUP_ROW);
        setUpQueryExpectations(displayNameAlternativeUri,
                PhoneQuery.DISPLAY_NAME_ALTERNATIVE_PROJECTION);

        ContactInfo contactInfo = mContactInfoHelper.lookupContactFromUri(
                PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI, false);
        Assert.assertEquals(TEST_DISPLAY_NAME, contactInfo.name);
        Assert.assertNull(contactInfo.nameAlternative);
        mContext.verify();
    }

    public void testLookupContactFromUri_HasDisplayNameAlternative() {
        setUpQueryExpectations(PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI,
                PhoneQuery.getPhoneLookupProjection(PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI),
                TEST_LOOKUP_ROW);
        setUpQueryExpectations(displayNameAlternativeUri,
                PhoneQuery.DISPLAY_NAME_ALTERNATIVE_PROJECTION, TEST_DISPLAY_NAME_ALTERNATIVE_ROW);

        ContactInfo contactInfo = mContactInfoHelper.lookupContactFromUri(
                PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI, false);
        Assert.assertEquals(TEST_DISPLAY_NAME, contactInfo.name);
        Assert.assertEquals(TEST_DISPLAY_NAME_ALTERNATIVE, contactInfo.nameAlternative);
        mContext.verify();
    }

    public void testLookupDisplayNameAlternative_NullLookup() {
        Assert.assertNull(mContactInfoHelper.lookUpDisplayNameAlternative(mContext, null,
                ContactsUtils.USER_TYPE_CURRENT));
    }

    public void testLookupDisplayNameAlternative_NoResults() {
        setUpQueryExpectations(displayNameAlternativeUri,
                PhoneQuery.DISPLAY_NAME_ALTERNATIVE_PROJECTION);
        Assert.assertNull(mContactInfoHelper.lookUpDisplayNameAlternative(mContext,
                TEST_LOOKUP_KEY, ContactsUtils.USER_TYPE_CURRENT));
        mContext.verify();
    }

    public void testLookupDisplayNameAlternative_HasDisplayNameAlternative() {
        setUpQueryExpectations(displayNameAlternativeUri,
                PhoneQuery.DISPLAY_NAME_ALTERNATIVE_PROJECTION, TEST_DISPLAY_NAME_ALTERNATIVE_ROW);
        Assert.assertEquals(TEST_DISPLAY_NAME_ALTERNATIVE,
                mContactInfoHelper.lookUpDisplayNameAlternative(mContext, TEST_LOOKUP_KEY
                , ContactsUtils.USER_TYPE_CURRENT));
        mContext.verify();
    }

    /*
    * Sets up query expectations to return the given row for all queries for the given
    * uri and projection. If row is null, an empty cursor is returned for query calls
    */
    private void setUpQueryExpectations(Uri uri, String[] projection, String...row) {
        Query query = mContext.getContactsProvider().expectQuery(uri)
                .withProjection(projection).withAnySelection().withAnySortOrder();
        if (row == null || row.length == 0) {
            query.returnEmptyCursor();
            return;
        }
        query.returnRow(row);
    }
}
