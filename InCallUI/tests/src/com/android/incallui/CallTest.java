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

import android.os.Bundle;
import android.telecom.Connection;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.ArrayList;
import java.util.Arrays;

// @formatter:off
/**
 * Run test with
 * adb shell am instrument -e class com.android.incallui.CallTest -w com.google.android.dialer.tests/android.test.InstrumentationTestRunner
 */
// @formatter:on

@SmallTest
public class CallTest extends AndroidTestCase {

    private TestCall mCall;

    private final static String CHILD_NUMBER = "123";
    private final static ArrayList<String> LAST_FORWARDED_NUMBER_LIST =
            new ArrayList(Arrays.asList("456", "789"));
    private final static String LAST_FORWARDED_NUMBER = "789";
    private final static String CALL_SUBJECT = "foo";

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mCall = new TestCall();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testUpdateFromCallExtras() {
        mCall.updateFromCallExtras(getTestBundle());
        verifyTestBundleResult();
    }

    public void testUpdateFromCallExtras_corruptedBundle() {
        mCall.setBundleCorrupted(true);
        mCall.updateFromCallExtras(getTestBundle());

        assertEquals(mCall.getChildNumber(), null);
        assertEquals(mCall.getLastForwardedNumber(), null);
        assertEquals(mCall.getCallSubject(), null);
    }

    public void testUpdateFromCallExtras_corruptedBundleOverwrite() {

        mCall.updateFromCallExtras(getTestBundle());
        mCall.setBundleCorrupted(true);
        Bundle bundle = new Bundle();
        bundle.putString(Connection.EXTRA_CHILD_ADDRESS, "321");
        bundle.putStringArrayList(Connection.EXTRA_LAST_FORWARDED_NUMBER,
                new ArrayList(Arrays.asList("654", "987")));
        bundle.putString(Connection.EXTRA_CALL_SUBJECT, "bar");
        mCall.updateFromCallExtras(bundle);
        //corrupted bundle should not overwrite existing values.
        verifyTestBundleResult();
    }

    private Bundle getTestBundle() {
        Bundle bundle = new Bundle();
        bundle.putString(Connection.EXTRA_CHILD_ADDRESS, CHILD_NUMBER);
        bundle.putStringArrayList(Connection.EXTRA_LAST_FORWARDED_NUMBER,
                LAST_FORWARDED_NUMBER_LIST);
        bundle.putString(Connection.EXTRA_CALL_SUBJECT, CALL_SUBJECT);
        return bundle;
    }

    private void verifyTestBundleResult() {
        assertEquals(CHILD_NUMBER, mCall.getChildNumber());
        assertEquals(LAST_FORWARDED_NUMBER, mCall.getLastForwardedNumber());
        assertEquals(CALL_SUBJECT, mCall.getCallSubject());
    }

    private class TestCall extends Call {

        private boolean mBundleCorrupted = false;

        public TestCall() {
            super(Call.State.NEW);
        }

        @Override
        public void updateFromCallExtras(Bundle bundle) {
            super.updateFromCallExtras(bundle);
        }

        public void setBundleCorrupted(boolean value) {
            this.mBundleCorrupted = value;
        }

        @Override
        protected boolean areCallExtrasCorrupted(Bundle callExtras) {
            if (mBundleCorrupted) {
                return true;
            }
            return super.areCallExtrasCorrupted(callExtras);
        }
    }
}
