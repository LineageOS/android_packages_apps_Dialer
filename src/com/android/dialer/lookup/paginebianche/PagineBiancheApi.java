/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package com.android.dialer.lookup.paginebianche;

import android.content.Context;
import android.net.Uri;

import com.android.dialer.lookup.LookupUtils;

import org.apache.http.client.methods.HttpGet;

import java.io.IOException;

public class PagineBiancheApi {
    private static final String TAG = PagineBiancheApi.class.getSimpleName();

    private static final String REVERSE_LOOKUP_URL =
       "http://www.paginebianche.it/ricerca-da-numero?";

    private static final String NAME_REGEX = "\\stitle=\"(.*?)\"";
    private static final String NUMBER_REGEX = "class=\"tel\".*?class=\"value\">(.*?)<";
    private static final String ADDRESS_REGEX = "class=\"street-address\">(.*?)</div>";

    private PagineBiancheApi() {
    }

    public static ContactInfo reverseLookup(Context context, String number)
            throws IOException {
        Uri uri = Uri.parse(REVERSE_LOOKUP_URL)
                .buildUpon()
                .appendQueryParameter("qs", number)
                .build();

        // Cut out everything we're not interested in (scripts etc.) to
        // speed up the subsequent matching.
        String output = LookupUtils.firstRegexResult(
                LookupUtils.httpGet(new HttpGet(uri.toString())),
                "class=\"org fn\"(.*?)class=\"link_social", true);

        String name = LookupUtils.firstRegexResult(output, NAME_REGEX, false);
        if (name == null) {
            return null;
        }

        String phoneNumber = LookupUtils.firstRegexResult(output, NUMBER_REGEX, false);
        String address = parseAddress(output);

        ContactInfo info = new ContactInfo();
        info.name = name;
        info.address = address;
        info.formattedNumber = phoneNumber != null ? phoneNumber : number;
        info.website = uri.toString();

        return info;
    }

    private static String parseAddress(String output) {
        String address = LookupUtils.firstRegexResult(output, ADDRESS_REGEX, false);
        return address != null ? address.replaceAll("</?span.*?>", "") : null;
    }

    public static class ContactInfo {
        String name;
        String address;
        String formattedNumber;
        String website;
    }
}
