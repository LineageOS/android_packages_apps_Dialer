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

import java.util.ArrayList;

/**
 * Class that manages the display of various fragments that show the user prompts asking for
 * certain privileged positions.
 */
public class OnboardingController {
    public static abstract class OnboardingScreen {
        public abstract boolean shouldShowScreen();
        public abstract boolean canSkipScreen();
        public abstract void onNextClicked(Activity activity);
    }

    public interface OnboardingUi {
        public void showScreen(int screenId);
        /**
         * Called when all the necessary permissions have been granted and the main activity
         * can launch.
         */
        public void completeOnboardingFlow();
    }

    private int mCurrentScreen = -1;
    private OnboardingUi mOnboardingUi;
    private ArrayList<OnboardingScreen> mScreens = new ArrayList<> ();

    public OnboardingController(OnboardingUi onBoardingUi) {
        mOnboardingUi = onBoardingUi;
    }

    public void addScreen(OnboardingScreen screen) {
        mScreens.add(screen);
    }

    public void showNextScreen() {
        mCurrentScreen++;

        if (mCurrentScreen >= mScreens.size()) {
            // Reached the end of onboarding flow
            mOnboardingUi.completeOnboardingFlow();
            return;
        }

        if (mScreens.get(mCurrentScreen).shouldShowScreen()) {
            mOnboardingUi.showScreen(mCurrentScreen);
        } else {
            showNextScreen();
        }
    }

    public void onScreenResult(int screenId, boolean success) {
        if (screenId >= mScreens.size()) {
            return;
        }

        // Show the next screen in the onboarding flow only under the following situations:
        // 1) Success was indicated, and the
        // 2) The user tried to skip the screen, and the screen can be skipped
        if (success && !mScreens.get(mCurrentScreen).shouldShowScreen()) {
            showNextScreen();
        } else if (mScreens.get(mCurrentScreen).canSkipScreen()) {
            showNextScreen();
        }
    }
}
