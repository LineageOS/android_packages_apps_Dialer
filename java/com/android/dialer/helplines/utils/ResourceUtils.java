/**
 * Copyright (C) 2016 The CyanogenMod project
 * Copyright (C) 2019 The LineageOS Project
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
package com.android.dialer.helplines.utils;

import android.content.res.Resources;
import android.util.Log;

public class ResourceUtils {

    private static final String TAG = ResourceUtils.class.getSimpleName();
    private static final boolean DEBUG = false;

    public static String getLocalizedString(final Resources res,
                                            final String stringName,
                                            final String stringFormat) {
        String name = stringName.toLowerCase();
        final String[] nonTokens = { "'", ",", "- ", "-", "(", ")" };
        for (String token : nonTokens) {
            name = name.replace(token, "");
        }
        final String[] underscoreTokens = { " ", "/" };
        for (String token : underscoreTokens) {
            name = name.replace(token, "_");
        }
        final String nameRes = String.format(stringFormat, name);
        return getStringForResourceName(res, nameRes, stringName);
    }

    public static String getStringForResourceName(final Resources res,
                                                  final String resourceName,
                                                  final String defaultValue) {
        final int resId = res.getIdentifier(resourceName, "string", "com.android.dialer");
        if (resId <= 0) {
            if (DEBUG) Log.d(TAG, "No resource found for " + resourceName);
            return defaultValue;
        } else {
            return res.getString(resId);
        }
    }
}
