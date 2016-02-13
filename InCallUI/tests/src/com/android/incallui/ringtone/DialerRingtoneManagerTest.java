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
import android.net.Uri;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.common.compat.CompatUtils;
import com.android.incallui.Call;
import com.android.incallui.Call.State;
import com.android.incallui.CallList;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@SmallTest
public class DialerRingtoneManagerTest extends AndroidTestCase {

    private static final Uri RINGTONE_URI = RingtoneManager
            .getDefaultUri(RingtoneManager.TYPE_RINGTONE);

    @Mock private InCallTonePlayer mInCallTonePlayer;
    @Mock private CallList mCallList;
    @Mock private Call mCall;
    private DialerRingtoneManager mRingtoneManagerEnabled;
    private DialerRingtoneManager mRingtoneManagerDisabled;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        mRingtoneManagerEnabled = new DialerRingtoneManager(mInCallTonePlayer, mCallList);
        mRingtoneManagerEnabled.setDialerRingingEnabledForTesting(true);
        mRingtoneManagerDisabled = new DialerRingtoneManager(mInCallTonePlayer, mCallList);
        mRingtoneManagerDisabled.setDialerRingingEnabledForTesting(false);
    }

    public void testNullInCallTonePlayer() {
        try {
            new DialerRingtoneManager(null, mCallList);
            fail();
        } catch (NullPointerException e) {}
    }

    public void testNullCallList() {
        try {
            new DialerRingtoneManager(mInCallTonePlayer, null);
            fail();
        } catch (NullPointerException e) {}
    }

    public void testShouldPlayRingtone_M() {
        if (CompatUtils.isNCompatible()) {
            return;
        }
        assertFalse(mRingtoneManagerEnabled.shouldPlayRingtone(0, RINGTONE_URI));
    }

    public void testShouldPlayRingtone_N_NullUri() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        assertFalse(mRingtoneManagerEnabled.shouldPlayRingtone(State.INCOMING, null));
    }

    public void testShouldPlayRingtone_N_Disabled() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        assertFalse(mRingtoneManagerDisabled.shouldPlayRingtone(State.INCOMING, RINGTONE_URI));
    }

    public void testShouldPlayRingtone_N_NotIncoming() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        assertFalse(mRingtoneManagerEnabled.shouldPlayRingtone(State.ACTIVE, RINGTONE_URI));
    }

    // Specific case for call waiting since that needs its own sound
    public void testShouldPlayRingtone_N_CallWaitingByState() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        assertFalse(mRingtoneManagerEnabled.shouldPlayRingtone(State.CALL_WAITING, RINGTONE_URI));
    }

    public void testShouldPlayRingtone_N_CallWaitingByActiveCall() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        Mockito.when(mCallList.getActiveCall()).thenReturn(mCall);
        assertFalse(mRingtoneManagerEnabled.shouldPlayRingtone(State.INCOMING, RINGTONE_URI));
    }

    public void testShouldPlayRingtone_N() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        assertTrue(mRingtoneManagerEnabled.shouldPlayRingtone(State.INCOMING, RINGTONE_URI));
    }

    public void testShouldPlayCallWaitingTone_M() {
        if (CompatUtils.isNCompatible()) {
            return;
        }
        assertFalse(mRingtoneManagerEnabled.shouldPlayCallWaitingTone(0));
    }

    public void testShouldPlayCallWaitingTone_N_Disabled() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        assertFalse(mRingtoneManagerDisabled.shouldPlayCallWaitingTone(State.CALL_WAITING));
    }

    public void testShouldPlayCallWaitingTone_N_NotCallWaiting() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        assertFalse(mRingtoneManagerEnabled.shouldPlayCallWaitingTone(State.ACTIVE));
    }

    // Specific case for incoming since it plays its own sound
    public void testShouldPlayCallWaitingTone_N_Incoming() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        assertFalse(mRingtoneManagerEnabled.shouldPlayCallWaitingTone(State.INCOMING));
    }

    public void testShouldPlayCallWaitingTone_N_AlreadyPlaying() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        Mockito.when(mInCallTonePlayer.isPlayingTone()).thenReturn(true);
        assertFalse(mRingtoneManagerEnabled.shouldPlayCallWaitingTone(State.CALL_WAITING));
    }

    public void testShouldPlayCallWaitingTone_N_ByState() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        assertTrue(mRingtoneManagerEnabled.shouldPlayCallWaitingTone(State.CALL_WAITING));
    }

    public void testShouldPlayCallWaitingTone_N_ByActiveCall() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        Mockito.when(mCallList.getActiveCall()).thenReturn(mCall);
        assertTrue(mRingtoneManagerEnabled.shouldPlayCallWaitingTone(State.INCOMING));
    }

    public void testPlayCallWaitingTone_M() {
        if (CompatUtils.isNCompatible()) {
            return;
        }
        mRingtoneManagerEnabled.playCallWaitingTone();
        Mockito.verify(mInCallTonePlayer, Mockito.never()).play(Mockito.anyInt());
    }

    public void testPlayCallWaitingTone_N_NotEnabled() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        mRingtoneManagerDisabled.playCallWaitingTone();
        Mockito.verify(mInCallTonePlayer, Mockito.never()).play(Mockito.anyInt());
    }

    public void testPlayCallWaitingTone_N() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        mRingtoneManagerEnabled.playCallWaitingTone();
        Mockito.verify(mInCallTonePlayer).play(Mockito.anyInt());
    }

    public void testStopCallWaitingTone_M() {
        if (CompatUtils.isNCompatible()) {
            return;
        }
        mRingtoneManagerEnabled.stopCallWaitingTone();
        Mockito.verify(mInCallTonePlayer, Mockito.never()).stop();
    }

    public void testStopCallWaitingTone_N_NotEnabled() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        mRingtoneManagerDisabled.stopCallWaitingTone();
        Mockito.verify(mInCallTonePlayer, Mockito.never()).stop();
    }

    public void testStopCallWaitingTone_N() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        mRingtoneManagerEnabled.stopCallWaitingTone();
        Mockito.verify(mInCallTonePlayer).stop();
    }
}
