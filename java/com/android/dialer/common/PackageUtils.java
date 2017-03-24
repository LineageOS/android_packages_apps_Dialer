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

package com.android.dialer.common;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;

/** Utility class for package management. */
public class PackageUtils {

  private static boolean isPackageInstalled(@NonNull String packageName, @NonNull Context context) {
    Assert.isNotNull(packageName);
    Assert.isNotNull(context);
    try {
      PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
      if (info != null && info.packageName != null) {
        LogUtil.d("PackageUtils.isPackageInstalled", packageName + " is found");
        return true;
      }
    } catch (PackageManager.NameNotFoundException e) {
      LogUtil.d("PackageUtils.isPackageInstalled", packageName + " is NOT found");
    }
    return false;
  }

  /** Returns true if the pkg is installed and enabled/default */
  public static boolean isPackageEnabled(@NonNull String packageName, @NonNull Context context) {
    Assert.isNotNull(packageName);
    Assert.isNotNull(context);
    if (isPackageInstalled(packageName, context)) {
      if (context.getPackageManager().getApplicationEnabledSetting(packageName)
          != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
        return true;
      }
    }
    return false;
  }
}
