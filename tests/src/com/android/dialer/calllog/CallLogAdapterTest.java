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

import com.google.common.collect.Lists;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.VoicemailContract;
import android.telephony.PhoneNumberUtils;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.text.TextUtils;
import android.view.View;

import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.dialer.contactinfo.ContactInfoCache;
import com.android.dialer.database.VoicemailArchiveContract;
import com.android.dialer.util.AppCompatConstants;
import com.android.dialer.util.TestConstants;
import com.android.dialer.R;

import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * Unit tests for {@link CallLogAdapter}.
 *
 * adb shell am instrument \
 *     -e com.android.dialer.calllog.CallLogAdapterTest \
 *     -w com.android.dialer.tests/android.test.InstrumentationTestRunner
 */
public class CallLogAdapterTest extends AndroidTestCase {
    private static final String EMPTY_STRING = "";
    private static final int NO_VALUE_SET = -1;
    private static final int ARCHIVE_TYPE = -2;

    private static final String TEST_CACHED_NAME_PRIMARY = "Cached Name";
    private static final String TEST_CACHED_NAME_ALTERNATIVE = "Name Cached";
    private static final String CONTACT_NAME_PRIMARY = "Contact Name";
    private static final String CONTACT_NAME_ALTERNATIVE = "Name, Contact";
    private static final String TEST_CACHED_NUMBER_LABEL = "label";
    private static final int TEST_CACHED_NUMBER_TYPE = 1;
    private static final String TEST_COUNTRY_ISO = "US";
    private static final String TEST_DEFAULT_CUSTOM_LABEL = "myLabel";
    private static final Uri TEST_LOOKUP_URI = Uri.parse("content://contacts/2");
    private static final String TEST_ACCOUNT_ID_LABEL = "label";

    private static final String TEST_NUMBER = "12125551000";
    private static final String TEST_NUMBER_1 = "12345678";
    private static final String TEST_NUMBER_2 = "87654321";
    private static final String TEST_NUMBER_3 = "18273645";
    private static final String TEST_POST_DIAL_DIGITS = ";12345";
    private static final String TEST_VIA_NUMBER = "+16505551234";
    private static final String TEST_FORMATTED_NUMBER = "1 212-555-1000";

    // The object under test.
    private TestCallLogAdapter mAdapter;

    private MatrixCursor mCursor;
    private Resources mResources;

    private CallLogListItemViewHolder mViewHolder;
    private final Random mRandom = new Random();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getContext();
        mResources = mContext.getResources();

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

        mAdapter = new TestCallLogAdapter(getContext(), fakeCallFetcher, fakeContactInfoHelper,
                CallLogAdapter.ACTIVITY_TYPE_DIALTACTS);

        // The cursor used in the tests to store the entries to display.
        mCursor = new MatrixCursor(CallLogQuery._PROJECTION);
        mCursor.moveToFirst();

