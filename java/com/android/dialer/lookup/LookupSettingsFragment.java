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

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;

import com.android.dialer.R;

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
    Context context = getContext();

    if (preference == enableForwardLookup) {
      LookupSettings.setForwardLookupEnabled(context, (Boolean) newValue);
    } else if (preference == enablePeopleLookup) {
      LookupSettings.setPeopleLookupEnabled(context, (Boolean) newValue);
    } else if (preference == enableReverseLookup) {
      LookupSettings.setReverseLookupEnabled(context, (Boolean) newValue);
    } else if (preference == forwardLookupProvider) {
      LookupSettings.setForwardLookupProvider(context, (String) newValue);
    } else if (preference == peopleLookupProvider) {
      LookupSettings.setPeopleLookupProvider(context, (String) newValue);
    } else if (preference == reverseLookupProvider) {
      LookupSettings.setReverseLookupProvider(context, (String) newValue);
    }

    return true;
  }

  private void restoreLookupProviderSwitches() {
    Context context = getContext();

    enableForwardLookup.setChecked(LookupSettings.isForwardLookupEnabled(context));
    enablePeopleLookup.setChecked(LookupSettings.isPeopleLookupEnabled(context));
    enableReverseLookup.setChecked(LookupSettings.isReverseLookupEnabled(context));
  }

  private void restoreLookupProviders() {
    Context context = getContext();

    restoreLookupProvider(forwardLookupProvider, LookupSettings.getForwardLookupProvider(context));
    restoreLookupProvider(peopleLookupProvider, LookupSettings.getPeopleLookupProvider(context));
    restoreLookupProvider(reverseLookupProvider, LookupSettings.getReverseLookupProvider(context));
  }

  private void restoreLookupProvider(ListPreference pref, String provider) {
    Context context = getContext();

    if (pref.getEntries().length < 1) {
      pref.setEnabled(false);
      return;
    }

    if (provider == null) {
      pref.setValueIndex(0);

      if (pref == forwardLookupProvider) {
        LookupSettings.setForwardLookupProvider(context, pref.getValue());
      } else if (pref == peopleLookupProvider) {
        LookupSettings.setPeopleLookupProvider(context, pref.getValue());
      } else if (pref == reverseLookupProvider) {
        LookupSettings.setReverseLookupProvider(context, pref.getValue());
      }
    } else {
      pref.setValue(provider);
    }
  }
}
