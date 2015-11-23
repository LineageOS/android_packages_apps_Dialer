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
 * limitations under the License
 */

package com.android.incallui;

import android.location.Address;
import android.test.AndroidTestCase;
import android.util.Pair;

import com.android.incallui.InCallContactInteractions.BusinessContextInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Tests for InCallContactInteractions class methods for formatting info for display.
 *
 * NOTE: tests assume system settings are set to 12hr time format and US locale. This means that
 * the output of InCallContactInteractions methods are compared against strings in 12hr time format
 * and US locale address formatting unless otherwise specified.
 */
public class InCallContactInteractionsTest extends AndroidTestCase {
    private InCallContactInteractions mInCallContactInteractions;
    private static final float TEST_DISTANCE = (float) 1234.56;

    @Override
    protected void setUp() {
        mInCallContactInteractions = new InCallContactInteractions(mContext, true /* isBusiness */);
    }

    public void testIsOpenNow_NowMatchesOpenTime() {
        assertEquals(mContext.getString(R.string.open_now),
                mInCallContactInteractions.constructHoursInfo(
                        getTestCalendarWithHour(8),
                        Arrays.asList(
                                Pair.create(
                                        getTestCalendarWithHour(8),
                                        getTestCalendarWithHour(20))))
                .heading);
    }

    public void testIsOpenNow_ClosingAfterMidnight() {
        assertEquals(mContext.getString(R.string.open_now),
                mInCallContactInteractions.constructHoursInfo(
                        getTestCalendarWithHour(10),
                        Arrays.asList(
                                Pair.create(
                                        getTestCalendarWithHour(8),
                                        getTestCalendarWithHourAndDaysFromToday(1, 1))))
                .heading);
    }

    public void testIsOpenNow_Open24Hours() {
        assertEquals(mContext.getString(R.string.open_now),
                mInCallContactInteractions.constructHoursInfo(
                        getTestCalendarWithHour(10),
                        Arrays.asList(
                                Pair.create(
                                        getTestCalendarWithHour(8),
                                        getTestCalendarWithHourAndDaysFromToday(8, 1))))
                .heading);
    }

    public void testIsOpenNow_AfterMiddayBreak() {
        assertEquals(mContext.getString(R.string.open_now),
                mInCallContactInteractions.constructHoursInfo(
                        getTestCalendarWithHour(13),
                        Arrays.asList(
                                Pair.create(
                                        getTestCalendarWithHour(8),
                                        getTestCalendarWithHour(10)),
                                Pair.create(
                                        getTestCalendarWithHour(12),
                                        getTestCalendarWithHour(15))))
                .heading);
    }

    public void testIsClosedNow_DuringMiddayBreak() {
        assertEquals(mContext.getString(R.string.closed_now),
                mInCallContactInteractions.constructHoursInfo(
                        getTestCalendarWithHour(11),
                        Arrays.asList(
                                Pair.create(
                                        getTestCalendarWithHour(8),
                                        getTestCalendarWithHour(10)),
                                Pair.create(
                                        getTestCalendarWithHour(12),
                                        getTestCalendarWithHour(15))))
                .heading);
    }

    public void testIsClosedNow_BeforeOpen() {
        assertEquals(mContext.getString(R.string.closed_now),
                mInCallContactInteractions.constructHoursInfo(
                        getTestCalendarWithHour(6),
                        Arrays.asList(
                                Pair.create(
                                        getTestCalendarWithHour(8),
                                        getTestCalendarWithHour(20))))
                .heading);
    }

    public void testIsClosedNow_NowMatchesClosedTime() {
        assertEquals(mContext.getString(R.string.closed_now),
                mInCallContactInteractions.constructHoursInfo(
                        getTestCalendarWithHour(20),
                        Arrays.asList(
                                Pair.create(
                                        getTestCalendarWithHour(8),
                                        getTestCalendarWithHour(20))))
                .heading);
    }

    public void testIsClosedNow_AfterClosed() {
        assertEquals(mContext.getString(R.string.closed_now),
                mInCallContactInteractions.constructHoursInfo(
                        getTestCalendarWithHour(21),
                        Arrays.asList(
                                Pair.create(
                                        getTestCalendarWithHour(8),
                                        getTestCalendarWithHour(20))))
                .heading);
    }

    public void testOpeningHours_SingleOpenRangeWhileOpen() {
        assertEquals("8:00 AM - 8:00 PM",
                mInCallContactInteractions.constructHoursInfo(
                        getTestCalendarWithHour(12),
                        Arrays.asList(
                                Pair.create(
                                        getTestCalendarWithHour(8),
                                        getTestCalendarWithHour(20))))
                .detail);
    }

    public void testOpeningHours_TwoOpenRangesWhileOpen() {
        assertEquals("8:00 AM - 10:00 AM, 12:00 PM - 3:00 PM",
                mInCallContactInteractions.constructHoursInfo(
                        getTestCalendarWithHour(12),
                        Arrays.asList(
                                Pair.create(
                                    getTestCalendarWithHour(8),
                                    getTestCalendarWithHour(10)),
                                Pair.create(
                                        getTestCalendarWithHour(12),
                                        getTestCalendarWithHour(15))))
                .detail);
    }