        // The views into which to store the data.
        mViewHolder = CallLogListItemViewHolder.createForTest(getContext());
    }

    @MediumTest
    public void testBindView_NumberOnlyNoCache() {
        createCallLogEntry();

        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        assertNameIs(mViewHolder, TEST_NUMBER);
    }

    @MediumTest
    public void testBindView_PrivateCall() {
        createPrivateCallLogEntry();

        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        assertEquals(Calls.PRESENTATION_RESTRICTED, mViewHolder.numberPresentation);
        assertNull(mViewHolder.primaryActionButtonView.getTag());
        // QC should be disabled since there are no actions to be performed on this
        // call.
        assertFalse(mViewHolder.quickContactView.isEnabled());
    }

    @MediumTest
    public void testBindView_UnknownCall() {
        createUnknownCallLogEntry();

        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        assertEquals(Calls.PRESENTATION_UNKNOWN, mViewHolder.numberPresentation);
        assertNull(mViewHolder.primaryActionButtonView.getTag());
        // QC should be disabled since there are no actions to be performed on this
        // call.
        assertFalse(mViewHolder.quickContactView.isEnabled());
    }

    @MediumTest
    public void testBindView_WithoutQuickContactBadge() {
        createCallLogEntry();

        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        //assertFalse(mViewHolder.quickContactView.isEnabled());
    }

    @MediumTest
    public void testBindView_CallButton() {
        createCallLogEntry();

        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        // The primaryActionView tag is set when the ViewHolder is binded. If it is possible
        // to place a call to the phone number, a call intent will have been created which
        // starts a phone call to the entry's number.
        assertHasCallAction(mViewHolder);
    }

    @MediumTest
    public void testBindView_FirstNameFirstOrder() {
        createCallLogEntry();

        mAdapter.getContactInfoCache()
                .mockGetValue(createContactInfo(CONTACT_NAME_PRIMARY, CONTACT_NAME_ALTERNATIVE));

        setNameDisplayOrder(getContext(), ContactsPreferences.DISPLAY_ORDER_PRIMARY);

        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);
        assertEquals(CONTACT_NAME_PRIMARY, mViewHolder.phoneCallDetailsViews.nameView.getText());
    }

    @MediumTest
    public void testBindView_LastNameFirstOrder() {
        createCallLogEntry();

        mAdapter.getContactInfoCache()
                .mockGetValue(createContactInfo(CONTACT_NAME_PRIMARY, CONTACT_NAME_ALTERNATIVE));

        setNameDisplayOrder(getContext(), ContactsPreferences.DISPLAY_ORDER_ALTERNATIVE);

        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);
        assertEquals(CONTACT_NAME_ALTERNATIVE,
                mViewHolder.phoneCallDetailsViews.nameView.getText());
    }

    @MediumTest
    public void testBindView_NameOrderCorrectOnChange() {
        createCallLogEntry();

        mAdapter.getContactInfoCache()
                .mockGetValue(createContactInfo(CONTACT_NAME_PRIMARY, CONTACT_NAME_ALTERNATIVE));

        Context context = getContext();
        setNameDisplayOrder(context, ContactsPreferences.DISPLAY_ORDER_PRIMARY);

        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);
        assertEquals(CONTACT_NAME_PRIMARY,
                mViewHolder.phoneCallDetailsViews.nameView.getText());

        setNameDisplayOrder(context, ContactsPreferences.DISPLAY_ORDER_ALTERNATIVE);
        mAdapter.onResume();

        mAdapter.onBindViewHolder(mViewHolder, 0);
        assertEquals(CONTACT_NAME_ALTERNATIVE,
                mViewHolder.phoneCallDetailsViews.nameView.getText());
    }

    private void setNameDisplayOrder(Context context, int displayOrder) {
        context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE).edit().putInt(
                ContactsPreferences.DISPLAY_ORDER_KEY, displayOrder).commit();
    }

    @MediumTest
    public void testBindView_CallButtonWithPostDialDigits() {
        createCallLogEntry(TEST_NUMBER, TEST_POST_DIAL_DIGITS, NO_VALUE_SET, NO_VALUE_SET);

        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        if (CompatUtils.isNCompatible()) {
            assertHasCallActionToGivenNumber(mViewHolder, TEST_NUMBER + TEST_POST_DIAL_DIGITS);
        }
    }

    @MediumTest
    public void testBindView_VoicemailUri() {
        createVoicemailCallLogEntry();

        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        assertEquals(Uri.parse(mViewHolder.voicemailUri),
                ContentUris.withAppendedId(VoicemailContract.Voicemails.CONTENT_URI, 0));
        assertNull(mViewHolder.primaryActionButtonView.getTag());
    }

    @MediumTest
    public void testBindView_NumberWithPostDialDigits() {
        createCallLogEntry(TEST_NUMBER, TEST_POST_DIAL_DIGITS, NO_VALUE_SET, NO_VALUE_SET);

        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        if (CompatUtils.isNCompatible()) {
            assertNameIs(mViewHolder, TEST_NUMBER + TEST_POST_DIAL_DIGITS);
        }
    }

    @MediumTest
    public void testBindView_ContactWithPostDialDigits() {
        createCallLogEntry(TEST_NUMBER, TEST_POST_DIAL_DIGITS, NO_VALUE_SET, NO_VALUE_SET);
        mAdapter.injectContactInfoForTest(TEST_NUMBER + TEST_POST_DIAL_DIGITS, TEST_COUNTRY_ISO,
                createContactInfo());

        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        if (CompatUtils.isNCompatible()) {
            assertNameIs(mViewHolder, TEST_CACHED_NAME_PRIMARY);
        }
    }

    @MediumTest
    public void testBindView_CallLogWithViaNumber() {
        createCallLogEntry(TEST_NUMBER, EMPTY_STRING, TEST_VIA_NUMBER, NO_VALUE_SET, NO_VALUE_SET);

        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        // Copy format of Resource String
        String formattedNumber = mResources.getString(R.string.description_via_number,
                TEST_VIA_NUMBER);

        if (CompatUtils.isNCompatible()) {
            assertEquals(formattedNumber,
                    mViewHolder.phoneCallDetailsViews.callAccountLabel.getText());
        }
    }

    @MediumTest
    public void testBindView_CallLogWithoutViaNumber() {
        createCallLogEntry(TEST_NUMBER, EMPTY_STRING, EMPTY_STRING, NO_VALUE_SET, NO_VALUE_SET);

        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        if (CompatUtils.isNCompatible()) {
            assertEquals(View.GONE,
                    mViewHolder.phoneCallDetailsViews.callAccountLabel.getVisibility());
        }
    }

    @MediumTest
    public void testPresentationAfterRebindingViewHolders() {
        final int increment = 10;
        final int size = increment * 4;

        // Instantiate list of ViewHolders.
        CallLogListItemViewHolder[] holders = new CallLogListItemViewHolder[size];
        for (int i = 0; i < size; i++) {
            holders[i] = CallLogListItemViewHolder.createForTest(getContext());
        }

        // Add first set of entries to the cursor.
        for (int i = 0; i < increment; i++) {
            createCallLogEntry();
            createPrivateCallLogEntry();
            createCallLogEntry();
            createUnknownCallLogEntry();
        }

        mAdapter.changeCursor(mCursor);

        // Verify correct appearance for presentation.
        for (int i = 0; i < size; i++) {
            mAdapter.onBindViewHolder(holders[i], i);
            if (holders[i].numberPresentation == Calls.PRESENTATION_ALLOWED) {
                assertHasCallAction(holders[i]);
            } else {
                assertNull(holders[i].primaryActionButtonView.getTag());
                assertEquals(holders[i].number, EMPTY_STRING);
            }
        }

        // Append the rest of the entries to the cursor. Keep the first set of ViewHolders
        // so they are updated and not buitl from scratch. This checks for bugs which may
        // be evident only after the call log is updated.
        for (int i = 0; i < increment; i++) {
            createPrivateCallLogEntry();
            createCallLogEntry();
            createUnknownCallLogEntry();
            createCallLogEntry();
        }

        mCursor.move(size);

        // Verify correct appearnce for presentation.
        for (int i = 0; i < size; i++) {
            mAdapter.onBindViewHolder(holders[i], i + size);
            if (holders[i].numberPresentation == Calls.PRESENTATION_ALLOWED) {
                assertHasCallAction(holders[i]);
            } else {
                assertNull(holders[i].primaryActionButtonView.getTag());
                assertEquals(holders[i].number, EMPTY_STRING);
            }
        }
    }

   @MediumTest
   public void testBindView_NoCallLogCacheNorMemoryCache_EnqueueRequest() {
       createCallLogEntry();

       // Bind the views of a single row.
       mAdapter.changeCursor(mCursor);
       mAdapter.onBindViewHolder(mViewHolder, 0);

       // There is one request for contact details.
       assertEquals(1, mAdapter.getContactInfoCache().requests.size());

       TestContactInfoCache.Request request = mAdapter.getContactInfoCache().requests.get(0);
       // It is for the number we need to show.
       assertEquals(TEST_NUMBER, request.number);
       // It has the right country.
       assertEquals(TEST_COUNTRY_ISO, request.countryIso);
       // Since there is nothing in the cache, it is an immediate request.
       assertTrue("should be immediate", request.immediate);
   }

   @MediumTest
   public void testBindView_CallLogCacheButNoMemoryCache_EnqueueRequest() {
       createCallLogEntryWithCachedValues(false);

       // Bind the views of a single row.
       mAdapter.changeCursor(mCursor);
       mAdapter.onBindViewHolder(mViewHolder, 0);

        // There is one request for contact details.
        assertEquals(1, mAdapter.getContactInfoCache().requests.size());

        TestContactInfoCache.Request request = mAdapter.getContactInfoCache().requests.get(0);

        // The values passed to the request, match the ones in the call log cache.
        assertEquals(TEST_CACHED_NAME_PRIMARY, request.callLogInfo.name);
        assertEquals(TEST_CACHED_NUMBER_TYPE, request.callLogInfo.type);
        assertEquals(TEST_CACHED_NUMBER_LABEL, request.callLogInfo.label);
    }

    @MediumTest
    public void testBindView_NoCallLogButMemoryCache_EnqueueRequest() {
        createCallLogEntry();
        mAdapter.injectContactInfoForTest(TEST_NUMBER, TEST_COUNTRY_ISO, createContactInfo());

        // Bind the views of a single row.
        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        // There is one request for contact details.
        assertEquals(1, mAdapter.getContactInfoCache().requests.size());

        TestContactInfoCache.Request request = mAdapter.getContactInfoCache().requests.get(0);
        // Since there is something in the cache, it is not an immediate request.
        assertFalse("should not be immediate", request.immediate);
    }

    @MediumTest
    public void testBindView_BothCallLogAndMemoryCache_NoEnqueueRequest() {
        createCallLogEntryWithCachedValues(true);

        // Bind the views of a single row.
        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        // Cache and call log are up-to-date: no need to request update.
        assertEquals(0, mAdapter.getContactInfoCache().requests.size());
    }

    @MediumTest
    public void testBindView_MismatchBetweenCallLogAndMemoryCache_EnqueueRequest() {
        createCallLogEntryWithCachedValues(false);

        // Contact info contains a different name.
        ContactInfo info = createContactInfo();
        info.name = "new name";
        mAdapter.injectContactInfoForTest(TEST_NUMBER, TEST_COUNTRY_ISO, info);

        // Bind the views of a single row.
        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        // There is one request for contact details.
        assertEquals(1, mAdapter.getContactInfoCache().requests.size());

        TestContactInfoCache.Request request = mAdapter.getContactInfoCache().requests.get(0);
        // Since there is something in the cache, it is not an immediate request.
        assertFalse("should not be immediate", request.immediate);
    }

    @MediumTest
    public void testBindView_WithCachedName() {
        createCallLogEntryWithCachedValues(
                "John Doe",
                Phone.TYPE_HOME,
                TEST_CACHED_NUMBER_LABEL);

        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        assertNameIs(mViewHolder, "John Doe");
        assertLabel(mViewHolder, TEST_FORMATTED_NUMBER, getTypeLabel(Phone.TYPE_HOME));
    }

    @MediumTest
    public void testBindView_UriNumber() {
        createCallLogEntryWithCachedValues(
                "sip:johndoe@gmail.com",
                AppCompatConstants.CALLS_INCOMING_TYPE,
                "John Doe",
                Phone.TYPE_HOME,
                TEST_DEFAULT_CUSTOM_LABEL,
                EMPTY_STRING,
                false /* inject */);

        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        assertNameIs(mViewHolder, "John Doe");
        assertLabel(mViewHolder, "sip:johndoe@gmail.com", "sip:johndoe@gmail.com");
    }

    @MediumTest
    public void testBindView_HomeLabel() {
        createCallLogEntryWithCachedValues(
                "John Doe",
                Phone.TYPE_HOME,
                TEST_CACHED_NUMBER_LABEL);

        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        assertNameIs(mViewHolder, "John Doe");
        assertLabel(mViewHolder, TEST_FORMATTED_NUMBER, getTypeLabel(Phone.TYPE_HOME));
    }

    @MediumTest
    public void testBindView_WorkLabel() {
        createCallLogEntryWithCachedValues(
                "John Doe",
                Phone.TYPE_WORK,
                TEST_CACHED_NUMBER_LABEL);

        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        assertNameIs(mViewHolder, "John Doe");
        assertLabel(mViewHolder, TEST_FORMATTED_NUMBER, getTypeLabel(Phone.TYPE_WORK));
    }

    @MediumTest
    public void testBindView_CustomLabel() {
        createCallLogEntryWithCachedValues(
                "John Doe",
                Phone.TYPE_CUSTOM,
                TEST_DEFAULT_CUSTOM_LABEL);

        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        assertNameIs(mViewHolder, "John Doe");
        assertLabel(mViewHolder, TEST_FORMATTED_NUMBER, TEST_DEFAULT_CUSTOM_LABEL);
    }

    @MediumTest
    public void testBindView_NumberOnlyDbCachedFormattedNumber() {
        createCallLogEntryWithCachedValues(
                TEST_NUMBER,
                AppCompatConstants.CALLS_INCOMING_TYPE,
                EMPTY_STRING,
                TEST_CACHED_NUMBER_TYPE,
                TEST_CACHED_NUMBER_LABEL,
                TEST_FORMATTED_NUMBER,
                false /* inject */);

        mAdapter.changeCursor(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        assertNameIs(mViewHolder, TEST_FORMATTED_NUMBER);
    }

    @MediumTest
    public void testBindVoicemailPromoCard() {
        createCallLogEntry(TEST_NUMBER_1);
        createCallLogEntry(TEST_NUMBER_1);
        createCallLogEntry(TEST_NUMBER_2);
        createCallLogEntry(TEST_NUMBER_2);
        createCallLogEntry(TEST_NUMBER_2);
        createCallLogEntry(TEST_NUMBER_3);

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

    public void testVoicemailArchive() {
        setUpArchiveAdapter();
        createVoicemailArchiveCallLogEntry();

        mAdapter.changeCursorVoicemail(mCursor);
        mAdapter.onBindViewHolder(mViewHolder, 0);

        assertEquals(Uri.parse(mViewHolder.voicemailUri),
                ContentUris.withAppendedId(
                        VoicemailArchiveContract.VoicemailArchive.CONTENT_URI, 0));
        assertNull(mViewHolder.primaryActionButtonView.getTag());
    }

    private void createCallLogEntry() {
        createCallLogEntry(TEST_NUMBER);
    }

    private void createCallLogEntry(String testNumber) {
        createCallLogEntry(testNumber, EMPTY_STRING, NO_VALUE_SET, NO_VALUE_SET);
    }

    private void createPrivateCallLogEntry() {
        createCallLogEntry(
                EMPTY_STRING,
                EMPTY_STRING,
                Calls.PRESENTATION_RESTRICTED,
                AppCompatConstants.CALLS_INCOMING_TYPE);
    }

    private void createUnknownCallLogEntry() {
        createCallLogEntry(
                EMPTY_STRING,
                EMPTY_STRING,
                Calls.PRESENTATION_UNKNOWN,
                AppCompatConstants.CALLS_INCOMING_TYPE);
    }

    private void createVoicemailCallLogEntry() {
        createCallLogEntry(TEST_NUMBER, EMPTY_STRING, NO_VALUE_SET, Calls.VOICEMAIL_TYPE);
    }

    private void createVoicemailArchiveCallLogEntry() {
        createCallLogEntry(TEST_NUMBER, EMPTY_STRING, NO_VALUE_SET, ARCHIVE_TYPE);
    }

    private void createCallLogEntry(String number, String postDialDigits, int presentation,
            int type) {
        Object[] values = getValues(number, postDialDigits, presentation, type);
        mCursor.addRow(values);
    }

    private void createCallLogEntry(String number, String postDialDigits, String viaNumber,
            int presentation, int type) {
        Object[] values = getValues(number, postDialDigits, viaNumber, presentation, type);
        mCursor.addRow(values);
    }

    private void createCallLogEntryWithCachedValues(boolean inject) {
        createCallLogEntryWithCachedValues(
                TEST_NUMBER,
                NO_VALUE_SET,
                TEST_CACHED_NAME_PRIMARY,
                TEST_CACHED_NUMBER_TYPE,
                TEST_CACHED_NUMBER_LABEL,
                EMPTY_STRING,
                inject);
    }

    private void createCallLogEntryWithCachedValues(
            String cachedName, int cachedNumberType, String cachedNumberLabel) {
        createCallLogEntryWithCachedValues(
                TEST_NUMBER,
                NO_VALUE_SET,
                cachedName,
                cachedNumberType,
                cachedNumberLabel,
                EMPTY_STRING,
                false /* inject */);
    }

    /**
     * Inserts a new call log entry
     *
     * It includes the values for the cached contact associated with the number.
     *
     * @param number The phone number.
     * @param type Valid value of {@code Calls.TYPE}.
     * @param cachedName The name of the contact with this number
     * @param cachedNumberType The type of the number, from the contact with this number.
     * @param cachedNumberLabel The label of the number, from the contact with this number.
     * @param cachedFormattedNumber The formatted number, from the contact with this number.
     * @param inject Whether to inject the contact info into the adapter's ContactInfoCache.
    */
    private void createCallLogEntryWithCachedValues(
            String number,
            int type,
            String cachedName,
            int cachedNumberType,
            String cachedNumberLabel,
            String cachedFormattedNumber,
            boolean inject) {
        Object[] values = getValues(number, EMPTY_STRING, NO_VALUE_SET, type);
        values[CallLogQuery.CACHED_NAME] = cachedName;
        values[CallLogQuery.CACHED_NUMBER_TYPE] = cachedNumberType;
        values[CallLogQuery.CACHED_NUMBER_LABEL] = cachedNumberLabel;
        values[CallLogQuery.CACHED_FORMATTED_NUMBER] = cachedFormattedNumber;

        mCursor.addRow(values);

        if (inject) {
            ContactInfo contactInfo =
                    createContactInfo(cachedName, cachedName, cachedNumberType, cachedNumberLabel);
            mAdapter.injectContactInfoForTest(number, TEST_COUNTRY_ISO, contactInfo);
        }
    }

    /**
     * @param number The phone number.
     * @param postDialDigits The post dial digits dialed (if any)
     * @param presentation Number representing display rules for "allowed",
     *               "payphone", "restricted", or "unknown".
     * @param type The type of the call (outgoing/ingoing)
     */
    private Object[] getValues(
            String number,
            String postDialDigits,
            int presentation,
            int type) {
        return getValues(number, postDialDigits, "", presentation, type);
    }

    /**
     * @param number The phone number.
     * @param postDialDigits The post dial digits dialed (if any)
     * @param viaNumber The secondary number that the call was placed via
     * @param presentation Number representing display rules for "allowed",
     *               "payphone", "restricted", or "unknown".
     * @param type The type of the call (outgoing/ingoing)
     */
    private Object[] getValues(
            String number,
            String postDialDigits,
            String viaNumber,
            int presentation,
            int type) {
        Object[] values = CallLogQueryTestUtils.createTestValues();

        values[CallLogQuery.ID] = mCursor.getCount();
        values[CallLogQuery.COUNTRY_ISO] = TEST_COUNTRY_ISO;
        values[CallLogQuery.DATE] = new Date().getTime();
        values[CallLogQuery.DURATION] = mRandom.nextInt(10 * 60);

        if (!TextUtils.isEmpty(number)) {
            values[CallLogQuery.NUMBER] = number;
        }
        if (!TextUtils.isEmpty(postDialDigits) && CompatUtils.isNCompatible()) {
            values[CallLogQuery.POST_DIAL_DIGITS] = postDialDigits;
        }
        if (!TextUtils.isEmpty(viaNumber) && CompatUtils.isNCompatible()) {
            values[CallLogQuery.VIA_NUMBER] = viaNumber;
        }
        if (presentation != NO_VALUE_SET) {
            values[CallLogQuery.NUMBER_PRESENTATION] = presentation;
        }
        if (type != NO_VALUE_SET) {
            values[CallLogQuery.CALL_TYPE] = type;
        }
        if (type == AppCompatConstants.CALLS_VOICEMAIL_TYPE) {
            values[CallLogQuery.VOICEMAIL_URI] = ContentUris.withAppendedId(
                    VoicemailContract.Voicemails.CONTENT_URI, mCursor.getCount());
        }
        if (type == ARCHIVE_TYPE) {
            values[CallLogQuery.VOICEMAIL_URI] = ContentUris.withAppendedId(
                    VoicemailArchiveContract.VoicemailArchive.CONTENT_URI, mCursor.getCount());
        }

        return values;
    }

    private ContactInfo createContactInfo() {
        return createContactInfo(
                TEST_CACHED_NAME_PRIMARY,
                TEST_CACHED_NAME_ALTERNATIVE);
    }

    private ContactInfo createContactInfo(String namePrimary, String nameAlternative) {
        return createContactInfo(
                namePrimary,
                nameAlternative,
                TEST_CACHED_NUMBER_TYPE,
                TEST_CACHED_NUMBER_LABEL);
    }

    /** Returns a contact info with default values. */
    private ContactInfo createContactInfo(String namePrimary, String nameAlternative, int type, String label) {
        ContactInfo info = new ContactInfo();
        info.number = TEST_NUMBER;
        info.name = namePrimary;
        info.nameAlternative = nameAlternative;
        info.type = type;
        info.label = label;
        info.formattedNumber = TEST_FORMATTED_NUMBER;
        info.normalizedNumber = TEST_NUMBER;
        info.lookupUri = TEST_LOOKUP_URI;
        return info;
    }

    // Asserts that the name text view is shown and contains the given text.
    private void assertNameIs(CallLogListItemViewHolder viewHolder, String name) {
        assertEquals(View.VISIBLE, viewHolder.phoneCallDetailsViews.nameView.getVisibility());
        assertEquals(name, viewHolder.phoneCallDetailsViews.nameView.getText());
    }

    // Asserts that the label text view contains the given text.
    private void assertLabel(
            CallLogListItemViewHolder viewHolder, CharSequence number, CharSequence label) {
        if (label != null) {
            assertTrue(viewHolder.phoneCallDetailsViews.callLocationAndDate.getText()
                    .toString().contains(label));
        }
    }

    private void assertHasCallAction(CallLogListItemViewHolder viewHolder) {
        assertHasCallActionToGivenNumber(viewHolder, TEST_NUMBER);
    }

    private void assertHasCallActionToGivenNumber(CallLogListItemViewHolder viewHolder,
            String number) {
        IntentProvider intentProvider =
                (IntentProvider) viewHolder.primaryActionButtonView.getTag();
        Intent intent = intentProvider.getIntent(getContext());
        assertEquals(TestConstants.CALL_INTENT_ACTION, intent.getAction());
        assertEquals(Uri.parse("tel:" + Uri.encode(number)), intent.getData());
    }

    /** Returns the label associated with a given phone type. */
    private CharSequence getTypeLabel(int phoneType) {
        return Phone.getTypeLabel(getContext().getResources(), phoneType, "");
    }

    private void setUpArchiveAdapter() {
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

        mAdapter = new TestCallLogAdapter(getContext(), fakeCallFetcher, fakeContactInfoHelper,
                CallLogAdapter.ACTIVITY_TYPE_ARCHIVE);
    }

    /// Subclass of {@link CallLogAdapter} used in tests to intercept certain calls.
    private static final class TestCallLogAdapter extends CallLogAdapter {
        public TestCallLogAdapter(Context context, CallFetcher callFetcher,
                ContactInfoHelper contactInfoHelper, int mActivity) {
            super(context, callFetcher, contactInfoHelper, null,
                    mActivity);
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

        /**
         * Dummy contactInfo to return in the even that the getValue method has been mocked
         */
        private ContactInfo mContactInfo;

        public TestContactInfoCache(
                ContactInfoHelper contactInfoHelper, OnContactInfoChangedListener listener) {
            super(contactInfoHelper, listener);
        }

        /**
         * Sets the given value to be returned by all calls to
         * {@link #getValue(String, String, ContactInfo)}
         *
         * @param contactInfo the contactInfo
         */
        public void mockGetValue(ContactInfo contactInfo) {
            this.mContactInfo = contactInfo;
        }

        @Override
        public ContactInfo getValue(String number, String countryIso,
                ContactInfo cachedContactInfo) {
            if (mContactInfo != null) {
                return mContactInfo;
            }
            return super.getValue(number, countryIso, cachedContactInfo);
        }

        @Override
        protected void enqueueRequest(String number, String countryIso, ContactInfo callLogInfo,
                boolean immediate) {
            requests.add(new Request(number, countryIso, callLogInfo, immediate));
        }
    }
}
