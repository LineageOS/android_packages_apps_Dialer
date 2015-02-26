package com.android.dialer.settings;

import com.google.common.collect.Lists;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceActivity.Header;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.MenuItem;

import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;

import java.util.List;

public class DialerSettingsActivity extends PreferenceActivity {

    protected SharedPreferences mPreferences;

    private static final int OWNER_HANDLE_ID = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        Header displayOptionsHeader = new Header();
        displayOptionsHeader.titleRes = R.string.display_options_title;
        displayOptionsHeader.fragment = DisplayOptionsSettingsFragment.class.getName();
        target.add(displayOptionsHeader);

        Header generalSettingsHeader = new Header();
        generalSettingsHeader.titleRes = R.string.general_settings_label;
        generalSettingsHeader.fragment = GeneralSettingsFragment.class.getName();
        target.add(generalSettingsHeader);

        Header quickResponseSettingsHeader = new Header();
        Intent quickResponseSettingsIntent =
                new Intent(TelecomManager.ACTION_SHOW_RESPOND_VIA_SMS_SETTINGS);
        quickResponseSettingsHeader.titleRes = R.string.respond_via_sms_setting_title;
        quickResponseSettingsHeader.intent = quickResponseSettingsIntent;
        target.add(quickResponseSettingsHeader);

        // Only show call setting menus if the current user is the primary/owner user.
        if (isPrimaryUser()) {
            TelephonyManager telephonyManager =
                    (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            // Show "Call Settings" if there is one SIM and "Phone Accounts" if there are more.
            if (telephonyManager.getPhoneCount() <= 1) {
                Header callSettingsHeader = new Header();
                Intent callSettingsIntent = new Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS);
                callSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                callSettingsHeader.titleRes = R.string.call_settings_label;
                callSettingsHeader.intent = callSettingsIntent;
                target.add(callSettingsHeader);
            } else {
                Header phoneAccountSettingsHeader = new Header();
                Intent phoneAccountSettingsIntent =
                        new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
                phoneAccountSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                phoneAccountSettingsHeader.titleRes = R.string.phone_account_settings_label;
                phoneAccountSettingsHeader.intent = phoneAccountSettingsIntent;
                target.add(phoneAccountSettingsHeader);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return false;
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return true;
    }

    /**
     * Whether a user handle associated with the current user is that of the primary owner. That is,
     * whether there is a user handle which has an id which matches the owner's handle.
     * @return Whether the current user is the primary user.
     */
    private boolean isPrimaryUser() {
        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        List<UserHandle> userHandles = userManager.getUserProfiles();
        for (int i = 0; i < userHandles.size(); i++){
            if (userHandles.get(i).myUserId() == OWNER_HANDLE_ID) {
                return true;
            }
        }

        return false;
    }
}
