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

import android.test.AndroidTestCase;
import android.util.Pair;

import com.android.incallui.InCallContactInteractions.BusinessContextInfo;

import java.util.Calendar;

public class InCallContactInteractionsTest extends AndroidTestCase {
    private InCallContactInteractions mInCallContactInteractions;

    @Override
    protected void setUp() {
        mInCallContactInteractions = new InCallContactInteractions(mContext, true /* isBusiness */);
    }

    public void testIsOpenNow() {
        Calendar currentTimeForTest = Calendar.getInstance();
        currentTimeForTest.set(Calendar.HOUR_OF_DAY, 10);
        BusinessContextInfo info =
                mInCallContactInteractions.constructHoursInfoByTime(
                        currentTimeForTest,
                        Pair.create("0800", "2000"));
        assertEquals(mContext.getString(R.string.open_now), info.heading);
    }

    public void testIsClosedNow_BeforeOpen() {
        Calendar currentTimeForTest = Calendar.getInstance();
        currentTimeForTest.set(Calendar.HOUR_OF_DAY, 6);
        BusinessContextInfo info =
                mInCallContactInteractions.constructHoursInfoByTime(
                        currentTimeForTest,
                        Pair.create("0800", "2000"));
        assertEquals(mContext.getString(R.string.closed_now), info.heading);
    }

    public void testIsClosedNow_AfterClosed() {
        Calendar currentTimeForTest = Calendar.getInstance();
        currentTimeForTest.set(Calendar.HOUR_OF_DAY, 21);
        BusinessContextInfo info =
                mInCallContactInteractions.constructHoursInfoByTime(
                        currentTimeForTest,
                        Pair.create("0800", "2000"));
        assertEquals(mContext.getString(R.string.closed_now), info.heading);
    }

    public void testInvalidOpeningHours() {
        Calendar currentTimeForTest = Calendar.getInstance();
        currentTimeForTest.set(Calendar.HOUR_OF_DAY, 21);
        BusinessContextInfo info =
                mInCallContactInteractions.constructHoursInfoByTime(
                        currentTimeForTest,
                        Pair.create("", "2000"));
        assertEquals(null, info);
    }
}
