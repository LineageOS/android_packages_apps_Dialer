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
import android.text.TextUtils;
import android.util.Log;

import com.android.server.telecom.SensitivePhoneNumberInfo;

import java.util.ArrayList;

public class HelplineUtils {

    private static final String TAG = HelplineUtils.class.getSimpleName();
    public static final String CATEGORY_STR_FORMAT = "category_%s";
    public static final String LANGUAGE_STR_FORMAT = "language_%s";

    public static String getTitle(Resources res, SensitivePhoneNumberInfo info) {
        Log.e("MICHAEL", "getTitle! info: " + info);
        if (info != null) {
            String str = getDisplayString(res, info.get("categories"), CATEGORY_STR_FORMAT);
            if (!TextUtils.isEmpty(str)) {
                return str;
            }

            String name = info.get("name");
            String number = info.get("number");
            if (!TextUtils.isEmpty(name)) {
                return name;
            } else if (!TextUtils.isEmpty(number)) {
                return number;
            }
        }

        return "";
    }

    public static String getLanguages(Resources res, SensitivePhoneNumberInfo info) {
        if (info != null) {
            return getDisplayString(res, info.get("languages"), LANGUAGE_STR_FORMAT);
        }

        return "";
    }

    private static String getDisplayString(Resources res, String content, String pattern) {
        Log.e("MICHAEL", "content: " + content);
        if (content != null && content.length() > 0) {
            String[] listItems = TextUtils.split(content, "\\|");
            ArrayList<String> list = new ArrayList<>();
            for (String item : listItems) {
                Log.e("MICHAEL", "item: " + item);
                String l = ResourceUtils.getLocalizedString(res, item, pattern);
                Log.e("MICHAEL", "translated: " + l);
                list.add(l);
            }
            if (list.size() > 0) {
                return TextUtils.join(", ", list);
            }
            return content;
        }
        return "";
    }
}
