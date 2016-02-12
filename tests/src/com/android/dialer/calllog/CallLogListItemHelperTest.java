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

import com.android.contacts.common.CallUtil;
import com.android.dialer.PhoneCallDetails;
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
    private CallLogListItemViewHolder mViewHolder;

    private Context mContext;
    private Resources mResources;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getContext();
        mResources = mContext.getResources();
        final TestTelecomCallLogCache phoneUtils =
                new TestTelecomCallLogCache(mContext, TEST_VOICEMAIL_NUMBER);
        PhoneCallDetailsHelper phoneCallDetailsHelper =
                new PhoneCallDetailsHelper(mContext, mResources, phoneUtils);
        LookupInfoPresenter lookupInfoPresenter = new LookupInfoPresenter(mContext, mResources);
        mHelper = new CallLogListItemHelper(phoneCallDetailsHelper, lookupInfoPresenter,
                mResources, phoneUtils);
        mViewHolder = CallLogListItemViewHolder.createForTest(mContext);

    }

    @Override
    protected void tearDown() throws Exception {
        mHelper = null;
        mViewHolder = null;
        super.tearDown();
    }

    public void testSetPhoneCallDetails() {
        setPhoneCallDetailsWithNumber("12125551234", Calls.PRESENTATION_ALLOWED,
                "1-212-555-1234");
        assertEquals(View.VISIBLE, mViewHolder.primaryActionButtonView.getVisibility());
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
        assertEquals(View.VISIBLE, mViewHolder.voicemailPlaybackView.getVisibility());
    }

    public void testSetPhoneCallDetails_ReadVoicemail() {
        PhoneCallDetails details = getPhoneCallDetailsWithTypes(Calls.VOICEMAIL_TYPE);
        mHelper.setPhoneCallDetails(mViewHolder, details);
        assertEquals(View.VISIBLE, mViewHolder.voicemailPlaybackView.getVisibility());
    }

    public void testSetPhoneCallDetails_UnreadVoicemail() {
        PhoneCallDetails details = getPhoneCallDetailsWithTypes(Calls.VOICEMAIL_TYPE);
        mHelper.setPhoneCallDetails(mViewHolder, details);
        assertEquals(View.VISIBLE, mViewHolder.voicemailPlaybackView.getVisibility());
    }

    public void testSetPhoneCallDetails_VoicemailFromUnknown() {
        setPhoneCallDetailsWithNumberAndType("", Calls.PRESENTATION_UNKNOWN,
                "", Calls.VOICEMAIL_TYPE);
        assertEquals(View.VISIBLE, mViewHolder.voicemailPlaybackView.getVisibility());
    }

    /**
     * Test getCallDescriptionID method used to get the accessibility description for calls.
     */
    public void testGetCallDescriptionID_Answered() {
        int[] callTypes = new int[]{ Calls.INCOMING_TYPE };
        assertEquals(R.string.description_incoming_answered_call,
                mHelper.getCallDescriptionStringID(callTypes));
    }

    /**
     * Test getCallDescriptionID method used to get the accessibility description for calls.
     */
    public void testGetCallDescriptionID_Missed() {
        int[] callTypes = new int[]{ Calls.MISSED_TYPE };
        assertEquals(R.string.description_incoming_missed_call,
                mHelper.getCallDescriptionStringID(callTypes));
    }

    /**
     * Test getCallDescriptionID method used to get the accessibility description for calls.
     */
    public void testGetCallDescriptionID_Voicemail() {
        int[] callTypes = new int[]{ Calls.VOICEMAIL_TYPE };
        assertEquals(R.string.description_incoming_missed_call,
                mHelper.getCallDescriptionStringID(callTypes));
    }

    /**
     * Test getCallDescriptionID method used to get the accessibility description for calls.
     * Test case where an outgoing call is made to a known number and there is a history of
     * only a single call for this caller.
     */
    public void testGetCallDescriptionID_OutgoingSingle() {
        int[] callTypes = new int[]{ Calls.OUTGOING_TYPE };
        assertEquals(R.string.description_outgoing_call,
                mHelper.getCallDescriptionStringID(callTypes));
    }

    /**
     * Test getCallDescriptionID method used to get the accessibility description for calls.
     * Test case where an outgoing call is made to a known number and there is a history of
     * many calls for this caller.
     */
    public void testGetCallDescriptionID_OutgoingMultiple() {
        int[] callTypes = new int[]{ Calls.OUTGOING_TYPE, Calls.OUTGOING_TYPE };
        assertEquals(R.string.description_outgoing_call,
                mHelper.getCallDescriptionStringID(callTypes));
    }

    /**
     * Test getCallDescription method used to get the accessibility description for calls.
     * For outgoing calls, we should NOT have "New Voicemail" in the description.
     */
    public void testGetCallDescription_NoVoicemailOutgoing() {
        PhoneCallDetails details =
                getPhoneCallDetailsWithTypes(Calls.OUTGOING_TYPE, Calls.OUTGOING_TYPE);
        CharSequence description = mHelper.getCallDescription(details);
        assertFalse(description.toString()
                .contains(this.mResources.getString(R.string.description_new_voicemail)));
    }

    /**
     * Test getCallDescription method used to get the accessibility description for calls.
     * For regular incoming calls, we should NOT have "New Voicemail" in the description.
     */
    public void testGetCallDescription_NoVoicemailIncoming() {
        PhoneCallDetails details =
                getPhoneCallDetailsWithTypes(Calls.INCOMING_TYPE, Calls.OUTGOING_TYPE);
        CharSequence description = mHelper.getCallDescription(details);
        assertFalse(description.toString()
                .contains(this.mResources.getString(R.string.description_new_voicemail)));
    }

    /**
     * Test getCallDescription method used to get the accessibility description for calls.
     * For regular missed calls, we should NOT have "New Voicemail" in the description.
     */
    public void testGetCallDescription_NoVoicemailMissed() {
        PhoneCallDetails details =
                getPhoneCallDetailsWithTypes(Calls.MISSED_TYPE, Calls.OUTGOING_TYPE);
        CharSequence description = mHelper.getCallDescription(details);
        assertFalse(description.toString()
                .contains(this.mResources.getString(R.string.description_new_voicemail)));
    }

    /**
     * Test getCallDescription method used to get the accessibility description for calls.
     * For voicemail calls, we should have "New Voicemail" in the description.
     */
    public void testGetCallDescription_Voicemail() {
        PhoneCallDetails details =
                getPhoneCallDetailsWithTypes(Calls.VOICEMAIL_TYPE, Calls.OUTGOING_TYPE);
        CharSequence description = mHelper.getCallDescription(details);
        assertTrue(description.toString()
                .contains(this.mResources.getString(R.string.description_new_voicemail)));
    }

    /**
     * Test getCallDescription method used to get the accessibility description for calls.
     * Test that the "X calls" message is not present if there is only a single call.
     */
    public void testGetCallDescription_NumCallsSingle() {
        PhoneCallDetails details = getPhoneCallDetailsWithTypes(Calls.VOICEMAIL_TYPE);
        CharSequence description = mHelper.getCallDescription(details);

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
        PhoneCallDetails details =
                getPhoneCallDetailsWithTypes(Calls.VOICEMAIL_TYPE, Calls.INCOMING_TYPE);
        CharSequence description = mHelper.getCallDescription(details);
        assertTrue(description.toString()
                .contains(this.mResources.getString(R.string.description_num_calls, 2)));
    }

    /**
     * Test getCallDescription method used to get the accessibility description for calls.
     * Test that the "Video call." message is present if the call had video capability.
     */
    public void testGetCallDescription_Video() {
        PhoneCallDetails details =
                getPhoneCallDetailsWithTypes(Calls.INCOMING_TYPE, Calls.INCOMING_TYPE);
        details.features = Calls.FEATURES_VIDEO;

        CharSequence description = mHelper.getCallDescription(details);
        final boolean isVideoEnabled = CallUtil.isVideoEnabled(getContext());
        assertTrue(description.toString()
                .contains(this.mResources.getString(
                        isVideoEnabled
                        ? R.string.description_video_call
                        : R.string.description_num_calls,
                                2)));
    }

    /** Asserts that the primary action view does not have a call intent. */
    private void assertNoCallIntent() {
        Object intentProvider = (IntentProvider)mViewHolder.primaryActionView.getTag();
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
        PhoneCallDetails details = getPhoneCallDetails(
                number, presentation, formattedNumber);
        details.callTypes = new int[]{ callType };
        mHelper.setPhoneCallDetails(mViewHolder, details);
    }

    private PhoneCallDetails getPhoneCallDetails(
            String number, int presentation, String formattedNumber) {
        PhoneCallDetails details = new PhoneCallDetails(
                mContext,
                number,
                presentation,
                formattedNumber,
                false /* isVoicemail */);
        setDefaultDetails(details);
        return details;
    }

    /** Returns the details of a phone call using the specified call type. */
    private PhoneCallDetails getPhoneCallDetailsWithTypes(int... types) {
        PhoneCallDetails details = new PhoneCallDetails(
                mContext,
                TEST_NUMBER,
                Calls.PRESENTATION_ALLOWED,
                TEST_FORMATTED_NUMBER,
                false /* isVoicemail */);
        setDefaultDetails(details);
        details.callTypes = types;
        return details;
    }

    private void setDefaultDetails(PhoneCallDetails details) {
        details.callTypes = new int[]{ Calls.INCOMING_TYPE };
        details.countryIso = TEST_COUNTRY_ISO;
        details.date = TEST_DATE;
        details.duration = TEST_DURATION;
        details.geocode = TEST_GEOCODE;
    }
}
