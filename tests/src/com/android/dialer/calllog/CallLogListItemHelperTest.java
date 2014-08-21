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
import android.content.res.Resources;
import android.provider.CallLog.Calls;
import android.test.AndroidTestCase;
import android.view.View;

import com.android.dialer.PhoneCallDetails;
import com.android.dialer.PhoneCallDetailsHelper;
import com.android.dialer.R;

/**
 * Unit tests for {@link CallLogListItemHelper}.
 */
public class CallLogListItemHelperTest extends AndroidTestCase {
    /** A test phone number for phone calls. */
    private static final String TEST_NUMBER = "14125555555";
    /** The formatted version of {@link #TEST_NUMBER}. */
    private static final String TEST_FORMATTED_NUMBER = "1-412-255-5555";
    /** A test date value for phone calls. */
    private static final long TEST_DATE = 1300000000;
    /** A test duration value for phone calls. */
    private static final long TEST_DURATION = 62300;
    /** A test voicemail number. */
    private static final String TEST_VOICEMAIL_NUMBER = "123";
    /** The country ISO name used in the tests. */
    private static final String TEST_COUNTRY_ISO = "US";
    /** The geocoded location used in the tests. */
    private static final String TEST_GEOCODE = "United States";

    /** The object under test. */
    private CallLogListItemHelper mHelper;

    /** The views used in the tests. */
    private CallLogListItemViews mViews;
    private PhoneNumberDisplayHelper mPhoneNumberHelper;
    private PhoneNumberDisplayHelper mPhoneNumberDisplayHelper;

