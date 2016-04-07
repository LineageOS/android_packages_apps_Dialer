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

import static com.google.common.collect.Lists.newArrayList;

import android.database.MatrixCursor;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.common.compat.CompatUtils;
import com.android.dialer.util.AppCompatConstants;

import java.util.List;

/**
 * Unit tests for {@link CallLogGroupBuilder}
 */
@SmallTest
public class CallLogGroupBuilderTest extends AndroidTestCase {
    /** A phone number for testing. */
    private static final String TEST_NUMBER1 = "14125551234";
    /** A phone number for testing. */
    private static final String TEST_NUMBER2 = "14125555555";
    /** A post-dial string for testing */
    private static final String TEST_POST_DIAL_DIGITS = ";12435;0987";

    /** The object under test. */
    private CallLogGroupBuilder mBuilder;
    /** Records the created groups. */
    private FakeGroupCreator mFakeGroupCreator;
    /** Cursor to store the values. */
    private MatrixCursor mCursor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mFakeGroupCreator = new FakeGroupCreator();
        mBuilder = new CallLogGroupBuilder(mFakeGroupCreator);
        createCursor();
    }

    @Override
    protected void tearDown() throws Exception {
        mCursor = null;
        mBuilder = null;
        mFakeGroupCreator = null;
        super.tearDown();
    }

    public void testAddGroups_NoCalls() {
        mBuilder.addGroups(mCursor);
        assertEquals(0, mFakeGroupCreator.groups.size());
    }

    public void testAddGroups_OneCall() {
        addCallLogEntry(TEST_NUMBER1, AppCompatConstants.CALLS_INCOMING_TYPE);
        mBuilder.addGroups(mCursor);
        assertEquals(1, mFakeGroupCreator.groups.size());
    }

    public void testAddGroups_TwoCallsNotMatching() {
        addCallLogEntry(TEST_NUMBER1, AppCompatConstants.CALLS_INCOMING_TYPE);
        addCallLogEntry(TEST_NUMBER2, AppCompatConstants.CALLS_INCOMING_TYPE);
        mBuilder.addGroups(mCursor);
        assertEquals(2, mFakeGroupCreator.groups.size());
    }

    public void testAddGroups_ThreeCallsMatching() {
        addCallLogEntry(TEST_NUMBER1, AppCompatConstants.CALLS_INCOMING_TYPE);
        addCallLogEntry(TEST_NUMBER1, AppCompatConstants.CALLS_INCOMING_TYPE);
        addCallLogEntry(TEST_NUMBER1, AppCompatConstants.CALLS_INCOMING_TYPE);
        mBuilder.addGroups(mCursor);
        assertEquals(1, mFakeGroupCreator.groups.size());
        assertGroupIs(0, 3, mFakeGroupCreator.groups.get(0));
    }

    public void testAddGroups_WithPostDialMatching() {
        addCallLogEntryWithPostDialDigits(TEST_NUMBER1, TEST_POST_DIAL_DIGITS,
                AppCompatConstants.CALLS_OUTGOING_TYPE);
        addCallLogEntryWithPostDialDigits(TEST_NUMBER1, TEST_POST_DIAL_DIGITS,
                AppCompatConstants.CALLS_OUTGOING_TYPE);
        addCallLogEntryWithPostDialDigits(TEST_NUMBER1, "",
                AppCompatConstants.CALLS_OUTGOING_TYPE);

        mBuilder.addGroups(mCursor);

        if (CompatUtils.isNCompatible()) {
            assertEquals(2, mFakeGroupCreator.groups.size());
            assertGroupIs(0, 2, mFakeGroupCreator.groups.get(0));
            assertGroupIs(2, 1, mFakeGroupCreator.groups.get(1));
        } else {
            assertEquals(1, mFakeGroupCreator.groups.size());
            assertGroupIs(0, 3, mFakeGroupCreator.groups.get(0));
        }
    }

    public void testAddGroups_WithViaNumberMatching() {
        addCallLogEntryWithViaNumber(TEST_NUMBER1, TEST_NUMBER2,
                AppCompatConstants.CALLS_OUTGOING_TYPE);
        addCallLogEntryWithViaNumber(TEST_NUMBER1, TEST_NUMBER2,
                AppCompatConstants.CALLS_OUTGOING_TYPE);
        addCallLogEntryWithViaNumber(TEST_NUMBER1, "",
                AppCompatConstants.CALLS_OUTGOING_TYPE);

        mBuilder.addGroups(mCursor);

        if (CompatUtils.isNCompatible()) {
            assertEquals(2, mFakeGroupCreator.groups.size());
            assertGroupIs(0, 2, mFakeGroupCreator.groups.get(0));
            assertGroupIs(2, 1, mFakeGroupCreator.groups.get(1));
        } else {
            assertEquals(1, mFakeGroupCreator.groups.size());
            assertGroupIs(0, 3, mFakeGroupCreator.groups.get(0));
        }
    }

    public void testAddGroups_MatchingIncomingAndOutgoing() {
        addCallLogEntry(TEST_NUMBER1, AppCompatConstants.CALLS_INCOMING_TYPE);
        addCallLogEntry(TEST_NUMBER1, AppCompatConstants.CALLS_OUTGOING_TYPE);
        addCallLogEntry(TEST_NUMBER1, AppCompatConstants.CALLS_INCOMING_TYPE);
        mBuilder.addGroups(mCursor);
        assertEquals(1, mFakeGroupCreator.groups.size());
        assertGroupIs(0, 3, mFakeGroupCreator.groups.get(0));
    }

    public void testGrouping_Voicemail() {
        // Does not group with other types of calls, include voicemail themselves.
        assertCallsAreNotGrouped(
                AppCompatConstants.CALLS_VOICEMAIL_TYPE, AppCompatConstants.CALLS_MISSED_TYPE);
        assertCallsAreNotGrouped(
                AppCompatConstants.CALLS_VOICEMAIL_TYPE, AppCompatConstants.CALLS_VOICEMAIL_TYPE);
        assertCallsAreNotGrouped(
                AppCompatConstants.CALLS_VOICEMAIL_TYPE, AppCompatConstants.CALLS_INCOMING_TYPE);
        assertCallsAreNotGrouped(
                AppCompatConstants.CALLS_VOICEMAIL_TYPE, AppCompatConstants.CALLS_OUTGOING_TYPE);
    }

    public void testGrouping_VoicemailArchive() {
        // Does not group with other types of calls, include voicemail themselves.
        assertVoicemailsAreNotGrouped(
                AppCompatConstants.CALLS_VOICEMAIL_TYPE, AppCompatConstants.CALLS_MISSED_TYPE);
        assertVoicemailsAreNotGrouped(
                AppCompatConstants.CALLS_VOICEMAIL_TYPE, AppCompatConstants.CALLS_VOICEMAIL_TYPE);
        assertVoicemailsAreNotGrouped(
                AppCompatConstants.CALLS_VOICEMAIL_TYPE, AppCompatConstants.CALLS_INCOMING_TYPE);
        assertVoicemailsAreNotGrouped(
                AppCompatConstants.CALLS_VOICEMAIL_TYPE, AppCompatConstants.CALLS_OUTGOING_TYPE);
    }

    public void testGrouping_Missed() {
        // Groups with one or more missed calls.
        assertCallsAreGrouped(
                AppCompatConstants.CALLS_MISSED_TYPE, AppCompatConstants.CALLS_MISSED_TYPE);
        assertCallsAreGrouped(
                AppCompatConstants.CALLS_MISSED_TYPE,
                AppCompatConstants.CALLS_MISSED_TYPE,
                AppCompatConstants.CALLS_MISSED_TYPE);
        // Does not group with other types of calls.
        assertCallsAreNotGrouped(
                AppCompatConstants.CALLS_MISSED_TYPE, AppCompatConstants.CALLS_VOICEMAIL_TYPE);
        assertCallsAreGrouped(
                AppCompatConstants.CALLS_MISSED_TYPE, AppCompatConstants.CALLS_INCOMING_TYPE);
        assertCallsAreGrouped(
                AppCompatConstants.CALLS_MISSED_TYPE, AppCompatConstants.CALLS_OUTGOING_TYPE);
    }

    public void testGrouping_Incoming() {
        // Groups with one or more incoming or outgoing.
        assertCallsAreGrouped(
                AppCompatConstants.CALLS_INCOMING_TYPE, AppCompatConstants.CALLS_INCOMING_TYPE);
        assertCallsAreGrouped(
                AppCompatConstants.CALLS_INCOMING_TYPE, AppCompatConstants.CALLS_OUTGOING_TYPE);
        assertCallsAreGrouped(
                AppCompatConstants.CALLS_INCOMING_TYPE,
                AppCompatConstants.CALLS_INCOMING_TYPE,
                AppCompatConstants.CALLS_OUTGOING_TYPE);
        assertCallsAreGrouped(
                AppCompatConstants.CALLS_INCOMING_TYPE,
                AppCompatConstants.CALLS_OUTGOING_TYPE,
                AppCompatConstants.CALLS_INCOMING_TYPE);
        assertCallsAreGrouped(
                AppCompatConstants.CALLS_INCOMING_TYPE, AppCompatConstants.CALLS_MISSED_TYPE);
        // Does not group with voicemail and missed calls.
        assertCallsAreNotGrouped(
                AppCompatConstants.CALLS_INCOMING_TYPE, AppCompatConstants.CALLS_VOICEMAIL_TYPE);
    }

    public void testGrouping_Outgoing() {
        // Groups with one or more incoming or outgoing.
        assertCallsAreGrouped(
                AppCompatConstants.CALLS_OUTGOING_TYPE, AppCompatConstants.CALLS_INCOMING_TYPE);
        assertCallsAreGrouped(
                AppCompatConstants.CALLS_OUTGOING_TYPE, AppCompatConstants.CALLS_OUTGOING_TYPE);
        assertCallsAreGrouped(
                AppCompatConstants.CALLS_OUTGOING_TYPE,
                AppCompatConstants.CALLS_INCOMING_TYPE,
                AppCompatConstants.CALLS_OUTGOING_TYPE);
        assertCallsAreGrouped(
                AppCompatConstants.CALLS_OUTGOING_TYPE,
                AppCompatConstants.CALLS_OUTGOING_TYPE,
                AppCompatConstants.CALLS_INCOMING_TYPE);
        assertCallsAreGrouped(
                AppCompatConstants.CALLS_INCOMING_TYPE, AppCompatConstants.CALLS_MISSED_TYPE);
        // Does not group with voicemail and missed calls.
        assertCallsAreNotGrouped(
                AppCompatConstants.CALLS_INCOMING_TYPE, AppCompatConstants.CALLS_VOICEMAIL_TYPE);
    }

    public void testGrouping_Blocked() {
        assertCallsAreNotGrouped(
                AppCompatConstants.CALLS_BLOCKED_TYPE, AppCompatConstants.CALLS_INCOMING_TYPE);
        assertCallsAreNotGrouped(
                AppCompatConstants.CALLS_BLOCKED_TYPE, AppCompatConstants.CALLS_OUTGOING_TYPE);
        assertCallsAreNotGrouped(
                AppCompatConstants.CALLS_BLOCKED_TYPE, AppCompatConstants.CALLS_MISSED_TYPE);

    }

    public void testAddGroups_Separate() {
        addMultipleCallLogEntries(TEST_NUMBER1,
                AppCompatConstants.CALLS_VOICEMAIL_TYPE,    // Group 1: 0
                AppCompatConstants.CALLS_INCOMING_TYPE,     // Group 2: 1
                AppCompatConstants.CALLS_OUTGOING_TYPE,     // Group 3: 2
                AppCompatConstants.CALLS_MISSED_TYPE);      // Group 4: 3
        mBuilder.addVoicemailGroups(mCursor);

        assertEquals(4, mFakeGroupCreator.groups.size());
        assertGroupIs(0, 1, mFakeGroupCreator.groups.get(0));
        assertGroupIs(1, 1, mFakeGroupCreator.groups.get(1));
        assertGroupIs(2, 1, mFakeGroupCreator.groups.get(2));
        assertGroupIs(3, 1, mFakeGroupCreator.groups.get(3));
    }

    public void testAddGroups_Mixed() {
        addMultipleCallLogEntries(TEST_NUMBER1,
                AppCompatConstants.CALLS_VOICEMAIL_TYPE,   // Group 1: 0
                AppCompatConstants.CALLS_INCOMING_TYPE,    // Group 2: 1-4
                AppCompatConstants.CALLS_OUTGOING_TYPE,
                AppCompatConstants.CALLS_MISSED_TYPE,
                AppCompatConstants.CALLS_MISSED_TYPE,
                AppCompatConstants.CALLS_VOICEMAIL_TYPE,   // Group 3: 5
                AppCompatConstants.CALLS_INCOMING_TYPE,    // Group 4: 6
                AppCompatConstants.CALLS_VOICEMAIL_TYPE,   // Group 5: 7
                AppCompatConstants.CALLS_MISSED_TYPE,      // Group 6: 8-10
                AppCompatConstants.CALLS_MISSED_TYPE,
                AppCompatConstants.CALLS_OUTGOING_TYPE);
        mBuilder.addGroups(mCursor);

        assertEquals(6, mFakeGroupCreator.groups.size());
        assertGroupIs(0, 1, mFakeGroupCreator.groups.get(0));
        assertGroupIs(1, 4, mFakeGroupCreator.groups.get(1));
        assertGroupIs(5, 1, mFakeGroupCreator.groups.get(2));
        assertGroupIs(6, 1, mFakeGroupCreator.groups.get(3));
        assertGroupIs(7, 1, mFakeGroupCreator.groups.get(4));
        assertGroupIs(8, 3, mFakeGroupCreator.groups.get(5));
    }

    public void testAddGroups_Blocked() {
        addMultipleCallLogEntries(TEST_NUMBER1,
                AppCompatConstants.CALLS_INCOMING_TYPE,     // Group 1: 0-1
                AppCompatConstants.CALLS_OUTGOING_TYPE,
                AppCompatConstants.CALLS_BLOCKED_TYPE,      // Group 2: 2
                AppCompatConstants.CALLS_MISSED_TYPE,       // Group 3: 3
                AppCompatConstants.CALLS_BLOCKED_TYPE,      // Group 4: 4-5
                AppCompatConstants.CALLS_BLOCKED_TYPE);
        mBuilder.addGroups(mCursor);

        assertEquals(4, mFakeGroupCreator.groups.size());
        assertGroupIs(0, 2, mFakeGroupCreator.groups.get(0));
        assertGroupIs(2, 1, mFakeGroupCreator.groups.get(1));
        assertGroupIs(3, 1, mFakeGroupCreator.groups.get(2));
        assertGroupIs(4, 2, mFakeGroupCreator.groups.get(3));
    }

    public void testEqualPhoneNumbers() {
        // Identical.
        assertTrue(mBuilder.equalNumbers("6505555555", "6505555555"));
        assertTrue(mBuilder.equalNumbers("650 555 5555", "650 555 5555"));
        // Formatting.
        assertTrue(mBuilder.equalNumbers("6505555555", "650 555 5555"));
        assertTrue(mBuilder.equalNumbers("6505555555", "(650) 555-5555"));
        assertTrue(mBuilder.equalNumbers("650 555 5555", "(650) 555-5555"));
        // Short codes.
        assertTrue(mBuilder.equalNumbers("55555", "55555"));
        assertTrue(mBuilder.equalNumbers("55555", "555 55"));
        // Different numbers.
        assertFalse(mBuilder.equalNumbers("6505555555", "650555555"));
        assertFalse(mBuilder.equalNumbers("6505555555", "6505555551"));
        assertFalse(mBuilder.equalNumbers("650 555 5555", "650 555 555"));
        assertFalse(mBuilder.equalNumbers("650 555 5555", "650 555 5551"));
        assertFalse(mBuilder.equalNumbers("55555", "5555"));
        assertFalse(mBuilder.equalNumbers("55555", "55551"));
        // SIP addresses.
        assertTrue(mBuilder.equalNumbers("6505555555@host.com", "6505555555@host.com"));
        assertTrue(mBuilder.equalNumbers("6505555555@host.com", "6505555555@HOST.COM"));
        assertTrue(mBuilder.equalNumbers("user@host.com", "user@host.com"));
        assertTrue(mBuilder.equalNumbers("user@host.com", "user@HOST.COM"));
        assertFalse(mBuilder.equalNumbers("USER@host.com", "user@host.com"));
        assertFalse(mBuilder.equalNumbers("user@host.com", "user@host1.com"));
        // SIP address vs phone number.
        assertFalse(mBuilder.equalNumbers("6505555555@host.com", "6505555555"));
        assertFalse(mBuilder.equalNumbers("6505555555", "6505555555@host.com"));
        assertFalse(mBuilder.equalNumbers("user@host.com", "6505555555"));
        assertFalse(mBuilder.equalNumbers("6505555555", "user@host.com"));
        // Nulls.
        assertTrue(mBuilder.equalNumbers(null, null));
        assertFalse(mBuilder.equalNumbers(null, "6505555555"));
        assertFalse(mBuilder.equalNumbers("6505555555", null));
        assertFalse(mBuilder.equalNumbers(null, "6505555555@host.com"));
        assertFalse(mBuilder.equalNumbers("6505555555@host.com", null));
    }

    public void testCompareSipAddresses() {
        // Identical.
        assertTrue(mBuilder.compareSipAddresses("6505555555@host.com", "6505555555@host.com"));
        assertTrue(mBuilder.compareSipAddresses("user@host.com", "user@host.com"));
        // Host is case insensitive.
        assertTrue(mBuilder.compareSipAddresses("6505555555@host.com", "6505555555@HOST.COM"));
        assertTrue(mBuilder.compareSipAddresses("user@host.com", "user@HOST.COM"));
        // Userinfo is case sensitive.
        assertFalse(mBuilder.compareSipAddresses("USER@host.com", "user@host.com"));
        // Different hosts.
        assertFalse(mBuilder.compareSipAddresses("user@host.com", "user@host1.com"));
        // Different users.
        assertFalse(mBuilder.compareSipAddresses("user1@host.com", "user@host.com"));
        // Nulls.
        assertTrue(mBuilder.compareSipAddresses(null, null));
        assertFalse(mBuilder.compareSipAddresses(null, "6505555555@host.com"));
        assertFalse(mBuilder.compareSipAddresses("6505555555@host.com", null));
    }

    /** Creates (or recreates) the cursor used to store the call log content for the tests. */
    private void createCursor() {
        mCursor = new MatrixCursor(CallLogQuery._PROJECTION);
    }

    /** Clears the content of the {@link FakeGroupCreator} used in the tests. */
    private void clearFakeGroupCreator() {
        mFakeGroupCreator.groups.clear();
    }

    /** Asserts that calls of the given types are grouped together into a single group. */
    private void assertCallsAreGrouped(int... types) {
        createCursor();
        clearFakeGroupCreator();
        addMultipleCallLogEntries(TEST_NUMBER1, types);
        mBuilder.addGroups(mCursor);
        assertEquals(1, mFakeGroupCreator.groups.size());
        assertGroupIs(0, types.length, mFakeGroupCreator.groups.get(0));

    }

    /** Asserts that calls of the given types are not grouped together at all. */
    private void assertCallsAreNotGrouped(int... types) {
        createCursor();
        clearFakeGroupCreator();
        addMultipleCallLogEntries(TEST_NUMBER1, types);
        mBuilder.addGroups(mCursor);
        assertEquals(types.length, mFakeGroupCreator.groups.size());
    }

    /** Asserts that voicemails are not grouped together with other types at all. */
    private void assertVoicemailsAreNotGrouped(int... types) {
        createCursor();
        clearFakeGroupCreator();
        addMultipleCallLogEntries(TEST_NUMBER1, types);
        mBuilder.addVoicemailGroups(mCursor);
        assertEquals(types.length, mFakeGroupCreator.groups.size());
    }

    /** Adds a set of calls with the given types, all from the same number, in the old section. */
    private void addMultipleCallLogEntries(String number, int... types) {
        for (int type : types) {
            addCallLogEntry(number, type);
        }
    }
    /** Adds a call log entry with the given number and type to the cursor. */
    private void addCallLogEntry(String number, int type) {
        addCallLogEntryWithPostDialDigits(number, "", type);
    }

    /** Adds a call log entry with the given number, post-dial digits, and type to the cursor. */
    private void addCallLogEntryWithPostDialDigits(String number, String postDialDigits, int type) {
        mCursor.moveToNext();
        Object[] values = CallLogQueryTestUtils.createTestValues();
        values[CallLogQuery.ID] = mCursor.getPosition();
        values[CallLogQuery.NUMBER] = number;
        values[CallLogQuery.CALL_TYPE] = type;
        if (CompatUtils.isNCompatible()) {
            values[CallLogQuery.POST_DIAL_DIGITS] = postDialDigits;
        }
        mCursor.addRow(values);
    }

    /** Adds a call log entry with the given number, post-dial digits, and type to the cursor. */
    private void addCallLogEntryWithViaNumber(String number, String viaNumber, int type) {
        mCursor.moveToNext();
        Object[] values = CallLogQueryTestUtils.createTestValues();
        values[CallLogQuery.ID] = mCursor.getPosition();
        values[CallLogQuery.NUMBER] = number;
        values[CallLogQuery.CALL_TYPE] = type;
        if (CompatUtils.isNCompatible()) {
            values[CallLogQuery.VIA_NUMBER] = viaNumber;
        }
        mCursor.addRow(values);
    }

    /** Adds a call log entry with a header to the cursor. */
    private void addCallLogHeader(int section) {
        mCursor.moveToNext();
        Object[] values = CallLogQueryTestUtils.createTestValues();
        values[CallLogQuery.ID] = mCursor.getPosition();
        mCursor.addRow(values);
    }

    /** Asserts that the group matches the given values. */
    private void assertGroupIs(int cursorPosition, int size, GroupSpec group) {
        assertEquals(cursorPosition, group.cursorPosition);
        assertEquals(size, group.size);
    }

    /** Defines an added group. Used by the {@link FakeGroupCreator}. */
    private static class GroupSpec {
        /** The starting position of the group. */
        public final int cursorPosition;
        /** The number of elements in the group. */
        public final int size;

        public GroupSpec(int cursorPosition, int size) {
            this.cursorPosition = cursorPosition;
            this.size = size;
        }
    }

    /** Fake implementation of a GroupCreator which stores the created groups in a member field. */
    private static class FakeGroupCreator implements CallLogGroupBuilder.GroupCreator {
        /** The list of created groups. */
        public final List<GroupSpec> groups = newArrayList();

        @Override
        public void addGroup(int cursorPosition, int size) {
            groups.add(new GroupSpec(cursorPosition, size));
        }

        @Override
        public void setDayGroup(long rowId, int dayGroup) {
            //No-op
        }

        @Override
        public void clearDayGroups() {
            //No-op
        }
    }
}
