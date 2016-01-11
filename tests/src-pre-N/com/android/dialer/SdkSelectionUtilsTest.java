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


package com.android.dialer;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

// @formatter:off
/**
 * Run test with
 * adb shell am instrument -e class com.android.dialer.SdkSelectionUtilsTest -w com.google.android.dialer.tests/android.test.InstrumentationTestRunner
 */
// @formatter:on
@SmallTest
public class SdkSelectionUtilsTest extends AndroidTestCase {

    public void testTargetNSdk_False() {
        assertFalse(SdkSelectionUtils.TARGET_N_SDK);
    }
}