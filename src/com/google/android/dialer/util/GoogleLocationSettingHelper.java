/*
  * Copyright (C) 2013 Xiao-Long Chen <chenxiaolong@cxl.epac.to>
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

/*
 * This is a reverse-engineered implementation of com.google.android.Dialer.
 * There is no guarantee that this implementation will work correctly or even
 * work at all. Use at your own risk.
 */

package com.google.android.dialer.util;

import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

public class GoogleLocationSettingHelper {
    private static final Uri GOOGLE_SETTINGS_CONTENT_URI =
            Uri.parse("content://com.google.settings/partner");

    // TODO: Check logic. Decompiler totally screwed this one up
    private static int getUseLocationForServices(Context context) {
        ContentResolver resolver = context.getContentResolver();
        Cursor query = null;
        String string = null;

        try {
            query = resolver.query(GOOGLE_SETTINGS_CONTENT_URI,
                    new String[] { "value" },
                    "name=?",
                    new String[] { "use_location_for_services" },
                    null);

            if (query != null) {
                if (query.moveToNext()) {
                    string = query.getString(0);
                }
            }
            if (string == null) {
                return 2;
            }
        } catch (RuntimeException e) {
            Log.w("GoogleDialer", "Failed to get 'Use My Location' setting", e);
        } finally {
            if (query != null) {
                query.close();
            }
        }

        try {
            return Integer.parseInt(string);
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    public static boolean isEnforceable(Context context) {
        return context.getPackageManager().resolveActivity(
                new Intent("com.google.android.gsf.GOOGLE_APPS_LOCATION_SETTINGS"),
                PackageManager.MATCH_DEFAULT_ONLY) != null;
    }

    public static boolean isGoogleLocationServicesEnabled(Context context) {
        return !isEnforceable(context) || getUseLocationForServices(context) == 1;
    }

    public static boolean isSystemLocationSettingEnabled(Context context) {
        try {
            int locationMode = Settings.Secure.getInt(
                    context.getContentResolver(), "location_mode");
            if (locationMode != 0) {
                return true;
            } else {
                return false;
            }
        } catch (Settings.SettingNotFoundException e) {
            Log.w("GoogleDialer", "Failed to get System Location setting", e);
            return false;
        }
    }
}
