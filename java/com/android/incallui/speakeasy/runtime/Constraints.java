/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.incallui.speakeasy.runtime;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.v4.os.BuildCompat;
import android.support.v4.os.UserManagerCompat;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.util.PermissionsUtil;

/** Preconditions for the use of SpeakEasyModule */
public final class Constraints {

  @VisibleForTesting public static final String SPEAK_EASY_ENABLED = "speak_easy_enabled";
  private static final String[] REQUIRED_PERMISSIONS = {

  };

  // Non-instantiatable.
  private Constraints() {}

  public static boolean isAvailable(@NonNull Context context) {
    Assert.isNotNull(context);

    return isServerConfigEnabled(context)
        && isUserUnlocked(context)
        && meetsPlatformSdkFloor()
        && hasNecessaryPermissions(context);
  }

  private static boolean isServerConfigEnabled(@NonNull Context context) {
    return ConfigProviderBindings.get(context).getBoolean(SPEAK_EASY_ENABLED, false);
  }

  private static boolean isUserUnlocked(@NonNull Context context) {
    return UserManagerCompat.isUserUnlocked(context);
  }

  private static boolean meetsPlatformSdkFloor() {
    return BuildCompat.isAtLeastP();
  }

  @SuppressWarnings("AndroidApiChecker") // Use of Java 8 APIs.
  @TargetApi(VERSION_CODES.N)
  private static boolean hasNecessaryPermissions(@NonNull Context context) {
    for (String permission : REQUIRED_PERMISSIONS) {
      if (!PermissionsUtil.hasPermission(context, permission)) {
        LogUtil.i("Constraints.hasNecessaryPermissions", "missing permission: %s ", permission);
        return false;
      }
    }
    return true;
  }
}