    public void testOpeningHours_AfterClosedNoTomorrow() {
        assertEquals("Closed today at 8:00 PM",
                mInCallContactInteractions.constructHoursInfo(
                        getTestCalendarWithHour(21),
                        Arrays.asList(
                                Pair.create(
                                        getTestCalendarWithHour(8),
                                        getTestCalendarWithHour(20))))
                .detail);
    }

    public void testOpeningHours_NotOpenTodayOpenTomorrow() {
        assertEquals("Opens tomorrow at 8:00 AM",
                mInCallContactInteractions.constructHoursInfo(
                        getTestCalendarWithHour(21),
                        Arrays.asList(
                                Pair.create(
                                        getTestCalendarWithHourAndDaysFromToday(8, 1),
                                        getTestCalendarWithHourAndDaysFromToday(10, 1))))
                .detail);
    }

    public void testMultipleOpenRanges_BeforeOpen() {
        assertEquals("Opens today at 8:00 AM",
                mInCallContactInteractions.constructHoursInfo(
                        getTestCalendarWithHour(7),
                        getMultipleOpeningHours())
                .detail);
    }

    public void testMultipleOpenRanges_DuringFirstRange() {
        assertEquals("Closes at 10:00 AM",
                mInCallContactInteractions.constructHoursInfo(
                        getTestCalendarWithHour(9),
                        getMultipleOpeningHours())
                .detail);
    }

    public void testMultipleOpenRanges_BeforeMiddleRange() {
        assertEquals("Opens today at 12:00 PM",
                mInCallContactInteractions.constructHoursInfo(
                        getTestCalendarWithHour(11),
                        getMultipleOpeningHours())
                .detail);
    }

    public void testMultipleOpeningHours_DuringLastRange() {
        assertEquals("Closes at 9:00 PM",
                mInCallContactInteractions.constructHoursInfo(
                        getTestCalendarWithHour(19),
                        getMultipleOpeningHours())
                .detail);
    }

    public void testMultipleOpeningHours_AfterClose() {
        assertEquals("Opens tomorrow at 8:00 AM",
                mInCallContactInteractions.constructHoursInfo(
                        getTestCalendarWithHour(22),
                        getMultipleOpeningHours())
                .detail);
    }

    public void testNotOpenTodayOrTomorrow() {
        assertEquals(null,
                mInCallContactInteractions.constructHoursInfo(
                        getTestCalendarWithHour(21),
                        new ArrayList<Pair<Calendar, Calendar>>()));
    }

    public void testLocationInfo_ForUS() {
        BusinessContextInfo info =
                mInCallContactInteractions.constructLocationInfo(
                        Locale.US,
                        getAddressForTest(),
                        TEST_DISTANCE);
        assertEquals("0.8 mi away", info.heading);
        assertEquals("Test address, Test locality", info.detail);
    }

    public void testLocationInfo_ForNotUS() {
        BusinessContextInfo info =
                mInCallContactInteractions.constructLocationInfo(
                        Locale.CANADA,
                        getAddressForTest(),
                        TEST_DISTANCE);
        assertEquals("1.2 km away", info.heading);
        assertEquals("Test address, Test locality", info.detail);
    }

    public void testLocationInfo_NoLocality() {
        Address address = getAddressForTest();
        address.setLocality(null);
        BusinessContextInfo info =
                mInCallContactInteractions.constructLocationInfo(
                        Locale.CANADA,
                        address,
                        TEST_DISTANCE);
        assertEquals("1.2 km away", info.heading);
        assertEquals("Test address", info.detail);
    }

    public void testLocationInfo_NoAddress() {
        BusinessContextInfo info =
                mInCallContactInteractions.constructLocationInfo(
                        Locale.CANADA,
                        null,
                        TEST_DISTANCE);
        assertEquals(null, info);
    }

    public void testLocationInfo_NoDistance() {
        BusinessContextInfo info =
                mInCallContactInteractions.constructLocationInfo(
                        Locale.US,
                        getAddressForTest(),
                        DistanceHelper.DISTANCE_NOT_FOUND);
        assertEquals(null, info.heading);
    }

    private Address getAddressForTest() {
        Address address = new Address(Locale.US);
        address.setAddressLine(0, "Test address");
        address.setLocality("Test locality");
        return address;
    }

    private Calendar getTestCalendarWithHour(int hour) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    private Calendar getTestCalendarWithHourAndDaysFromToday(int hour, int daysFromToday) {
        Calendar calendar = getTestCalendarWithHour(hour);
        calendar.add(Calendar.DATE, daysFromToday);
        return calendar;
    }

    private List<Pair<Calendar, Calendar>> getMultipleOpeningHours() {
        return Arrays.asList(
                Pair.create(
                    getTestCalendarWithHour(8),
                    getTestCalendarWithHour(10)),
                Pair.create(
                        getTestCalendarWithHour(12),
                        getTestCalendarWithHour(15)),
                Pair.create(
                        getTestCalendarWithHour(17),
                        getTestCalendarWithHour(21)),
                Pair.create(
                        getTestCalendarWithHourAndDaysFromToday(8, 1),
                        getTestCalendarWithHourAndDaysFromToday(10, 1)),
                Pair.create(
                        getTestCalendarWithHourAndDaysFromToday(12, 1),
                        getTestCalendarWithHourAndDaysFromToday(8, 1)));
    }
}
