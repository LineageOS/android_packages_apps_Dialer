/*
 * Copyright (C) 2014 Xiao-Long Chen <chillermillerlong@hotmail.com>
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

package com.android.dialer.lookup.zabasearch;

import android.text.TextUtils;

import com.android.dialer.lookup.LookupUtils;

import org.apache.http.client.methods.HttpGet;

import java.io.IOException;

public class ZabaSearchApi {
    private static final String TAG = ZabaSearchApi.class.getSimpleName();

    private static final String LOOKUP_URL = "http://www.zabasearch.com/phone/";

    private String mNumber = null;
    public String mOutput = null;
    private ContactInfo mInfo = null;

    public ZabaSearchApi(String number) {
        mNumber = number;
    }

    private void fetchPage() throws IOException {
        mOutput = LookupUtils.httpGet(new HttpGet(LOOKUP_URL + mNumber));
    }

    private void buildContactInfo() {
        // Name
        String name = LookupUtils.firstRegexResult(mOutput,
                "itemprop=\"?name\"?>([^<]+)<", true);
        // Formatted phone number
        String phoneNumber = LookupUtils.firstRegexResult(mOutput,
                "itemprop=\"?telephone\"?>([^<]+)<", true);
        // Address
        String addressStreet = LookupUtils.firstRegexResult(mOutput,
                "itemprop=\"?streetAddress\"?>([^<]+?)(&nbsp;)*<", true);
        String addressCity = LookupUtils.firstRegexResult(mOutput,
                "itemprop=\"?addressLocality\"?>([^<]+)<", true);
        String addressState = LookupUtils.firstRegexResult(mOutput,
                "itemprop=\"?addressRegion\"?>([^<]+)<", true);
        String addressZip = LookupUtils.firstRegexResult(mOutput,
                "itemprop=\"?postalCode\"?>([^<]+)<", true);

        StringBuilder sb = new StringBuilder();

        if (!TextUtils.isEmpty(addressStreet)) {
            sb.append(addressStreet);
        }
        if (!TextUtils.isEmpty(addressCity)) {
            sb.append(", ");
            sb.append(addressCity);
        }
        if (!TextUtils.isEmpty(addressState)) {
            sb.append(", ");
            sb.append(addressState);
        }
        if (!TextUtils.isEmpty(addressZip)) {
            sb.append(", ");
            sb.append(addressZip);
        }

        String address = sb.toString();
        if (address.length() == 0) {
            address = null;
        }

        ContactInfo info = new ContactInfo();
        info.name = name;
        info.address = address;
        info.formattedNumber = mNumber;
        info.website = LOOKUP_URL + info.formattedNumber;
        mInfo = info;
    }

    public ContactInfo getContactInfo() throws IOException {
        if (mInfo == null) {
            fetchPage();

            buildContactInfo();
        }

        return mInfo;
    }

    public static class ContactInfo {
        String name;
        String address;
        String formattedNumber;
        String website;
    }
}
