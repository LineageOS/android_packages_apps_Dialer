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
 * limitations under the License
 */

package com.android.dialer.settings;

import android.content.Context;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.text.TextUtils;

import com.android.dialer.R;

import cyanogenmod.providers.CMSettings;

import java.util.Locale;

public class DisplayOptionsSettingsFragment extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String BUTTON_T9_SEARCH_INPUT_LOCALE = "button_t9_search_input";

    private ListPreference mT9SearchInputLocale;
    private Context mContext;

    // t9 search input locales that we have a custom overlay for
    private static final Locale[] T9_SEARCH_INPUT_LOCALES = new Locale[] {
            new Locale("ko"), new Locale("el"), new Locale("ru"),
            new Locale("he"), new Locale("zh")
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getActivity().getApplicationContext();

        addPreferencesFromResource(R.xml.display_options_settings);

        mT9SearchInputLocale = (ListPreference) findPreference(BUTTON_T9_SEARCH_INPUT_LOCALE);
        if (mT9SearchInputLocale != null) {
            initT9SearchInputPreferenceList();
            mT9SearchInputLocale.setOnPreferenceChangeListener(this);
        }
    }

    /**
     * Supports onPreferenceChangeListener to look for preference changes.
     *
     * @param preference The preference to be changed
     * @param objValue The value of the selection, NOT its localized display value.
     */
    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == mT9SearchInputLocale) {
            saveT9SearchInputLocale(preference, (String) objValue);
        }
        return true;
    }

    private void saveT9SearchInputLocale(Preference preference, String newT9Locale) {
        String lastT9Locale = CMSettings.System.getString(mContext.getContentResolver(),
                CMSettings.System.T9_SEARCH_INPUT_LOCALE);

        if (!TextUtils.equals(lastT9Locale, newT9Locale)) {
            CMSettings.System.putString(mContext.getContentResolver(),
                    CMSettings.System.T9_SEARCH_INPUT_LOCALE, newT9Locale);
        }
    }

    private void initT9SearchInputPreferenceList() {
        int len = T9_SEARCH_INPUT_LOCALES.length + 1;
        String[] entries = new String[len];
        String[] values = new String[len];

        entries[0] = getString(R.string.t9_search_input_locale_default);
        values[0] = Locale.getDefault().getLanguage();

        // add locales programatically so we can use locale.getDisplayName
        for (int i = 0; i < T9_SEARCH_INPUT_LOCALES.length; i++) {
            Locale locale = T9_SEARCH_INPUT_LOCALES[i];
            entries[i + 1] = locale.getDisplayName();
            values[i + 1] = locale.toString();
        }

        // Set current entry from global system setting
        String settingsT9Locale = CMSettings.System.getString(mContext.getContentResolver(),
                CMSettings.System.T9_SEARCH_INPUT_LOCALE);
        if (settingsT9Locale != null) {
            mT9SearchInputLocale.setValue(settingsT9Locale);
        }

        mT9SearchInputLocale.setEntries(entries);
        mT9SearchInputLocale.setEntryValues(values);
        if (mT9SearchInputLocale.getValue().equals("")) {
            mT9SearchInputLocale.setValueIndex(0);
        }
    }
}
