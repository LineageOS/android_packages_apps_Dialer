package com.android.dialer.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceActivity.Header;
import android.view.MenuItem;

import com.android.contacts.common.preference.DisplayOptionsPreferenceFragment;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;

import java.util.List;

public class DialerSettingsActivity extends PreferenceActivity {

    protected SharedPreferences mPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        final Header contactDisplayHeader = new Header();
        contactDisplayHeader.titleRes = R.string.settings_contact_display_options_title;
        contactDisplayHeader.summaryRes = R.string.settings_contact_display_options_description;
        contactDisplayHeader.fragment = DisplayOptionsPreferenceFragment.class.getName();
        target.add(contactDisplayHeader);

        final Header callSettingHeader = new Header();
        callSettingHeader.titleRes = R.string.call_settings_label;
        callSettingHeader.summaryRes = R.string.call_settings_description;
        callSettingHeader.intent = DialtactsActivity.getCallSettingsIntent();
        target.add(callSettingHeader);
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
}
