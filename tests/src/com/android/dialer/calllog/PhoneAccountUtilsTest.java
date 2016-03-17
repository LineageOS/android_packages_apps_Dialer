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
 * limitations under the License.
 */

package com.android.dialer.calllog;

import android.content.ComponentName;
import android.telecom.PhoneAccountHandle;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

public class PhoneAccountUtilsTest extends AndroidTestCase {

    private static final String VALID_COMPONENT_NAME =
            "com.android.dialer.calllog/com.android.dialer.calllog.PhoneAccountUtilsTest";
    private static final String VALID_ACCOUNT_ID = "Account1";

    @SmallTest
    public void testGetAccount_CorrectParams() {
        ComponentName correctComponentName =
                ComponentName.unflattenFromString(VALID_COMPONENT_NAME);
        PhoneAccountHandle correctPhoneAccountHandle = new PhoneAccountHandle(correctComponentName,
                VALID_ACCOUNT_ID);

        PhoneAccountHandle testPhoneAccountHandle =
                PhoneAccountUtils.getAccount(VALID_COMPONENT_NAME, VALID_ACCOUNT_ID);

        assertTrue(correctPhoneAccountHandle.equals(testPhoneAccountHandle));
    }

    @SmallTest
    public void testGetAccount_ComponentStringNoClassName() {
        final String malformedComponentName = "com.android.dialer.calllog/";

        PhoneAccountHandle testPhoneAccountHandle =
                PhoneAccountUtils.getAccount(malformedComponentName, VALID_ACCOUNT_ID);

        assertNull(testPhoneAccountHandle);
    }

    @SmallTest
    public void testGetAccount_ComponentStringInvalid() {
        final String malformedComponentName = "com.android.dialer.calllog";

        PhoneAccountHandle testPhoneAccountHandle =
                PhoneAccountUtils.getAccount(malformedComponentName, VALID_ACCOUNT_ID);

        assertNull(testPhoneAccountHandle);
    }

    @SmallTest
    public void testGetAccount_NoComponentName() {
        final String blankComponentName = "";

        PhoneAccountHandle testPhoneAccountHandle =
                PhoneAccountUtils.getAccount(blankComponentName, VALID_ACCOUNT_ID);

        assertNull(testPhoneAccountHandle);
    }

    @SmallTest
    public void testGetAccount_NoAccountId() {
        final String blankAccountId = "";

        PhoneAccountHandle testPhoneAccountHandle =
                PhoneAccountUtils.getAccount(VALID_COMPONENT_NAME, blankAccountId);

        assertNull(testPhoneAccountHandle);
    }

    @SmallTest
    public void testGetAccount_NoAccountIdOrComponentName() {
        final String blankComponentName = "";
        final String blankAccountId = "";

        PhoneAccountHandle testPhoneAccountHandle =
                PhoneAccountUtils.getAccount(VALID_COMPONENT_NAME, blankAccountId);

        assertNull(testPhoneAccountHandle);
    }

    @SmallTest
    public void testGetAccount_NullAccountIdAndComponentName() {
        final String blankComponentName = null;
        final String blankAccountId = null;

        PhoneAccountHandle testPhoneAccountHandle =
                PhoneAccountUtils.getAccount(VALID_COMPONENT_NAME, blankAccountId);

        assertNull(testPhoneAccountHandle);
    }
}
