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
 * limitations under the License
 */

package com.android.incallui;

import static com.android.incallui.LatencyReport.INVALID_TIME;

import android.os.Bundle;
import android.telecom.Connection;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.ArrayList;
import java.util.Arrays;

public class LatencyReportTest extends AndroidTestCase {
    public void testEmptyInit() {
        LatencyReport report = new LatencyReport();
        assertEquals(INVALID_TIME, report.getCreatedTimeMillis());
        assertEquals(INVALID_TIME, report.getTelecomRoutingStartTimeMillis());
        assertEquals(INVALID_TIME, report.getTelecomRoutingEndTimeMillis());
        assertTrue(report.getCallAddedTimeMillis() > 0);
    }

    public void testCallBlocking() {
        LatencyReport report = new LatencyReport();
        assertEquals(INVALID_TIME, report.getCallBlockingTimeMillis());
        report.onCallBlockingDone();
        assertTrue(report.getCallBlockingTimeMillis() > 0);
    }

    public void testNotificationShown() {
        LatencyReport report = new LatencyReport();
        assertEquals(INVALID_TIME, report.getCallNotificationTimeMillis());
        report.onNotificationShown();
        assertTrue(report.getCallNotificationTimeMillis() > 0);
    }

    public void testInCallUiShown() {
        LatencyReport report = new LatencyReport();
        assertEquals(INVALID_TIME, report.getInCallUiShownTimeMillis());
        report.onInCallUiShown(false);
        assertTrue(report.getInCallUiShownTimeMillis() > 0);
        assertFalse(report.getDidDisplayHeadsUpNotification());
    }
}
