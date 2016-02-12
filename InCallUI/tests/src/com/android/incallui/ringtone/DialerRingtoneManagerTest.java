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
package com.android.incallui.ringtone;

import android.media.RingtoneManager;
import android.test.AndroidTestCase;

import com.android.contacts.common.compat.CompatUtils;
import com.android.incallui.Call.State;

public class DialerRingtoneManagerTest extends AndroidTestCase {

    private DialerRingtoneManager mRingtoneManager;

    @Override
    public void setUp() {
        mRingtoneManager = new DialerRingtoneManager();
        mRingtoneManager.forceDialerRingingEnabled();
    }

    @Override
    public void tearDown() {
        mRingtoneManager = null;
    }

    public void testShouldPlayRingtone_M() {
        if (CompatUtils.isNCompatible()) {
            return;
        }
        assertFalse(mRingtoneManager.shouldPlayRingtone(0, null));
    }

    public void testShouldPlayRingtone_N_NullUri() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        assertFalse(mRingtoneManager.shouldPlayRingtone(State.INCOMING, null));
    }

    public void testShouldPlayRingtone_N_NotIncoming() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        assertFalse(mRingtoneManager.shouldPlayRingtone(State.ACTIVE, null));
    }

    // Specific case for call waiting since that needs its own sound
    public void testShouldPlayRingtone_N_CallWaiting() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        assertFalse(mRingtoneManager.shouldPlayRingtone(State.CALL_WAITING, null));
    }

    public void testShouldPlayRingtone_N() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        assertTrue(mRingtoneManager.shouldPlayRingtone(State.INCOMING,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)));
    }

    public void testShouldPlayCallWaitingTone_M() {
        if (CompatUtils.isNCompatible()) {
            return;
        }
        assertFalse(mRingtoneManager.shouldPlayCallWaitingTone(0));
    }

    public void testShouldPlayCallWaitingTone_N_NotCallWaiting() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        assertFalse(mRingtoneManager.shouldPlayCallWaitingTone(State.ACTIVE));
    }

    // Specific case for incoming since it plays its own sound
    public void testShouldPlayCallWaitingTone_N_Incoming() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        assertFalse(mRingtoneManager.shouldPlayCallWaitingTone(State.INCOMING));
    }

    public void testShouldPlayCallWaitingTone_N() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        assertTrue(mRingtoneManager.shouldPlayCallWaitingTone(State.CALL_WAITING));
    }
}
