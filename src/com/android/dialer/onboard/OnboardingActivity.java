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

import static android.Manifest.permission.CALL_PHONE;
import static android.Manifest.permission.READ_CONTACTS;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telecom.TelecomManager;

import com.android.contacts.common.util.PermissionsUtil;
import com.android.dialer.TransactionSafeActivity;
import com.android.dialer.onboard.OnboardingController.OnboardingScreen;
import com.android.dialer.onboard.OnboardingController.OnboardingUi;
import com.android.dialer.util.TelecomUtil;
import com.android.dialer.R;

/**
 * Activity hosting the onboarding UX flow that appears when you launch Dialer and you don't have
 * the necessary permissions to run the app.
 */
public class OnboardingActivity extends TransactionSafeActivity implements OnboardingUi,
        PermissionsChecker, OnboardingFragment.HostInterface {
    public static final String KEY_ALREADY_REQUESTED_DEFAULT_DIALER =
            "key_already_requested_default_dialer";

    public static final int SCREEN_DEFAULT_DIALER = 0;
    public static final int SCREEN_PERMISSIONS = 1;
    public static final int SCREEN_COUNT = 2;

    private OnboardingController mOnboardingController;

    private DefaultDialerOnboardingScreen mDefaultDialerOnboardingScreen;
    private PermissionsOnboardingScreen mPermissionsOnboardingScreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.onboarding_activity);
        mOnboardingController = new OnboardingController(this);
        mDefaultDialerOnboardingScreen = new DefaultDialerOnboardingScreen(this);
        mPermissionsOnboardingScreen = new PermissionsOnboardingScreen(this);
        mOnboardingController.addScreen(mDefaultDialerOnboardingScreen);
        mOnboardingController.addScreen(mPermissionsOnboardingScreen);

        mOnboardingController.showNextScreen();
    }

    @Override
    public void showScreen(int screenId) {
        if (!isSafeToCommitTransactions()) {
            return;
        }
        final Fragment fragment;
        switch (screenId) {
            case SCREEN_DEFAULT_DIALER:
                fragment = mDefaultDialerOnboardingScreen.getFragment();
                break;
            case SCREEN_PERMISSIONS:
                fragment = mPermissionsOnboardingScreen.getFragment();
                break;
            default:
                return;
        }

        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
        ft.replace(R.id.onboarding_fragment_container, fragment);
        ft.commit();
    }

    @Override
    public void completeOnboardingFlow() {
        final Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putBoolean(KEY_ALREADY_REQUESTED_DEFAULT_DIALER, true).apply();
        finish();
    }

    @Override
    public boolean hasPhonePermissions() {
        return PermissionsUtil.hasPhonePermissions(this);
    }

    @Override
    public boolean hasContactsPermissions() {
        return PermissionsUtil.hasContactsPermissions(this);
    }

    @Override
    public boolean isDefaultOrSystemDialer() {
        return TelecomUtil.hasModifyPhoneStatePermission(this);
    }

    @Override
    public boolean previouslyRequestedDefaultDialer() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        return preferences.getBoolean(KEY_ALREADY_REQUESTED_DEFAULT_DIALER, false);
    }

    /**
     * Triggers the screen-specific logic that should occur when the next button is clicked.
     */
    @Override
    public void onNextClicked(int screenId) {
        switch (screenId) {
            case SCREEN_DEFAULT_DIALER:
                mDefaultDialerOnboardingScreen.onNextClicked(this);
                break;
            case SCREEN_PERMISSIONS:
                mPermissionsOnboardingScreen.onNextClicked(this);
                break;
            default:
                return;
        }
    }

    @Override
    public void onSkipClicked(int screenId) {
        mOnboardingController.onScreenResult(screenId, false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SCREEN_DEFAULT_DIALER
                && resultCode == RESULT_OK) {
            mOnboardingController.onScreenResult(SCREEN_DEFAULT_DIALER, true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        boolean allPermissionsGranted = true;
        if (requestCode == SCREEN_PERMISSIONS) {
            if (permissions.length == 0 && grantResults.length == 0) {
                // Cancellation of permissions dialog
                allPermissionsGranted = false;
            } else {
                for (int result : grantResults) {
                    if (result == PackageManager.PERMISSION_DENIED) {
                        allPermissionsGranted = false;
                    }
                }
            }

            if (allPermissionsGranted) {
                mOnboardingController.onScreenResult(SCREEN_PERMISSIONS, true);
            }
        }
    }

    public static class DefaultDialerOnboardingScreen extends OnboardingScreen {
        private PermissionsChecker mPermissionsChecker;

        public DefaultDialerOnboardingScreen(PermissionsChecker permissionsChecker) {
            mPermissionsChecker = permissionsChecker;
        }

        @Override
        public boolean shouldShowScreen() {
            return !mPermissionsChecker.previouslyRequestedDefaultDialer()
                    && !mPermissionsChecker.isDefaultOrSystemDialer();
        }

        @Override
        public boolean canSkipScreen() {
            return true;
        }

        public Fragment getFragment() {
            return new OnboardingFragment(
                    SCREEN_DEFAULT_DIALER,
                    canSkipScreen(),
                    R.color.onboarding_default_dialer_screen_background_color,
                    R.string.request_default_dialer_screen_title,
                    R.string.request_default_dialer_screen_content
            );
        }

        @Override
        public void onNextClicked(Activity activity) {
            final Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
            intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                    activity.getPackageName());
            activity.startActivityForResult(intent, SCREEN_DEFAULT_DIALER);
        }
    }

    public static class PermissionsOnboardingScreen extends OnboardingScreen {
        private PermissionsChecker mPermissionsChecker;

        public PermissionsOnboardingScreen(PermissionsChecker permissionsChecker) {
            mPermissionsChecker = permissionsChecker;
        }

        @Override
        public boolean shouldShowScreen() {
            return !(mPermissionsChecker.hasPhonePermissions()
                    && mPermissionsChecker.hasContactsPermissions());
        }

        @Override
        public boolean canSkipScreen() {
            return false;
        }

        public Fragment getFragment() {
            return new OnboardingFragment(
                    SCREEN_PERMISSIONS,
                    canSkipScreen(),
                    R.color.onboarding_permissions_screen_background_color,
                    R.string.request_permissions_screen_title,
                    R.string.request_permissions_screen_content
            );
        }

        @Override
        public void onNextClicked(Activity activity) {
            activity.requestPermissions(new String[] {CALL_PHONE, READ_CONTACTS},
                    SCREEN_PERMISSIONS);
        }
    }
}
