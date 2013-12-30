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

package com.google.android.dialer.reverselookup;

import com.android.incallui.Log;
import com.google.android.gsf.Gservices;

import android.content.Context;

public class ReverseLookupSettingUtil {
    private static final String TAG = ReverseLookupSettingUtil.class.getSimpleName();

    public static String getAdditionalQueryParams(Context context) {
        return Gservices.getString(context.getContentResolver(),
                "dialer_reverse_lookup_additional_query_params",
                "includePeople=1&includeGal=1");
    }

    public static String getProtectedPhotoUrl(Context context) {
        return Gservices.getString(context.getContentResolver(),
                "dialer_reverse_lookup_discovery_protected_photo_url",
                "https://plus.google.com/_/focus/photos/private");
    }

    public static boolean isEnabled(Context context) {
        return isUserEnabled(context) && isGServiceEnabled(context);
    }

    public static boolean isGServiceEnabled(Context context) {
        int version = Gservices.getInt(context.getContentResolver(),
                "dialer_reverse_lookup_min_version_enabled", Integer.MAX_VALUE);
        boolean ret = 2 >= version;
        if (!ret) {
            Log.d(TAG, "Reverse number lookup is disabled."
                    + " Current version: 2 Enabled version: " + version);
        }
        return ret;
    }

    public static boolean isUserEnabled(Context context) {
        return context.getSharedPreferences(
                context.getPackageName() + "_preferences",
                Context.MODE_MULTI_PROCESS).getBoolean("google_caller_id", true);
    }
}
