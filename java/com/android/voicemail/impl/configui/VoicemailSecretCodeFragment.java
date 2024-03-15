/*
 * Copyright (C) 2024 The LineageOS Project
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

package com.android.voicemail.impl.configui;

import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import android.content.Intent;
import android.provider.VoicemailContract;

public class VoicemailSecretCodeFragment extends PreferenceFragmentCompat {

  private Preference syncPreference;
  private Preference configOverridePreference;

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    setPreferencesFromResource(R.xml.vvm_secret_main, rootKey);

    syncPreference = findPreference("sync_preference");
    syncPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
      @Override
      public boolean onPreferenceClick(Preference preference) {
        Intent intent = new Intent(VoicemailContract.ACTION_SYNC_VOICEMAIL);
        intent.setPackage(requireContext().getPackageName());
        requireContext().sendBroadcast(intent);
        return true;
      }
    });

    configOverridePreference = findPreference("config_override_preference");
    configOverridePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
      @Override
      public boolean onPreferenceClick(Preference preference) {
        getParentFragmentManager().beginTransaction()
          .replace(android.R.id.content, new ConfigOverrideFragment())
          .addToBackStack(null)
          .commit();
        return true;
      }
    });
  }
}
