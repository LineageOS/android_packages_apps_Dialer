/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package com.android.dialer.lookup;

import android.content.ContentResolver;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;

import com.android.dialer.R;

import lineageos.providers.LineageSettings;

import java.util.Arrays;

public class LookupSettingsFragment extends PreferenceFragment
    implements Preference.OnPreferenceChangeListener {

  private static final String KEY_ENABLE_FORWARD_LOOKUP = "enable_forward_lookup";
  private static final String KEY_ENABLE_PEOPLE_LOOKUP = "enable_people_lookup";
  private static final String KEY_ENABLE_REVERSE_LOOKUP = "enable_reverse_lookup";
  private static final String KEY_FORWARD_LOOKUP_PROVIDER = "forward_lookup_provider";
  private static final String KEY_PEOPLE_LOOKUP_PROVIDER = "people_lookup_provider";
  private static final String KEY_REVERSE_LOOKUP_PROVIDER = "reverse_lookup_provider";

  private SwitchPreference enableForwardLookup;
  private SwitchPreference enablePeopleLookup;
  private SwitchPreference enableReverseLookup;
  private ListPreference forwardLookupProvider;
  private ListPreference peopleLookupProvider;
  private ListPreference reverseLookupProvider;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    addPreferencesFromResource(R.xml.lookup_settings);

    enableForwardLookup = (SwitchPreference) findPreference(KEY_ENABLE_FORWARD_LOOKUP);
    enablePeopleLookup = (SwitchPreference) findPreference(KEY_ENABLE_PEOPLE_LOOKUP);
    enableReverseLookup = (SwitchPreference) findPreference(KEY_ENABLE_REVERSE_LOOKUP);

    enableForwardLookup.setOnPreferenceChangeListener(this);
    enablePeopleLookup.setOnPreferenceChangeListener(this);
    enableReverseLookup.setOnPreferenceChangeListener(this);

    forwardLookupProvider = (ListPreference) findPreference(KEY_FORWARD_LOOKUP_PROVIDER);
    peopleLookupProvider = (ListPreference) findPreference(KEY_PEOPLE_LOOKUP_PROVIDER);
    reverseLookupProvider = (ListPreference) findPreference(KEY_REVERSE_LOOKUP_PROVIDER);

    forwardLookupProvider.setOnPreferenceChangeListener(this);
    peopleLookupProvider.setOnPreferenceChangeListener(this);
    reverseLookupProvider.setOnPreferenceChangeListener(this);
  }

  @Override
  public void onResume() {
    super.onResume();

    restoreLookupProviderSwitches();
    restoreLookupProviders();
  }

  @Override
  public boolean onPreferenceChange(Preference preference, Object newValue) {
    final ContentResolver cr = getActivity().getContentResolver();

    if (preference == enableForwardLookup) {
      LineageSettings.System.putInt(cr, LineageSettings.System.ENABLE_FORWARD_LOOKUP,
          ((Boolean) newValue) ? 1 : 0);
    } else if (preference == enablePeopleLookup) {
      LineageSettings.System.putInt(cr, LineageSettings.System.ENABLE_PEOPLE_LOOKUP,
          ((Boolean) newValue) ? 1 : 0);
    } else if (preference == enableReverseLookup) {
      LineageSettings.System.putInt(cr, LineageSettings.System.ENABLE_REVERSE_LOOKUP,
          ((Boolean) newValue) ? 1 : 0);
    } else if (preference == forwardLookupProvider) {
      LineageSettings.System.putString(cr, LineageSettings.System.FORWARD_LOOKUP_PROVIDER,
          (String) newValue);
    } else if (preference == peopleLookupProvider) {
      LineageSettings.System.putString(cr, LineageSettings.System.PEOPLE_LOOKUP_PROVIDER,
          (String) newValue);
    } else if (preference == reverseLookupProvider) {
      LineageSettings.System.putString(cr, LineageSettings.System.REVERSE_LOOKUP_PROVIDER,
          (String) newValue);
    }

    return true;
  }

  private void restoreLookupProviderSwitches() {
    final ContentResolver cr = getActivity().getContentResolver();
    enableForwardLookup.setChecked(LineageSettings.System.getInt(cr,
        LineageSettings.System.ENABLE_FORWARD_LOOKUP, 0) != 0);
    enablePeopleLookup.setChecked(LineageSettings.System.getInt(cr,
        LineageSettings.System.ENABLE_PEOPLE_LOOKUP, 0) != 0);
    enableReverseLookup.setChecked(LineageSettings.System.getInt(cr,
        LineageSettings.System.ENABLE_REVERSE_LOOKUP, 0) != 0);
  }

  private void restoreLookupProviders() {
    restoreLookupProvider(forwardLookupProvider,
        LineageSettings.System.FORWARD_LOOKUP_PROVIDER);
    restoreLookupProvider(peopleLookupProvider,
        LineageSettings.System.PEOPLE_LOOKUP_PROVIDER);
    restoreLookupProvider(reverseLookupProvider,
        LineageSettings.System.REVERSE_LOOKUP_PROVIDER);
  }

  private void restoreLookupProvider(ListPreference pref, String key) {
    if (pref.getEntries().length < 1) {
      pref.setEnabled(false);
      return;
    }

    final ContentResolver cr = getActivity().getContentResolver();
    String provider = LineageSettings.System.getString(cr, key);
    if (provider == null) {
      pref.setValueIndex(0);
      LineageSettings.System.putString(cr, key, pref.getValue());
    } else {
      pref.setValue(provider);
    }
  }
}
