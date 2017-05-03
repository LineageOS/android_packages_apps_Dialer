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

package com.android.voicemail;

import android.Manifest.permission;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles permission checking for the voicemail module. Currently "phone" and "sms" permissions are
 * required.
 */
public class VoicemailPermissionHelper {

  /** *_VOICEMAIL permissions are auto-granted by being the default dialer. */
  private static final String[] VOICEMAIL_PERMISSIONS = {
    permission.ADD_VOICEMAIL,
    permission.WRITE_VOICEMAIL,
    permission.READ_VOICEMAIL,
    permission.READ_PHONE_STATE,
    permission.SEND_SMS
  };

  /**
   * Returns {@code true} if the app has all permissions required for the voicemail module to
   * operate.
   */
  public static boolean hasPermissions(Context context) {
    return getMissingPermissions(context).isEmpty();
  }

  /** Returns a list of permission that is missing for the voicemail module to operate. */
  @NonNull
  public static List<String> getMissingPermissions(Context context) {
    List<String> result = new ArrayList<>();
    for (String permission : VOICEMAIL_PERMISSIONS) {
      if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
        result.add(permission);
      }
    }
    return result;
  }
}
