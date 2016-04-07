/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.provider.CallLog.Calls;
import android.telecom.PhoneAccountHandle;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.text.Html;
import android.text.Spanned;
import android.view.View;
import android.widget.TextView;

import com.android.dialer.PhoneCallDetails;
import com.android.dialer.R;
import com.android.dialer.calllog.calllogcache.TestTelecomCallLogCache;
import com.android.dialer.util.AppCompatConstants;
import com.android.dialer.util.LocaleTestUtils;

import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unit tests for {@link PhoneCallDetailsHelper}.
 */
@MediumTest
public class PhoneCallDetailsHelperTest extends AndroidTestCase {
    /** The number to be used to access the voicemail. */
    private static final String TEST_VOICEMAIL_NUMBER = "125";
    /** The date of the call log entry. */
    private static final long TEST_DATE =
        new GregorianCalendar(2011, 5, 3, 13, 0, 0).getTimeInMillis();
    private static final long INJECTED_CURRENT_DATE =
        new GregorianCalendar(2011, 5, 4, 13, 0, 0).getTimeInMillis();
    /** A test duration value for phone calls. */
    private static final long TEST_DURATION = 62300;
    /** The number of the caller/callee in the log entry. */
    private static final String TEST_NUMBER = "14125555555";
    /** The formatted version of {@link #TEST_NUMBER}. */
    private static final String TEST_FORMATTED_NUMBER = "1-412-255-5555";
    /** The country ISO name used in the tests. */
    private static final String TEST_COUNTRY_ISO = "US";
    /** The geocoded location used in the tests. */
    private static final String TEST_GEOCODE = "United States";
    /** Empty geocode label */
    private static final String EMPTY_GEOCODE = "";
    /** Empty post-dial digits label */
    private static final String EMPTY_POSTDIAL = "";
    /** The number that the call was received via */
    private static final String TEST_VIA_NUMBER = "+16505551234";
    /** The Phone Account name that the Call was received on */
    private static final String TEST_ACCOUNT_LABEL = "T-Stationary";

    /** The object under test. */
    private PhoneCallDetailsHelper mHelper;
    /** The views to fill. */
    private PhoneCallDetailsViews mViews;
    private TextView mNameView;
    private LocaleTestUtils mLocaleTestUtils;
    private TestTelecomCallLogCache mPhoneUtils;

