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

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
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
    public static final String KEY_CALLING_ACTIVITY_INTENT =
            "key_calling_activity_intent";

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

        // Once we have completed the onboarding flow, relaunch the activity that called us, so
        // that we return the user to the intended activity.
        if (getIntent() != null && getIntent().getExtras() != null) {
            final Intent previousActivityIntent =
                    getIntent().getExtras().getParcelable(KEY_CALLING_ACTIVITY_INTENT);
            if (previousActivityIntent != null) {
                startActivity(previousActivityIntent);
            }
        }
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
    public boolean hasAlreadyRequestedDefaultDialer() {
        return getAlreadyRequestedDefaultDialerFromPreferences(this);
    }

    private static boolean getAlreadyRequestedDefaultDialerFromPreferences(Context context) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean(KEY_ALREADY_REQUESTED_DEFAULT_DIALER, false);
    }

    /**
     * Checks the current permissions/application state to determine if the
     * {@link OnboardingActivity} should be displayed. The onboarding flow should be launched if
     * the current application is NOT the system dialer AND any of these criteria are true.
     *
     * 1) The phone application is not currently the default dialer AND we have not
     * previously displayed a prompt to ask the user to set our app as the default dialer.
     * 2) The phone application does not have the Phone permission.
     * 3) The phone application does not have the Contacts permission.
     *
     * The exception if the current application is the system dialer applies because:
     *
     * 1) The system dialer must always provide immediate access to the dialpad to allow
     * emergency calls to be made.
     * 2) In order for the system dialer to require the onboarding flow, the user must have
     * intentionally removed certain permissions/selected a different dialer. This assumes the
     * that the user understands the implications of the actions previously taken. For example,
     * removing the Phone permission from the system dialer displays a dialog that warns the
     * user that this might break certain core phone functionality. Furthermore, various elements in
     * the Dialer will prompt the user to grant permissions as needed.
     *
     * @param context A valid context object.
     * @return {@code true} if the onboarding flow should be launched to request for the
     *         necessary permissions or prompt the user to make us the default dialer, {@code false}
     *         otherwise.
     */
    public static boolean shouldStartOnboardingActivity(Context context) {
        // Since there is no official TelecomManager API to check for the system dialer,
        // check to see if we have the system-only MODIFY_PHONE_STATE permission.
        if (PermissionsUtil.hasPermission(context, Manifest.permission.MODIFY_PHONE_STATE)) {
            return false;
        }

        return (!getAlreadyRequestedDefaultDialerFromPreferences(context)
                && !TelecomUtil.isDefaultDialer(context))
                        || !PermissionsUtil.hasPhonePermissions(context)
                        || !PermissionsUtil.hasContactsPermissions(context);
    }

    public static void startOnboardingActivity(Activity callingActivity) {
        final Intent intent = new Intent(callingActivity, OnboardingActivity.class);
        intent.putExtra(KEY_CALLING_ACTIVITY_INTENT, callingActivity.getIntent());
        callingActivity.startActivity(intent);
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
            return !mPermissionsChecker.hasAlreadyRequestedDefaultDialer()
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
                    R.drawable.ill_onboard_default,
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
                    R.drawable.ill_onboard_permissions,
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
