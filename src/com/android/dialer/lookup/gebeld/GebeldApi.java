/*
 * Copyright (C) 2015 The CyanogenMod Project
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

package com.android.dialer.lookup.gebeld;

import android.content.Context;
import android.net.Uri;

import com.android.dialer.lookup.LookupUtils;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GebeldApi {
    private static final String TAG = GebeldApi.class.getSimpleName();

    private static final String REVERSE_LOOKUP_URL =
            "http://www.gebeld.nl/zoeken.asp?Page=4,%201" +
            "&searchfield1=fullnumber&action=Zoeken";

    private static String REGEX = "\n?\\s*(.*)<(?:(?:BR|br)\\s*/?)>";

    private GebeldApi() {
    }

    public static ContactInfo reverseLookup(Context context, String number)
            throws IOException {
        String phoneNumber = number.replace("+31", "0");
        Uri uri = Uri.parse(REVERSE_LOOKUP_URL)
                .buildUpon()
                .appendQueryParameter("queryfield1", phoneNumber)
                .build();
        // Cut out everything we're not interested in (scripts etc.) to
        // speed up the subsequent matching.
        String output = LookupUtils.firstRegexResult(
                LookupUtils.httpGet(uri.toString(), null),
                "<div class=\"small-12 large-4 columns information\">(.*?)</div>", true);

        String name = null;
        String address = null;

        if (output == null) {
            return null;
        } else {
            Pattern pattern = Pattern.compile(REGEX, 0);
            Matcher m = pattern.matcher(output);

            if (m.find()) {
                name = LookupUtils.fromHtml(m.group(1).trim());

                if (m.find()) {
                    address = LookupUtils.fromHtml(m.group(1).trim());

                    if (m.find()) {
                        address += "\n" + LookupUtils.fromHtml(m.group(1).trim());
                    }
                }
            }
        }

        if (name == null) {
            return null;
        }

        ContactInfo info = new ContactInfo();
        info.name = name;
        info.address = address;
        info.formattedNumber = phoneNumber != null ? phoneNumber : number;
        info.website = uri.toString();

        return info;
    }

    public static class ContactInfo {
        String name;
        String address;
        String formattedNumber;
        String website;
    }
}
