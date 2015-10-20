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

package com.android.dialer.filterednumber;

import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.LinkedList;

@SmallTest
public class FilteredNumbersUtilTest extends AndroidTestCase {

    private static final String NORMAL_PHONE_NUMBER = "555-555-5555";
    private static final String VOICEMAIL_NUMBER = "*555";

    @Mock private Context mContext;
    @Mock private TelecomManager mTelecomManager;
    private PhoneAccountHandle mHandle;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemService(Context.TELECOM_SERVICE)).thenReturn(mTelecomManager);

        LinkedList<PhoneAccountHandle> handles = new LinkedList<PhoneAccountHandle>();
        mHandle = new PhoneAccountHandle(new ComponentName(getContext(), "TestCase"), "0");
        handles.add(mHandle);

        when(mTelecomManager.getCallCapablePhoneAccounts()).thenReturn(handles);
    }

    public void testCanBlockNormalNumber() {
        assertTrue(FilteredNumbersUtil.canBlockNumber(mContext, NORMAL_PHONE_NUMBER));
    }

    public void testCannotBlockVoicemailNumber() {
        when(mTelecomManager.isVoiceMailNumber(Matchers.eq(mHandle), Matchers.anyString()))
                .thenReturn(true);
        assertFalse(FilteredNumbersUtil.canBlockNumber(mContext, VOICEMAIL_NUMBER));
    }
}
