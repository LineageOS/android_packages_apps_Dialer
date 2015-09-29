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
package com.android.dialer.onboard;

import static org.mockito.Mockito.when;

import android.test.AndroidTestCase;

import com.android.dialer.onboard.OnboardingActivity.PermissionsOnboardingScreen;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PermissionsOnboardScreenTest extends AndroidTestCase {
    private PermissionsOnboardingScreen mScreen;
    @Mock private PermissionsChecker mPermissionsChecker;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        mScreen = new PermissionsOnboardingScreen(mPermissionsChecker);
    }

    public void testMissingContactsAndPhonePermissions_shouldShowScreen() {
        when(mPermissionsChecker.hasContactsPermissions()).thenReturn(false);
        when(mPermissionsChecker.hasPhonePermissions()).thenReturn(false);
        assertTrue(mScreen.shouldShowScreen());
    }

    public void testMissingContactsPermission_shouldShowScreen() {
        when(mPermissionsChecker.hasContactsPermissions()).thenReturn(false);
        when(mPermissionsChecker.hasPhonePermissions()).thenReturn(true);
        assertTrue(mScreen.shouldShowScreen());
    }

    public void testMissingPhonePermission_shouldShowScreen() {
        when(mPermissionsChecker.hasContactsPermissions()).thenReturn(true);
        when(mPermissionsChecker.hasPhonePermissions()).thenReturn(false);
        assertTrue(mScreen.shouldShowScreen());
    }

    public void testHasAllPermissions_shouldNotShowScreen() {
        when(mPermissionsChecker.hasContactsPermissions()).thenReturn(true);
        when(mPermissionsChecker.hasPhonePermissions()).thenReturn(true);
        assertFalse(mScreen.shouldShowScreen());
    }

    public void testCanSkipScreen() {
        assertFalse(mScreen.canSkipScreen());
    }
}
