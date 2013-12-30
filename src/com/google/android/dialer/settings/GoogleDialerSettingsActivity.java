/*
  * Copyright (C) 2013 Xiao-Long Chen <chenxiaolong@cxl.epac.to>
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

/*
 * This is a reverse-engineered implementation of com.google.android.Dialer.
 * There is no guarantee that this implementation will work correctly or even
 * work at all. Use at your own risk.
 */

package com.google.android.dialer.settings;

import com.android.dialer.R;

import com.android.dialer.DialtactsActivity;
import com.google.android.dialer.reverselookup.ReverseLookupSettingUtil;
import com.google.android.gsf.Gservices;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;

import java.util.List;

public class GoogleDialerSettingsActivity extends PreferenceActivity {
    private PreferenceActivity.Header mGoogleCallerIdHeader;
    private PreferenceActivity.Header mLocalSearchHeader;
    private SharedPreferences mPreferences;

    private void updateHeaders() {
        ListAdapter adapter = getListAdapter();
        if (adapter instanceof BaseAdapter) {
            if (mLocalSearchHeader != null) {
                int summaryRes;
                if (mPreferences.getBoolean("local_search", true)) {
                    summaryRes = R.string.local_search_setting_on;
                } else {
                    summaryRes = R.string.local_search_setting_off;
                }
                mLocalSearchHeader.summaryRes = summaryRes;
            }
            if (mGoogleCallerIdHeader != null) {
                int summaryRes;
                if (mPreferences.getBoolean("google_caller_id", true)) {
                    summaryRes = R.string.google_caller_id_setting__on;
                } else {
                    summaryRes = R.string.google_caller_id_setting__off;
                }
                mGoogleCallerIdHeader.summaryRes = summaryRes;
            }
            ((BaseAdapter) adapter).notifyDataSetChanged();
        }
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return true;
    }

    @Override
    public void onBuildHeaders(List<PreferenceActivity.Header> target) {
        if (ReverseLookupSettingUtil.isGServiceEnabled(this)) {
            mGoogleCallerIdHeader = new PreferenceActivity.Header();
            mGoogleCallerIdHeader.titleRes = R.string.google_caller_id_setting_title;
            mGoogleCallerIdHeader.fragment = GoogleCallerIdSettingsFragment.class.getName();
            target.add(mGoogleCallerIdHeader);
        }
        if (Gservices.getBoolean(getContentResolver(),
                    "dialer_enable_nearby_places_directory", true)) {
            mLocalSearchHeader = new PreferenceActivity.Header();
            mLocalSearchHeader.titleRes = R.string.local_search_setting_title;
            mLocalSearchHeader.fragment = LocalSearchSettingsFragment.class.getName();
            target.add(mLocalSearchHeader);
        }
        PreferenceActivity.Header header = new PreferenceActivity.Header();
        header.titleRes = R.string.call_settings_label;
        header.intent = DialtactsActivity.getCallSettingsIntent();
        target.add(header);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 16908332) { // TODO: What is 0x102002c?
            onBackPressed();
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHeaders();
    }
}