    private Resources mResources;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context context = getContext();
        mResources = context.getResources();
        CallTypeHelper callTypeHelper = new CallTypeHelper(mResources);
        final TestPhoneNumberUtilsWrapper phoneUtils = new TestPhoneNumberUtilsWrapper(
                TEST_VOICEMAIL_NUMBER);
        PhoneCallDetailsHelper phoneCallDetailsHelper = new PhoneCallDetailsHelper(
                mResources, callTypeHelper, phoneUtils);
        mPhoneNumberDisplayHelper = new PhoneNumberDisplayHelper(mResources);
        mHelper = new CallLogListItemHelper(phoneCallDetailsHelper, mPhoneNumberDisplayHelper,
                mResources);
        mViews = CallLogListItemViews.createForTest(context);
    }

    @Override
    protected void tearDown() throws Exception {
        mHelper = null;
        mViews = null;
        super.tearDown();
    }

    public void testSetPhoneCallDetails() {
        setPhoneCallDetailsWithNumber("12125551234", Calls.PRESENTATION_ALLOWED,
                "1-212-555-1234");
        assertEquals(View.VISIBLE, mViews.callBackButtonView.getVisibility());
    }

    public void testSetPhoneCallDetails_Unknown() {
        setPhoneCallDetailsWithNumber("", Calls.PRESENTATION_UNKNOWN, "");
        assertNoCallIntent();
    }

    public void testSetPhoneCallDetails_Private() {
        setPhoneCallDetailsWithNumber("", Calls.PRESENTATION_RESTRICTED, "");
        assertNoCallIntent();
    }

    public void testSetPhoneCallDetails_Payphone() {
        setPhoneCallDetailsWithNumber("", Calls.PRESENTATION_PAYPHONE, "");
        assertNoCallIntent();
    }

    public void testSetPhoneCallDetails_VoicemailNumber() {
        setPhoneCallDetailsWithNumber(TEST_VOICEMAIL_NUMBER,
                Calls.PRESENTATION_ALLOWED, TEST_VOICEMAIL_NUMBER);
        assertEquals(View.VISIBLE, mViews.voicemailButtonView.getVisibility());
    }

    public void testSetPhoneCallDetails_ReadVoicemail() {
        setPhoneCallDetailsWithTypes(Calls.VOICEMAIL_TYPE);
        assertEquals(View.VISIBLE, mViews.voicemailButtonView.getVisibility());
    }

    public void testSetPhoneCallDetails_UnreadVoicemail() {
        setUnreadPhoneCallDetailsWithTypes(Calls.VOICEMAIL_TYPE);
        assertEquals(View.VISIBLE, mViews.voicemailButtonView.getVisibility());
    }

    public void testSetPhoneCallDetails_VoicemailFromUnknown() {
        setPhoneCallDetailsWithNumberAndType("", Calls.PRESENTATION_UNKNOWN,
                "", Calls.VOICEMAIL_TYPE);
        assertEquals(View.VISIBLE, mViews.voicemailButtonView.getVisibility());
    }

    /**
     * Test getCallDescriptionID method used to get the accessibility description for calls.
     * Test case where an answered unknown call is received.
     */
    public void testGetCallDescriptionID_UnknownAnswered() {
        PhoneCallDetails details = new PhoneCallDetails("", Calls.PRESENTATION_UNKNOWN, "",
                TEST_COUNTRY_ISO, TEST_GEOCODE,
                new int[]{Calls.INCOMING_TYPE}, TEST_DATE, TEST_DURATION);
        assertEquals(R.string.description_incoming_answered_call,
                mHelper.getCallDescriptionStringID(details));
    }

    /**
     * Test getCallDescriptionID method used to get the accessibility description for calls.
     * Test case where an missed unknown call is received.
     */
    public void testGetCallDescriptionID_UnknownMissed() {
        PhoneCallDetails details = new PhoneCallDetails("", Calls.PRESENTATION_UNKNOWN, "",
                TEST_COUNTRY_ISO, TEST_GEOCODE,
                new int[]{Calls.MISSED_TYPE}, TEST_DATE, TEST_DURATION);
        assertEquals(R.string.description_incoming_missed_call,
                mHelper.getCallDescriptionStringID(details));
    }

    /**
     * Test getCallDescriptionID method used to get the accessibility description for calls.
     * Test case where an missed unknown call is received and a voicemail was left.
     */
    public void testGetCallDescriptionID_UnknownVoicemail() {
        PhoneCallDetails details = new PhoneCallDetails("", Calls.PRESENTATION_UNKNOWN, "",
                TEST_COUNTRY_ISO, TEST_GEOCODE,
                new int[]{Calls.VOICEMAIL_TYPE}, TEST_DATE, TEST_DURATION);
        assertEquals(R.string.description_incoming_missed_call,
                mHelper.getCallDescriptionStringID(details));
    }

    /**
     * Test getCallDescriptionID method used to get the accessibility description for calls.
     * Test case where an answered call from a known caller is received.
     */
    public void testGetCallDescriptionID_KnownAnswered() {
        PhoneCallDetails details = new PhoneCallDetails(TEST_NUMBER, Calls.PRESENTATION_ALLOWED,
                TEST_FORMATTED_NUMBER,
                TEST_COUNTRY_ISO, TEST_GEOCODE,
                new int[]{Calls.INCOMING_TYPE}, TEST_DATE, TEST_DURATION);
        assertEquals(R.string.description_incoming_answered_call,
                mHelper.getCallDescriptionStringID(details));
    }

    /**
     * Test getCallDescriptionID method used to get the accessibility description for calls.
     * Test case where a missed call from a known caller is received.
     */
    public void testGetCallDescriptionID_KnownMissed() {
        PhoneCallDetails details = new PhoneCallDetails(TEST_NUMBER, Calls.PRESENTATION_ALLOWED,
                TEST_FORMATTED_NUMBER,
                TEST_COUNTRY_ISO, TEST_GEOCODE,
                new int[]{Calls.MISSED_TYPE}, TEST_DATE, TEST_DURATION);
        assertEquals(R.string.description_incoming_missed_call,
                mHelper.getCallDescriptionStringID(details));
    }

    /**
     * Test getCallDescriptionID method used to get the accessibility description for calls.
     * Test case where a missed call from a known caller is received and a voicemail was left.
     */
    public void testGetCallDescriptionID_KnownVoicemail() {
        PhoneCallDetails details = new PhoneCallDetails(TEST_NUMBER, Calls.PRESENTATION_ALLOWED,
                TEST_FORMATTED_NUMBER,
                TEST_COUNTRY_ISO, TEST_GEOCODE,
                new int[]{Calls.VOICEMAIL_TYPE}, TEST_DATE, TEST_DURATION);
        assertEquals(R.string.description_incoming_missed_call,
                mHelper.getCallDescriptionStringID(details));
    }

    /**
     * Test getCallDescriptionID method used to get the accessibility description for calls.
     * Test case where an outgoing call is made to a known number and there is a history of
     * only a single call for this caller.
     */
    public void testGetCallDescriptionID_OutgoingSingle() {
        PhoneCallDetails details = new PhoneCallDetails(TEST_NUMBER, Calls.PRESENTATION_ALLOWED,
                TEST_FORMATTED_NUMBER,
                TEST_COUNTRY_ISO, TEST_GEOCODE,
                new int[]{Calls.OUTGOING_TYPE}, TEST_DATE, TEST_DURATION);
        assertEquals(R.string.description_outgoing_call,
                mHelper.getCallDescriptionStringID(details));
    }

    /**
     * Test getCallDescriptionID method used to get the accessibility description for calls.
     * Test case where an outgoing call is made to a known number and there is a history of
     * many calls for this caller.
     */
    public void testGetCallDescriptionID_OutgoingMultiple() {
        PhoneCallDetails details = new PhoneCallDetails(TEST_NUMBER, Calls.PRESENTATION_ALLOWED,
                TEST_FORMATTED_NUMBER,
                TEST_COUNTRY_ISO, TEST_GEOCODE,
                new int[]{Calls.OUTGOING_TYPE, Calls.OUTGOING_TYPE}, TEST_DATE, TEST_DURATION);
        assertEquals(R.string.description_outgoing_call,
                mHelper.getCallDescriptionStringID(details));
    }

    /**
     * Test getCallDescription method used to get the accessibility description for calls.
     * For outgoing calls, we should NOT have "New Voicemail" in the description.
     */
    public void testGetCallDescription_NoVoicemailOutgoing() {
        PhoneCallDetails details = new PhoneCallDetails(TEST_NUMBER, Calls.PRESENTATION_ALLOWED,
                TEST_FORMATTED_NUMBER,
                TEST_COUNTRY_ISO, TEST_GEOCODE,
                new int[]{Calls.OUTGOING_TYPE, Calls.OUTGOING_TYPE}, TEST_DATE, TEST_DURATION);
        CharSequence description = mHelper.getCallDescription(getContext(), details);
        assertFalse(description.toString()
                .contains(this.mResources.getString(R.string.description_new_voicemail)));
    }

    /**
     * Test getCallDescription method used to get the accessibility description for calls.
     * For regular incoming calls, we should NOT have "New Voicemail" in the description.
     */
    public void testGetCallDescription_NoVoicemailIncoming() {
        PhoneCallDetails details = new PhoneCallDetails(TEST_NUMBER, Calls.PRESENTATION_ALLOWED,
                TEST_FORMATTED_NUMBER,
                TEST_COUNTRY_ISO, TEST_GEOCODE,
                new int[]{Calls.INCOMING_TYPE, Calls.OUTGOING_TYPE}, TEST_DATE, TEST_DURATION);
        CharSequence description = mHelper.getCallDescription(getContext(), details);
        assertFalse(description.toString()
                .contains(this.mResources.getString(R.string.description_new_voicemail)));
    }

    /**
     * Test getCallDescription method used to get the accessibility description for calls.
     * For regular missed calls, we should NOT have "New Voicemail" in the description.
     */
    public void testGetCallDescription_NoVoicemailMissed() {
        PhoneCallDetails details = new PhoneCallDetails(TEST_NUMBER, Calls.PRESENTATION_ALLOWED,
                TEST_FORMATTED_NUMBER,
                TEST_COUNTRY_ISO, TEST_GEOCODE,
                new int[]{Calls.MISSED_TYPE, Calls.OUTGOING_TYPE}, TEST_DATE, TEST_DURATION);
        CharSequence description = mHelper.getCallDescription(getContext(), details);
        assertFalse(description.toString()
                .contains(this.mResources.getString(R.string.description_new_voicemail)));
    }

    /**
     * Test getCallDescription method used to get the accessibility description for calls.
     * For voicemail calls, we should have "New Voicemail" in the description.
     */
    public void testGetCallDescription_Voicemail() {
        PhoneCallDetails details = new PhoneCallDetails(TEST_NUMBER, Calls.PRESENTATION_ALLOWED,
                TEST_FORMATTED_NUMBER,
                TEST_COUNTRY_ISO, TEST_GEOCODE,
                new int[]{Calls.VOICEMAIL_TYPE, Calls.OUTGOING_TYPE}, TEST_DATE, TEST_DURATION);
        CharSequence description = mHelper.getCallDescription(getContext(), details);
        assertTrue(description.toString()
                .contains(this.mResources.getString(R.string.description_new_voicemail)));
    }

    /**
     * Test getCallDescription method used to get the accessibility description for calls.
     * Test that the "X calls" message is not present if there is only a single call.
     */
    public void testGetCallDescription_NumCallsSingle() {
        PhoneCallDetails details = new PhoneCallDetails(TEST_NUMBER, Calls.PRESENTATION_ALLOWED,
                TEST_FORMATTED_NUMBER,
                TEST_COUNTRY_ISO, TEST_GEOCODE,
                new int[]{Calls.VOICEMAIL_TYPE}, TEST_DATE, TEST_DURATION);
        CharSequence description = mHelper.getCallDescription(getContext(), details);

        // Rather than hard coding the "X calls" string message, we'll generate it with an empty
        // number of calls, and trim the resulting string.  This gets us just the word "calls",
        // and ensures any trivial changes to that string resource won't unnecessarily break
        // the unit test.
        assertFalse(description.toString()
                .contains(this.mResources.getString(R.string.description_num_calls, "").trim()));
    }

    /**
     * Test getCallDescription method used to get the accessibility description for calls.
     * Test that the "X calls" message is present if there are many calls.
     */
    public void testGetCallDescription_NumCallsMultiple() {
        PhoneCallDetails details = new PhoneCallDetails(TEST_NUMBER, Calls.PRESENTATION_ALLOWED,
                TEST_FORMATTED_NUMBER,
                TEST_COUNTRY_ISO, TEST_GEOCODE,
                new int[]{Calls.VOICEMAIL_TYPE, Calls.INCOMING_TYPE}, TEST_DATE, TEST_DURATION);
        CharSequence description = mHelper.getCallDescription(getContext(), details);
        assertTrue(description.toString()
                .contains(this.mResources.getString(R.string.description_num_calls, 2)));
    }

    /**
     * Test getCallDescription method used to get the accessibility description for calls.
     * Test that the "Video call." message is present if the call had video capability.
     */
    public void testGetCallDescription_Video() {
        PhoneCallDetails details = new PhoneCallDetails(TEST_NUMBER, Calls.PRESENTATION_ALLOWED,
                TEST_FORMATTED_NUMBER,
                TEST_COUNTRY_ISO, TEST_GEOCODE,
                new int[]{Calls.INCOMING_TYPE, Calls.INCOMING_TYPE}, TEST_DATE, TEST_DURATION,
                null, null, Calls.FEATURES_VIDEO, null, null);

        CharSequence description = mHelper.getCallDescription(getContext(), details);
        assertTrue(description.toString()
                .contains(this.mResources.getString(R.string.description_video_call, 2)));
    }

    /** Asserts that the primary action view does not have a call intent. */
    private void assertNoCallIntent() {
        Object intentProvider = (IntentProvider)mViews.primaryActionView.getTag();
        // The intent provider should be null as there is no ability to make a call.
        assertNull(intentProvider);
    }

    /** Sets the details of a phone call using the specified phone number. */
    private void setPhoneCallDetailsWithNumber(String number,
            int presentation, String formattedNumber) {
        setPhoneCallDetailsWithNumberAndType(number, presentation,
                formattedNumber, Calls.INCOMING_TYPE);
    }

    /** Sets the details of a phone call using the specified phone number. */
    private void setPhoneCallDetailsWithNumberAndType(String number,
            int presentation, String formattedNumber, int callType) {
        mHelper.setPhoneCallDetails(getContext(), mViews,
                new PhoneCallDetails(number, presentation, formattedNumber,
                        TEST_COUNTRY_ISO, TEST_GEOCODE,
                        new int[]{ callType }, TEST_DATE, TEST_DURATION)
        );
    }

    /** Sets the details of a phone call using the specified call type. */
    private void setPhoneCallDetailsWithTypes(int... types) {
        mHelper.setPhoneCallDetails(getContext() ,mViews,
                new PhoneCallDetails(TEST_NUMBER, Calls.PRESENTATION_ALLOWED,
                        TEST_FORMATTED_NUMBER, TEST_COUNTRY_ISO, TEST_GEOCODE,
                        types, TEST_DATE, TEST_DURATION)
        );
    }

    /** Sets the details of an unread phone call using the specified call type. */
    private void setUnreadPhoneCallDetailsWithTypes(int... types) {
        mHelper.setPhoneCallDetails(getContext(), mViews,
                new PhoneCallDetails(TEST_NUMBER, Calls.PRESENTATION_ALLOWED,
                        TEST_FORMATTED_NUMBER, TEST_COUNTRY_ISO, TEST_GEOCODE,
                        types, TEST_DATE, TEST_DURATION)
        );
    }
}
