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

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.*;
import android.telecom.Call;
import android.test.AndroidTestCase;

import java.lang.reflect.Constructor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ExternalCallListTest extends AndroidTestCase {

    private static class Listener implements ExternalCallList.ExternalCallListener {
        private CountDownLatch mCallAddedLatch = new CountDownLatch(1);
        private CountDownLatch mCallRemovedLatch = new CountDownLatch(1);
        private CountDownLatch mCallUpdatedLatch = new CountDownLatch(1);

        @Override
        public void onExternalCallAdded(Call call) {
            mCallAddedLatch.countDown();
        }

        @Override
        public void onExternalCallRemoved(Call call) {
            mCallRemovedLatch.countDown();
        }

        @Override
        public void onExternalCallUpdated(Call call) {
            mCallUpdatedLatch.countDown();
        }

        public boolean awaitCallAdded() {
            try {
                return mCallAddedLatch.await(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }

        public boolean awaitCallRemoved() {
            try {
                return mCallRemovedLatch.await(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }

        public boolean awaitCallUpdated() {
            try {
                return mCallUpdatedLatch.await(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }
    }

    private static final int WAIT_TIMEOUT_MILLIS = 5000;

    private ExternalCallList mExternalCallList = new ExternalCallList();
    private Listener mExternalCallListener = new Listener();

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mExternalCallList.addExternalCallListener(mExternalCallListener);
    }

    public void testAddCallSuccess() {
        TestTelecomCall call = getTestCall(Call.Details.PROPERTY_IS_EXTERNAL_CALL);
        mExternalCallList.onCallAdded(call.getCall());
        assertTrue(mExternalCallListener.awaitCallAdded());
    }

    public void testAddCallFail() {
        TestTelecomCall call = getTestCall(0 /* no properties */);
        try {
            mExternalCallList.onCallAdded(call.getCall());
            fail();
        } catch (IllegalArgumentException e) {
        }
    }

    public void testUpdateCall() {
        TestTelecomCall call = getTestCall(Call.Details.PROPERTY_IS_EXTERNAL_CALL);
        mExternalCallList.onCallAdded(call.getCall());
        assertTrue(mExternalCallListener.awaitCallAdded());

        call.forceDetailsUpdate();
        assertTrue(mExternalCallListener.awaitCallUpdated());
    }

    public void testRemoveCall() {
        TestTelecomCall call = getTestCall(Call.Details.PROPERTY_IS_EXTERNAL_CALL);
        mExternalCallList.onCallAdded(call.getCall());
        assertTrue(mExternalCallListener.awaitCallAdded());

        mExternalCallList.onCallRemoved(call.getCall());
        assertTrue(mExternalCallListener.awaitCallRemoved());
    }

    private TestTelecomCall getTestCall(int properties) {
        TestTelecomCall testCall = TestTelecomCall.createInstance(
                "1",
                Uri.parse("tel:650-555-1212"), /* handle */
                TelecomManager.PRESENTATION_ALLOWED, /* handlePresentation */
                "Joe", /* callerDisplayName */
                TelecomManager.PRESENTATION_ALLOWED, /* callerDisplayNamePresentation */
                new PhoneAccountHandle(new ComponentName("test", "class"),
                        "handle"), /* accountHandle */
                Call.Details.CAPABILITY_CAN_PULL_CALL, /* capabilities */
                properties, /* properties */
                null, /* disconnectCause */
                0, /* connectTimeMillis */
                null, /* GatewayInfo */
                VideoProfile.STATE_AUDIO_ONLY, /* videoState */
                null, /* statusHints */
                null, /* extras */
                null /* intentExtras */);
        return testCall;
    }
}
