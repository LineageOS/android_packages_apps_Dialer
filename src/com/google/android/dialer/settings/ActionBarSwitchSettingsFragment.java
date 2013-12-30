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

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

public class ActionBarSwitchSettingsFragment
        extends Fragment implements CompoundButton.OnCheckedChangeListener {
    protected SharedPreferences mPreferences;
    private Switch mActionBarSwitch;
    private boolean mDefaultValue;
    private String mSettingKey;

    public ActionBarSwitchSettingsFragment(String settingsKey, boolean defaultValue) {
        mSettingKey = settingsKey;
        mDefaultValue = defaultValue;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Activity activity = getActivity();
        Resources resources = getResources();

        mPreferences = PreferenceManager.getDefaultSharedPreferences(activity);

        mActionBarSwitch = new Switch(activity);
        ((TextView) mActionBarSwitch).setPadding(0, 0,
                resources.getDimensionPixelSize(
                        R.dimen.action_bar_switch_padding_right), 0);

        ((CompoundButton) mActionBarSwitch).setOnCheckedChangeListener(this);

        mActionBarSwitch.setChecked(mPreferences.getBoolean(mSettingKey, mDefaultValue));
        ActionBar actionBar = activity.getActionBar();
        actionBar.setDisplayOptions(
                ActionBar.DISPLAY_HOME_AS_UP |
                ActionBar.DISPLAY_SHOW_CUSTOM,
                ActionBar.DISPLAY_HOME_AS_UP |
                ActionBar.DISPLAY_SHOW_CUSTOM);
        actionBar.setCustomView(mActionBarSwitch,
                new ActionBar.LayoutParams(
                ActionBar.LayoutParams.WRAP_CONTENT,
                ActionBar.LayoutParams.WRAP_CONTENT,
                /* 0x800015 */
                Gravity.CENTER_VERTICAL |
                Gravity.END));
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        mPreferences.edit().putBoolean(mSettingKey, isChecked).apply();
    }
}
