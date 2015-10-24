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

import java.util.Calendar;
import java.util.Locale;

public class InCallContactInteractionsTest extends AndroidTestCase {
    private InCallContactInteractions mInCallContactInteractions;
    private static final float TEST_DISTANCE = (float) 1234.56;

    @Override
    protected void setUp() {
        mInCallContactInteractions = new InCallContactInteractions(mContext, true /* isBusiness */);
    }

    public void testIsOpenNow() {
        Calendar currentTimeForTest = Calendar.getInstance();
        currentTimeForTest.set(Calendar.HOUR_OF_DAY, 10);
        BusinessContextInfo info =
                mInCallContactInteractions.constructHoursInfo(
                        currentTimeForTest,
                        Pair.create("0800", "2000"));
        assertEquals(mContext.getString(R.string.open_now), info.heading);
    }

    public void testIsClosedNow_BeforeOpen() {
        Calendar currentTimeForTest = Calendar.getInstance();
        currentTimeForTest.set(Calendar.HOUR_OF_DAY, 6);
        BusinessContextInfo info =
                mInCallContactInteractions.constructHoursInfo(
                        currentTimeForTest,
                        Pair.create("0800", "2000"));
        assertEquals(mContext.getString(R.string.closed_now), info.heading);
    }

    public void testIsClosedNow_AfterClosed() {
        Calendar currentTimeForTest = Calendar.getInstance();
        currentTimeForTest.set(Calendar.HOUR_OF_DAY, 21);
        BusinessContextInfo info =
                mInCallContactInteractions.constructHoursInfo(
                        currentTimeForTest,
                        Pair.create("0800", "2000"));
        assertEquals(mContext.getString(R.string.closed_now), info.heading);
    }

    public void testInvalidOpeningHours() {
        Calendar currentTimeForTest = Calendar.getInstance();
        currentTimeForTest.set(Calendar.HOUR_OF_DAY, 21);
        BusinessContextInfo info =
                mInCallContactInteractions.constructHoursInfo(
                        currentTimeForTest,
                        Pair.create("", "2000"));
        assertEquals(null, info);
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
}
