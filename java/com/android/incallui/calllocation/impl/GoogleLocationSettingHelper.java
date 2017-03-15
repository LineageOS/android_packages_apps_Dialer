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

package com.android.incallui.calllocation.impl;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Settings.Secure;
import android.provider.Settings.SettingNotFoundException;
import com.android.dialer.common.LogUtil;

/**
 * Helper class to check if Google Location Services is enabled. This class is based on
 * https://docs.google.com/a/google.com/document/d/1sGm8pHgGY1QmxbLCwTZuWQASEDN7CFW9EPSZXAuGQfo
 */
public class GoogleLocationSettingHelper {

  /** User has disagreed to use location for Google services. */
  public static final int USE_LOCATION_FOR_SERVICES_OFF = 0;
  /** User has agreed to use location for Google services. */
  public static final int USE_LOCATION_FOR_SERVICES_ON = 1;
  /** The user has neither agreed nor disagreed to use location for Google services yet. */
  public static final int USE_LOCATION_FOR_SERVICES_NOT_SET = 2;

  private static final String GOOGLE_SETTINGS_AUTHORITY = "com.google.settings";
  private static final Uri GOOGLE_SETTINGS_CONTENT_URI =
      Uri.parse("content://" + GOOGLE_SETTINGS_AUTHORITY + "/partner");
  private static final String NAME = "name";
  private static final String VALUE = "value";
  private static final String USE_LOCATION_FOR_SERVICES = "use_location_for_services";

  /** Determine if Google apps need to conform to the USE_LOCATION_FOR_SERVICES setting. */
  public static boolean isEnforceable(Context context) {
    final ResolveInfo ri =
        context
            .getPackageManager()
            .resolveActivity(
                new Intent("com.google.android.gsf.GOOGLE_APPS_LOCATION_SETTINGS"),
                PackageManager.MATCH_DEFAULT_ONLY);
    return ri != null;
  }

  /**
   * Get the current value for the 'Use value for location' setting.
   *
   * @return One of {@link #USE_LOCATION_FOR_SERVICES_NOT_SET}, {@link
   *     #USE_LOCATION_FOR_SERVICES_OFF} or {@link #USE_LOCATION_FOR_SERVICES_ON}.
   */
  private static int getUseLocationForServices(Context context) {
    final ContentResolver resolver = context.getContentResolver();
    Cursor c = null;
    String stringValue = null;
    try {
      c =
          resolver.query(
              GOOGLE_SETTINGS_CONTENT_URI,
              new String[] {VALUE},
              NAME + "=?",
              new String[] {USE_LOCATION_FOR_SERVICES},
              null);
      if (c != null && c.moveToNext()) {
        stringValue = c.getString(0);
      }
    } catch (final RuntimeException e) {
      LogUtil.e(
          "GoogleLocationSettingHelper.getUseLocationForServices",
          "Failed to get 'Use My Location' setting",
          e);
    } finally {
      if (c != null) {
        c.close();
      }
    }
    if (stringValue == null) {
      return USE_LOCATION_FOR_SERVICES_NOT_SET;
    }
    int value;
    try {
      value = Integer.parseInt(stringValue);
    } catch (final NumberFormatException nfe) {
      value = USE_LOCATION_FOR_SERVICES_NOT_SET;
    }
    return value;
  }

  /** Whether or not the system location setting is enable */
  public static boolean isSystemLocationSettingEnabled(Context context) {
    try {
      return Secure.getInt(context.getContentResolver(), Secure.LOCATION_MODE)
          != Secure.LOCATION_MODE_OFF;
    } catch (SettingNotFoundException e) {
      LogUtil.e(
          "GoogleLocationSettingHelper.isSystemLocationSettingEnabled",
          "Failed to get System Location setting",
          e);
      return false;
    }
  }

  /** Convenience method that returns true is GLS is ON or if it's not enforceable. */
  public static boolean isGoogleLocationServicesEnabled(Context context) {
    return !isEnforceable(context)
        || getUseLocationForServices(context) == USE_LOCATION_FOR_SERVICES_ON;
  }
}
