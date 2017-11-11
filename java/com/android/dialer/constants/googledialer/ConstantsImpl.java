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
 * limitations under the License.
 */

package com.android.dialer.constants;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import com.android.dialer.proguard.UsedByReflection;

/** Provider config values for Google Dialer. */
@UsedByReflection(value = "Constants.java")
public class ConstantsImpl extends Constants {

  @Override
  @NonNull
  public String getFilteredNumberProviderAuthority() {
    return "com.google.android.dialer.blocking.filterednumberprovider";
  }

  @Override
  @NonNull
  public String getFileProviderAuthority() {
    return "com.google.android.dialer.files";
  }

  @NonNull
  @Override
  public String getAnnotatedCallLogProviderAuthority() {
    return "com.google.android.dialer.annotatedcalllog";
  }

  @NonNull
  @Override
  public String getPhoneLookupHistoryProviderAuthority() {
    return "com.google.android.dialer.phonelookuphistory";
  }

  @NonNull
  @Override
  public String getPreferredSimFallbackProviderAuthority() {
    return "com.google.android.dialer.preferredsimfallback";
  }

  @Override
  public String getUserAgent(Context context) {
    StringBuilder userAgent = new StringBuilder("GoogleDialer ");
    try {
      String versionName =
          context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
      userAgent.append(versionName).append(" ");
    } catch (PackageManager.NameNotFoundException e) {
      // ignore
    }
    userAgent.append(Build.FINGERPRINT);

    return userAgent.toString();
  }

  @NonNull
  @Override
  public String getSettingsActivity() {
    return "com.google.android.apps.dialer.settings.GoogleDialerSettingsActivity";
  }
}
