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

package com.android.dialer.about;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.text.TextUtils;

/** The fragment for information about the Phone App */
public class AboutPhoneFragment extends PreferenceFragment {

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.about_phone_fragment);

    // We set the intent here, instead of in XML, to avoid specifying a target package, which
    // differs between AOSP and the GoogleDialer.
    Intent openSourceActivity =
        new Intent(getActivity().getApplicationContext(), LicenseMenuActivity.class);
    findPreference(getString(R.string.open_source_licenses_key)).setIntent(openSourceActivity);
    populateBuildVersion();
  }

  private void populateBuildVersion() {
    Preference buildVersion = findPreference(getResources().getString(R.string.build_version_key));
    String versionName = getVersionName();
    if (!TextUtils.isEmpty(versionName)) {
      buildVersion.setSummary(versionName);
    }
  }

  private String getVersionName() {
    Context context = getContext();
    try {
      return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
    } catch (NameNotFoundException e) {
      return "";
    }
  }

  @Override
  public Context getContext() {
    return getActivity();
  }
}
