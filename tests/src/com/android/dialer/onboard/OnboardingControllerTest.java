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

import android.app.Activity;
import android.test.AndroidTestCase;

public class OnboardingControllerTest extends AndroidTestCase {
    private MockOnboardUi mOnboardUi;
    private OnboardingController mController;

    public class MockOnboardUi implements OnboardingController.OnboardingUi {
        public int currentScreen = -1;
        public boolean completedOnboardingFlow = false;

        @Override
        public void showScreen(int screenId) {
            currentScreen = screenId;
        }

        @Override
        public void completeOnboardingFlow() {
            completedOnboardingFlow = true;
        }
    }

    public class MockScreen extends OnboardingController.OnboardingScreen {
        boolean shouldShowScreen;
        boolean canSkipScreen;

        public MockScreen(boolean shouldShowScreen, boolean canSkipScreen) {
            this.shouldShowScreen = shouldShowScreen;
            this.canSkipScreen = canSkipScreen;
        }

        @Override
        public boolean shouldShowScreen() {
            return shouldShowScreen;
        }

        @Override
        public boolean canSkipScreen() {
            return canSkipScreen;
        }

        @Override
        public void onNextClicked(Activity activity) {
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mOnboardUi = new MockOnboardUi();
        mController = new OnboardingController(mOnboardUi);
    }

    public void testNoScreensToDisplay_OnboardingFlowImmediatelyCompleted() {
        mController.showNextScreen();
        assertEquals(-1, mOnboardUi.currentScreen);
        assertTrue(mOnboardUi.completedOnboardingFlow);
    }

    public void testSkipAllScreens_OnboardingFlowImmediatelyCompleted() {
        mController.addScreen(new MockScreen(false /* shouldShowScreen */,
                true /* canSkipScreen */));
        mController.addScreen(new MockScreen(false /* shouldShowScreen */,
                true /* canSkipScreen */));
        mController.addScreen(new MockScreen(false /* shouldShowScreen */,
                true /* canSkipScreen */));
        mController.showNextScreen();
        assertEquals(-1, mOnboardUi.currentScreen);
        assertTrue(mOnboardUi.completedOnboardingFlow);
    }

    public void testFirstScreenNotNeeded_ShowsSecondScreen() {
        mController.addScreen(new MockScreen(false /* shouldShowScreen */,
                false /* canSkipScreen */));
        mController.addScreen(new MockScreen(true /* shouldShowScreen */,
                false /* canSkipScreen */));
        mController.showNextScreen();
        assertEquals(1, mOnboardUi.currentScreen);
    }

    public void testScreenRequired() {
        final MockScreen mockScreen =
                new MockScreen(true /* shouldShowScreen */, false /* canSkipScreen */);
        mController.addScreen(mockScreen);

        mController.showNextScreen();
        assertEquals(0, mOnboardUi.currentScreen);
        assertFalse(mOnboardUi.completedOnboardingFlow);

        // User tried to skip an unskippable screen
        mController.onScreenResult(0, false);
        assertEquals(0, mOnboardUi.currentScreen);
        assertFalse(mOnboardUi.completedOnboardingFlow);

        // User said yes, but the underlying requirements have not been fulfilled yet, so don't
        // show the next screen. Should be very rare in practice.
        mController.onScreenResult(0, true);
        assertEquals(0, mOnboardUi.currentScreen);
        assertFalse(mOnboardUi.completedOnboardingFlow);

        // Requirement has been fulfiled.
        mockScreen.shouldShowScreen = false;
        mController.onScreenResult(0, true);
        assertTrue(mOnboardUi.completedOnboardingFlow);
    }

    /**
     * Verifies the use case where completing the first screen will provide the necessary conditions
     * to skip the second screen as well.
     *
     * For example, setting the default dialer in the first screen will automatically grant
     * permissions such that the second permissions screen is no longer needed.
     */
    public void testFirstScreenCompleted_SkipsSecondScreen() {
        final MockScreen mockScreen1 =
                new MockScreen(true /* shouldShowScreen */, true /* canSkipScreen */);
        final MockScreen mockScreen2 =
                new MockScreen(true /* shouldShowScreen */, false /* canSkipScreen */);
        mController.addScreen(mockScreen1);
        mController.addScreen(mockScreen2);

        mController.showNextScreen();
        assertEquals(0, mOnboardUi.currentScreen);
        assertFalse(mOnboardUi.completedOnboardingFlow);

        // Screen 1 succeeded, screen 2 is no longer necessary
        mockScreen2.shouldShowScreen = false;
        mController.onScreenResult(0, true);
        assertEquals(0, mOnboardUi.currentScreen);
        assertTrue(mOnboardUi.completedOnboardingFlow);
    }

    /**
     * Verifies the use case where skipping the first screen will proceed to show the second screen
     * since the necessary conditions to skip the second screen have not been met.
     */
    public void testFirstScreenSkipped_ShowsSecondScreen() {
        final MockScreen mockScreen1 =
                new MockScreen(true /* shouldShowScreen */, true /* canSkipScreen */);
        final MockScreen mockScreen2 =
                new MockScreen(true /* shouldShowScreen */, false /* canSkipScreen */);
        mController.addScreen(mockScreen1);
        mController.addScreen(mockScreen2);

        mController.showNextScreen();
        assertEquals(0, mOnboardUi.currentScreen);
        assertFalse(mOnboardUi.completedOnboardingFlow);

        // Screen 1 skipped
        mController.onScreenResult(0, false);
        assertEquals(1, mOnboardUi.currentScreen);
        assertFalse(mOnboardUi.completedOnboardingFlow);

        // Repeatedly trying to skip screen 2 will not work since it is marked as unskippable.
        mController.onScreenResult(1, false);
        assertEquals(1, mOnboardUi.currentScreen);
        assertFalse(mOnboardUi.completedOnboardingFlow);
    }
}
