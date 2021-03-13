/**
 * Copyright (C) 2019-2021 The LineageOS Project
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

import com.android.dialer.R;
import com.android.dialer.helplines.HelplineItem;
import org.lineageos.lib.phone.spn.Item;

import java.util.ArrayList;

public class HelplineUtils {

    private static final String TAG = HelplineUtils.class.getSimpleName();
    private static final String NAME_STR_FORMAT = "helpline_name_%s";
    private static final String CATEGORY_STR_FORMAT = "helpline_category_%s";
    private static final String LANGUAGE_STR_FORMAT = "helpline_language_%s";

    /* Get the name of the helpline, fall back to the number if not given */
    public static String getName(Resources res, Item item, String countryIso) {
        if (item != null) {
            String name = item.getName();
            String displayName = !TextUtils.isEmpty(name)
                    ? getDisplayString(res, name, NAME_STR_FORMAT)
                    : item.getNumber();
            if (!TextUtils.isEmpty(countryIso)) {
                return res.getString(R.string.helpline_name_format_country, displayName,
                        countryIso);
            } else {
                return displayName;
            }
        }

        return "";
    }

    /* Split the given categories and translate them, fall back to "generic" if not given */
    public static String getCategories(Resources res, HelplineItem item) {
        if (item != null) {
            String str = getDisplayString(res, item.getItem().getCategories(), CATEGORY_STR_FORMAT);
            if (!TextUtils.isEmpty(str)) {
                return str;
            }
        }

        return res.getString(R.string.helpline_category_generic);
    }

    /* Split and translate the given languages, return empty string if not given */
    public static String getLanguages(Resources res, HelplineItem item) {
        if (item != null) {
            return getDisplayString(res, item.getItem().getLanguages(), LANGUAGE_STR_FORMAT);
        }

        return "";
    }

    /* Split the content at the pipe symbol and get a resource named according to each item
       matching a pattern
     */
    private static String getDisplayString(Resources res, String content, String pattern) {
        if (content != null && content.length() > 0) {
            String[] listItems = TextUtils.split(content, "\\|");
            ArrayList<String> list = new ArrayList<>();
            for (String item : listItems) {
                String l = ResourceUtils.getLocalizedString(res, item, pattern);
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
