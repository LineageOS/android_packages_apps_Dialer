/**
 * Copyright (c) 2015, The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dialer.lookup.auskunft;

import android.content.Context;
import android.net.Uri;

import com.android.dialer.lookup.LookupUtils;

import org.apache.http.client.methods.HttpGet;

import java.io.IOException;

import android.util.Log;

public class AuskunftApi {
    private static final String TAG = AuskunftApi.class.getSimpleName();

    private static final String REVERSE_LOOKUP_URL =
            "http://www.auskunft.at/Auskunft/Search";

    private static String NAME_REGEX = "<h2.*class=\"searchresult_entry_name\">(.*)</h2>";
    private static String NUMBER_REGEX = "href='callto:(.*)'";
    private static String ADDRESS_REGEX = "Adresse.*class=\"searchresult_entry_text\">(\n.*)</a>";

    private AuskunftApi() {
    }

    public static ContactInfo reverseLookup(Context context, String number)
            throws IOException {
        Uri uri = Uri.parse(REVERSE_LOOKUP_URL)
                .buildUpon()
                .appendQueryParameter("searchinput", number)
                .build();
        String output = LookupUtils.httpGet(new HttpGet(uri.toString()));

        String name = LookupUtils.fromHtml(LookupUtils.firstRegexResult(output, NAME_REGEX, false));
        if (name == null) {
            return null;
        }

        String phoneNumber = LookupUtils.fromHtml(LookupUtils.firstRegexResult(output, NUMBER_REGEX, false));
        String address = LookupUtils.fromHtml(LookupUtils.firstRegexResult(output, ADDRESS_REGEX, false));

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
