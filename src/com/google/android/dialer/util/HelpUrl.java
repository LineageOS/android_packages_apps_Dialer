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
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class HelpUrl {
    public static Uri getHelpUrl(Context context, String fromWhere) {
        if (TextUtils.isEmpty(fromWhere)) {
            throw new IllegalArgumentException("getHelpUrl(): fromWhere must be non-empty");
        }
        Uri.Builder uri = Uri.parse("http://support.google.com/mobile").buildUpon();
        uri.appendQueryParameter("p", fromWhere);
        try {
            uri.appendQueryParameter("version",
                    String.valueOf(context.getPackageManager().getPackageInfo(
                    ((PackageItemInfo)context.getApplicationInfo()).packageName, 0).versionCode));
            return uri.build();
        }
        catch (PackageManager.NameNotFoundException e) {
            Log.e("Dialer", "Error finding package " +
                    ((PackageItemInfo)context.getApplicationInfo()).packageName);
            return uri.build();
        }
    }

    public static Uri getPhoneAccountSettingUri() {
        return Uri.parse("https://www.google.com/settings/phone");
    }
}