    private Context mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getContext();
        Resources resources = mContext.getResources();
        mPhoneUtils = new TestTelecomCallLogCache(mContext, TEST_VOICEMAIL_NUMBER,
                TEST_ACCOUNT_LABEL);
        mHelper = new PhoneCallDetailsHelper(mContext, resources, mPhoneUtils);
        mHelper.setCurrentTimeForTest(INJECTED_CURRENT_DATE);
        mViews = PhoneCallDetailsViews.createForTest(mContext);
        mNameView = new TextView(mContext);
        mLocaleTestUtils = new LocaleTestUtils(mContext);
        mLocaleTestUtils.setLocale(Locale.US);
    }

    @Override
    protected void tearDown() throws Exception {
        mLocaleTestUtils.restoreLocale();
        mNameView = null;
        mViews = null;
        mHelper = null;
        super.tearDown();
    }

    public void testSetPhoneCallDetails_Unknown() {
        setPhoneCallDetailsWithNumber("", Calls.PRESENTATION_UNKNOWN, "");
        assertNameEqualsResource(R.string.unknown);
    }

    public void testSetPhoneCallDetails_Private() {
        setPhoneCallDetailsWithNumber("", Calls.PRESENTATION_RESTRICTED, "");
        assertNameEqualsResource(R.string.private_num);
    }

    public void testSetPhoneCallDetails_Payphone() {
        setPhoneCallDetailsWithNumber("", Calls.PRESENTATION_PAYPHONE, "");
        assertNameEqualsResource(R.string.payphone);
    }

    public void testSetPhoneCallDetails_Voicemail() {
        setPhoneCallDetailsWithNumber(TEST_VOICEMAIL_NUMBER,
                Calls.PRESENTATION_ALLOWED, TEST_VOICEMAIL_NUMBER);
        assertNameEqualsResource(R.string.voicemail);
    }

    public void testSetPhoneCallDetails_ViaNumber() {
        setPhoneCallDetailsWithViaNumber(TEST_VIA_NUMBER);
        assertViaNumberEquals(TEST_VIA_NUMBER);
    }

    public void testSetPhoneCallDetails_NoViaNumber() {
        setDefaultPhoneCallDetailsNoViaNumber();
        assertCallAccountInvisible();
    }

    public void testSetPhoneCallDetails_AccountLabel() {
        setPhoneCallDetailsWithAccountHandle();
        assertAccountLabelEquals(TEST_ACCOUNT_LABEL);
    }

    public void testSetPhoneCallDetails_AccountHandleViaNumber() {
        setPhoneCallDetailsWithAccountLabelViaNumber(TEST_VIA_NUMBER);
        assertAccountLabelEquals(TEST_VIA_NUMBER, TEST_ACCOUNT_LABEL);
    }

    // Voicemail date string has 3 different formats depending on how long ago the call was placed
    public void testSetVoicemailPhoneCallDetails_Today() {
        setVoicemailPhoneCallDetailsWithDate(System.currentTimeMillis());
        assertLocationAndDateContains("Today at");
    }

    public void testSetVoicemailPhoneCallDetails_WithinCurrentYear() {
        mHelper.setCurrentTimeForTest(INJECTED_CURRENT_DATE);
        String formattedTestDate = "Jun 3 at 1:00 PM";
        setVoicemailPhoneCallDetailsWithDate(TEST_DATE);
        assertLocationAndDateContains(formattedTestDate);
    }

    public void testSetVoicemailPhoneCallDetails_OutsideCurrentYear() {
        mHelper.setCurrentTimeForTest(INJECTED_CURRENT_DATE);
        long testDate = new GregorianCalendar(2009, 5, 3, 13, 0, 0).getTimeInMillis();
        String formattedTestDate = "Jun 3, 2009 at 1:00 PM";
        setVoicemailPhoneCallDetailsWithDate(testDate);
        assertLocationAndDateContains(formattedTestDate);
    }

    public void testVoicemailLocationNotShownWithDate() {
        setVoicemailPhoneCallDetailsWithDate(TEST_DATE);
        assertLocationAndDateExactEquals("Jun 3 at 1:00 PM • 99:20");
    }

    public void testVoicemailDuration() {
        setVoicemailPhoneCallDetailsWithDuration(100);
        assertDurationExactEquals("01:40");
    }

    public void testVoicemailDuration_Capped() {
        setVoicemailPhoneCallDetailsWithDuration(TEST_DURATION);
        assertDurationExactEquals("99:20");
    }

    public void testVoicemailDuration_Zero() {
        setVoicemailPhoneCallDetailsWithDuration(0);
        assertLocationAndDateExactEquals("Jun 3 at 1:00 PM");
    }

    public void testVoicemailDuration_EvenMinute() {
        setVoicemailPhoneCallDetailsWithDuration(60);
        assertDurationExactEquals("01:00");
    }

    /** Asserts that a char sequence is actually a Spanned corresponding to the expected HTML. */
    private void assertEqualsHtml(String expectedHtml, CharSequence actualText) {
        // In order to contain HTML, the text should actually be a Spanned.
        assertTrue(actualText instanceof Spanned);
        Spanned actualSpanned = (Spanned) actualText;
        // Convert from and to HTML to take care of alternative formatting of HTML.
        assertEquals(Html.toHtml(Html.fromHtml(expectedHtml)), Html.toHtml(actualSpanned));

    }

    public void testSetPhoneCallDetails_Date() {
        mHelper.setCurrentTimeForTest(
                new GregorianCalendar(2011, 5, 3, 13, 0, 0).getTimeInMillis());

        setPhoneCallDetailsWithDate(
                new GregorianCalendar(2011, 5, 3, 13, 0, 0).getTimeInMillis());
        assertLocationAndDateContains("0 min. ago");

        setPhoneCallDetailsWithDate(
                new GregorianCalendar(2011, 5, 3, 12, 0, 0).getTimeInMillis());
        assertLocationAndDateContains("1 hr. ago");

        setPhoneCallDetailsWithDate(
                new GregorianCalendar(2011, 5, 2, 13, 0, 0).getTimeInMillis());
        assertLocationAndDateContains("Yesterday");

        setPhoneCallDetailsWithDate(
                new GregorianCalendar(2011, 5, 1, 13, 0, 0).getTimeInMillis());
        assertLocationAndDateContains("2 days ago");
    }

    public void testSetPhoneCallDetails_CallTypeIcons() {
        setPhoneCallDetailsWithCallTypeIcons(AppCompatConstants.CALLS_INCOMING_TYPE);
        assertCallTypeIconsEquals(AppCompatConstants.CALLS_INCOMING_TYPE);

        setPhoneCallDetailsWithCallTypeIcons(AppCompatConstants.CALLS_OUTGOING_TYPE);
        assertCallTypeIconsEquals(AppCompatConstants.CALLS_OUTGOING_TYPE);

        setPhoneCallDetailsWithCallTypeIcons(AppCompatConstants.CALLS_MISSED_TYPE);
        assertCallTypeIconsEquals(AppCompatConstants.CALLS_MISSED_TYPE);

        setPhoneCallDetailsWithCallTypeIcons(AppCompatConstants.CALLS_VOICEMAIL_TYPE);
        assertCallTypeIconsEquals(AppCompatConstants.CALLS_VOICEMAIL_TYPE);
    }

    /**
     * Tests a case where the video call feature is present.
     */
    public void testSetPhoneCallDetails_Video() {
        PhoneCallDetails details = getPhoneCallDetails();
        details.features = Calls.FEATURES_VIDEO;
        mHelper.setPhoneCallDetails(mViews, details);

        assertIsVideoCall(true);
    }

    /**
     * Tests a case where the video call feature is not present.
     */
    public void testSetPhoneCallDetails_NoVideo() {
        PhoneCallDetails details = getPhoneCallDetails();
        details.features = 0;
        mHelper.setPhoneCallDetails(mViews, details);

        assertIsVideoCall(false);
    }

    public void testSetPhoneCallDetails_MultipleCallTypeIcons() {
        setPhoneCallDetailsWithCallTypeIcons(
                AppCompatConstants.CALLS_INCOMING_TYPE,
                AppCompatConstants.CALLS_OUTGOING_TYPE);
        assertCallTypeIconsEquals(
                AppCompatConstants.CALLS_INCOMING_TYPE,
                AppCompatConstants.CALLS_OUTGOING_TYPE);

        setPhoneCallDetailsWithCallTypeIcons(
                AppCompatConstants.CALLS_MISSED_TYPE,
                AppCompatConstants.CALLS_MISSED_TYPE);
        assertCallTypeIconsEquals(
                AppCompatConstants.CALLS_MISSED_TYPE,
                AppCompatConstants.CALLS_MISSED_TYPE);
    }

    public void testSetPhoneCallDetails_MultipleCallTypeIconsLastOneDropped() {
        setPhoneCallDetailsWithCallTypeIcons(
                AppCompatConstants.CALLS_MISSED_TYPE,
                AppCompatConstants.CALLS_MISSED_TYPE,
                AppCompatConstants.CALLS_INCOMING_TYPE,
                AppCompatConstants.CALLS_OUTGOING_TYPE);
        assertCallTypeIconsEqualsPlusOverflow("(4)",
                AppCompatConstants.CALLS_MISSED_TYPE,
                AppCompatConstants.CALLS_MISSED_TYPE,
                AppCompatConstants.CALLS_INCOMING_TYPE);
    }

    public void testSetPhoneCallDetails_Geocode() {
        setPhoneCallDetailsWithNumberAndGeocode("+14125555555", "1-412-555-5555", "Pennsylvania");
        assertNameEquals("1-412-555-5555");  // The phone number is shown as the name.
        assertLocationAndDateContains("Pennsylvania"); // The geocode is shown as the label.
    }

    public void testSetPhoneCallDetails_NoGeocode() {
        setPhoneCallDetailsWithNumberAndGeocode("+14125555555", "1-412-555-5555", null);
        assertNameEquals("1-412-555-5555");  // The phone number is shown as the name.
        assertLocationAndDateContains(EMPTY_GEOCODE); // The empty geocode is shown as the label.
    }

    public void testSetPhoneCallDetails_EmptyGeocode() {
        setPhoneCallDetailsWithNumberAndGeocode("+14125555555", "1-412-555-5555", "");
        assertNameEquals("1-412-555-5555");  // The phone number is shown as the name.
        assertLocationAndDateContains(EMPTY_GEOCODE); // The empty geocode is shown as the label.
    }

    public void testSetPhoneCallDetails_NoGeocodeForVoicemail() {
        setPhoneCallDetailsWithNumberAndGeocode(TEST_VOICEMAIL_NUMBER, "", "United States");
        assertLocationAndDateContains(EMPTY_GEOCODE); // The empty geocode is shown as the label.
    }

    public void testSetPhoneCallDetails_Highlighted() {
        setPhoneCallDetailsWithNumber(TEST_VOICEMAIL_NUMBER,
                Calls.PRESENTATION_ALLOWED, "");
    }

    public void testSetCallDetailsHeader_NumberOnly() {
        setCallDetailsHeaderWithNumber(TEST_NUMBER, Calls.PRESENTATION_ALLOWED);
        assertEquals(View.VISIBLE, mNameView.getVisibility());
        assertEquals("1-412-255-5555", mNameView.getText().toString());
    }

    public void testSetCallDetailsHeader_UnknownNumber() {
        setCallDetailsHeaderWithNumber("", Calls.PRESENTATION_UNKNOWN);
        assertEquals(View.VISIBLE, mNameView.getVisibility());
        assertEquals("Unknown", mNameView.getText().toString());
    }

    public void testSetCallDetailsHeader_PrivateNumber() {
        setCallDetailsHeaderWithNumber("", Calls.PRESENTATION_RESTRICTED);
        assertEquals(View.VISIBLE, mNameView.getVisibility());
        assertEquals("Private number", mNameView.getText().toString());
    }

    public void testSetCallDetailsHeader_PayphoneNumber() {
        setCallDetailsHeaderWithNumber("", Calls.PRESENTATION_PAYPHONE);
        assertEquals(View.VISIBLE, mNameView.getVisibility());
        assertEquals("Payphone", mNameView.getText().toString());
    }

    public void testSetCallDetailsHeader_VoicemailNumber() {
        PhoneCallDetails details = getPhoneCallDetails(
                TEST_VOICEMAIL_NUMBER,
                Calls.PRESENTATION_ALLOWED,
                TEST_FORMATTED_NUMBER);
        mHelper.setCallDetailsHeader(mNameView, details);
        assertEquals(View.VISIBLE, mNameView.getVisibility());
        assertEquals("Voicemail", mNameView.getText().toString());
    }

    public void testSetCallDetailsHeader() {
        setCallDetailsHeader("John Doe");
        assertEquals(View.VISIBLE, mNameView.getVisibility());
        assertEquals("John Doe", mNameView.getText().toString());
    }

    public void testGetCallTypeOrLocation_Geocode() {
        assertEquals(TEST_GEOCODE, mHelper.getCallTypeOrLocation(getPhoneCallDetails()));
    }

    public void testGetCallTypeOrLocation_CallType() {
        PhoneCallDetails details = getPhoneCallDetails();
        details.geocode = null;
        details.numberType = Calls.INCOMING_TYPE;
        mHelper.setPhoneTypeLabelForTest("mobile");
        assertEquals("mobile", mHelper.getCallTypeOrLocation(details));
    }

    public void testGetCallTypeOrLocation_DisplayNumber() {
        PhoneCallDetails details = getPhoneCallDetails("", Calls.PRESENTATION_ALLOWED,
                TEST_FORMATTED_NUMBER);
        details.namePrimary = "name";
        assertEquals(TEST_FORMATTED_NUMBER, mHelper.getCallTypeOrLocation(details));
    }

    /** Asserts that the name text field contains the value of the given string resource. */
    private void assertNameEqualsResource(int resId) {
        assertNameEquals(getContext().getString(resId));
    }

    /** Asserts that the name text field contains the given string value. */
    private void assertNameEquals(String text) {
        assertEquals(text, mViews.nameView.getText().toString());
    }

    /** Asserts that the location and date text field contains the given string value. */
    private void assertLocationAndDateContains(String text) {
        assertTrue(mViews.callLocationAndDate.getText().toString().contains(text));
    }

    /** Asserts that the location and date text field exactly equals the given string value. */
    private void assertLocationAndDateExactEquals(String text) {
        assertEquals(text, mViews.callLocationAndDate.getText());
    }

    /** Asserts that the via number is correct. */
    private void assertViaNumberEquals(String text) {
        final String callAccountText =
                mContext.getResources().getString(R.string.description_via_number, text);
        assertEquals(callAccountText, mViews.callAccountLabel.getText());
    }

    /** Asserts that the account label is correct. */
    private void assertAccountLabelEquals(String text) {
        assertEquals(text, mViews.callAccountLabel.getText());
    }

    /** Asserts that the account label is correct when also showing the via number. */
    private void assertAccountLabelEquals(String viaNumber, String accountLabel) {
        final String viaNumberText =
                mContext.getResources().getString(R.string.description_via_number, viaNumber);
        assertEquals(accountLabel + " " + viaNumberText, mViews.callAccountLabel.getText());
    }

    /** Asserts that the call account label is invisible. */
    private void assertCallAccountInvisible() {
        assertEquals(mViews.callAccountLabel.getVisibility(), View.GONE);
    }

    /** Asserts that the duration is exactly as included in the location and date text field. */
    private void assertDurationExactEquals(String text) {
        Matcher matcher = Pattern.compile("(.*) (\\u2022) (\\d{2}:\\d{2})").matcher(
                mViews.callLocationAndDate.getText());
        assertEquals(true, matcher.matches());
        assertEquals(text, matcher.group(3));
    }

    /** Asserts that the video icon is shown. */
    private void assertIsVideoCall(boolean isVideoCall) {
        assertEquals(isVideoCall, mViews.callTypeIcons.isVideoShown());
    }

    /** Asserts that the call type contains the images with the given drawables. */
    private void assertCallTypeIconsEquals(int... ids) {
        assertEquals(ids.length, mViews.callTypeIcons.getCount());
        for (int index = 0; index < ids.length; ++index) {
            int id = ids[index];
            assertEquals(id, mViews.callTypeIcons.getCallType(index));
        }
        assertEquals(View.VISIBLE, mViews.callTypeIcons.getVisibility());
    }

    /**
     * Asserts that the call type contains the images with the given drawables and shows the given
     * text next to the icons.
     */
    private void assertCallTypeIconsEqualsPlusOverflow(String overflowText, int... ids) {
        assertEquals(ids.length, mViews.callTypeIcons.getCount());
        for (int index = 0; index < ids.length; ++index) {
            int id = ids[index];
            assertEquals(id, mViews.callTypeIcons.getCallType(index));
        }
        assertEquals(View.VISIBLE, mViews.callTypeIcons.getVisibility());
        assertTrue(mViews.callLocationAndDate.getText().toString().contains(overflowText));
        assertTrue(mViews.callLocationAndDate.getText().toString().contains("Yesterday"));
    }

    /** Sets the phone call details with default values and the given number. */
    private void setPhoneCallDetailsWithNumber(String number, int presentation,
            String formattedNumber) {
        PhoneCallDetails details = getPhoneCallDetails(number, presentation, formattedNumber);
        details.callTypes = new int[]{ AppCompatConstants.CALLS_VOICEMAIL_TYPE };
        mHelper.setPhoneCallDetails(mViews, details);
    }

    /** Sets the phone call details with default values and the given via number. */
    private void setPhoneCallDetailsWithViaNumber(String viaNumber) {
        PhoneCallDetails details = getPhoneCallDetails();
        mPhoneUtils.setAccountLabel("");
        details.viaNumber = viaNumber;
        mHelper.setPhoneCallDetails(mViews, details);
    }

    /** Sets the phone call details with an account handle. */
    private void setPhoneCallDetailsWithAccountHandle() {
        PhoneCallDetails details = getPhoneCallDetails();
        details.accountHandle = new PhoneAccountHandle(new ComponentName("",""), "");
        mHelper.setPhoneCallDetails(mViews, details);
    }

    /** Sets the phone call details with an account handle and via number */
    private void setPhoneCallDetailsWithAccountLabelViaNumber(String viaNumber) {
        PhoneCallDetails details = getPhoneCallDetails();
        details.viaNumber = viaNumber;
        details.accountHandle = new PhoneAccountHandle(new ComponentName("",""), "");
        mHelper.setPhoneCallDetails(mViews, details);
    }

    /** Populates the phone call details with the Defaults. */
    private void setDefaultPhoneCallDetailsNoViaNumber() {
        PhoneCallDetails details = getPhoneCallDetails();
        mPhoneUtils.setAccountLabel("");
        mHelper.setPhoneCallDetails(mViews, details);
    }

    /** Sets the phone call details with default values and the given number. */
    private void setPhoneCallDetailsWithNumberAndGeocode(
            String number, String formattedNumber, String geocodedLocation) {
        PhoneCallDetails details = getPhoneCallDetails(
                number, Calls.PRESENTATION_ALLOWED, formattedNumber);
        details.geocode = geocodedLocation;
        mHelper.setPhoneCallDetails(mViews, details);
    }

    /** Sets the phone call details with default values and the given date. */
    private void setPhoneCallDetailsWithDate(long date) {
        PhoneCallDetails details = getPhoneCallDetails();
        details.date = date;
        mHelper.setPhoneCallDetails(mViews, details);
    }

    private void setVoicemailPhoneCallDetailsWithDate(long date) {
        PhoneCallDetails details = getPhoneCallDetails();
        details.date = date;
        details.callTypes = new int[] {Calls.VOICEMAIL_TYPE};
        mHelper.setPhoneCallDetails(mViews, details);
    }

    /** Sets the voice mail details with default values and the given duration. */
    private void setVoicemailPhoneCallDetailsWithDuration(long duration) {
        PhoneCallDetails details = getPhoneCallDetails();
        details.duration = duration;
        details.callTypes = new int[] {Calls.VOICEMAIL_TYPE};
        mHelper.setPhoneCallDetails(mViews, details);
    }

    /** Sets the phone call details with default values and the given call types using icons. */
    private void setPhoneCallDetailsWithCallTypeIcons(int... callTypes) {
        PhoneCallDetails details = getPhoneCallDetails();
        details.callTypes = callTypes;
        mHelper.setPhoneCallDetails(mViews, details);
    }

    private void setCallDetailsHeaderWithNumber(String number, int presentation) {
        mHelper.setCallDetailsHeader(mNameView,
                getPhoneCallDetails(number, presentation, TEST_FORMATTED_NUMBER));
    }

    private void setCallDetailsHeader(String name) {
        PhoneCallDetails details = getPhoneCallDetails();
        details.namePrimary = name;
        mHelper.setCallDetailsHeader(mNameView, details);
    }

    private PhoneCallDetails getPhoneCallDetails() {
        PhoneCallDetails details = new PhoneCallDetails(
                mContext,
                TEST_NUMBER,
                Calls.PRESENTATION_ALLOWED,
                TEST_FORMATTED_NUMBER,
                EMPTY_POSTDIAL,
                false /* isVoicemail */);
        setDefaultDetails(details);
        return details;
    }

    private PhoneCallDetails getPhoneCallDetails(
            String number, int presentation, String formattedNumber) {
        PhoneCallDetails details = new PhoneCallDetails(
                mContext,
                number,
                presentation,
                formattedNumber,
                EMPTY_POSTDIAL,
                isVoicemail(number));
        setDefaultDetails(details);
        return details;
    }

    private void setDefaultDetails(PhoneCallDetails details) {
        details.callTypes = new int[]{ AppCompatConstants.CALLS_INCOMING_TYPE };
        details.countryIso = TEST_COUNTRY_ISO;
        details.date = TEST_DATE;
        details.duration = TEST_DURATION;
        details.geocode = TEST_GEOCODE;
    }

    private boolean isVoicemail(String number) {
        return number.equals(TEST_VOICEMAIL_NUMBER);
    }
}
