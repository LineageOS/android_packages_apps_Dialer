/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.dialer.assisteddialing.ui;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.text.TextUtils;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;

/** The setting for Assisted Dialing */
public class AssistedDialingSettingFragment extends PreferenceFragment {

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Load the preferences from an XML resource
    addPreferencesFromResource(R.xml.assisted_dialing_setting);
    SwitchPreference switchPref =
        (SwitchPreference)
            findPreference(getContext().getString(R.string.assisted_dialing_setting_toggle_key));

    ListPreference countryChooserPref =
        (ListPreference)
            findPreference(getContext().getString(R.string.assisted_dialing_setting_cc_key));

    if (!TextUtils.isEmpty(countryChooserPref.getEntry())) {
      countryChooserPref.setSummary(countryChooserPref.getEntry());
    }
    countryChooserPref.setOnPreferenceChangeListener(this::updateListSummary);
    switchPref.setOnPreferenceChangeListener(this::logIfUserDisabledFeature);
  }

  boolean updateListSummary(Preference pref, Object newValue) {
    ListPreference listPref = (ListPreference) pref;
    CharSequence[] entries = listPref.getEntries();
    listPref.setSummary(entries[listPref.findIndexOfValue(newValue.toString())]);
    return true;
  }

  boolean logIfUserDisabledFeature(Preference pref, Object newValue) {
    if (!((boolean) newValue)) {
      Logger.get(getActivity().getApplicationContext())
          .logImpression(DialerImpression.Type.ASSISTED_DIALING_FEATURE_DISABLED_BY_USER);
    }

    return true;
  }
}
