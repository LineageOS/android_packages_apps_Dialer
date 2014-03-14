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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

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
        mOutput = httpGet(LOOKUP_URL + mNumber);
    }

    private String httpGet(String url) throws IOException {
        HttpClient client = new DefaultHttpClient();
        HttpGet get = new HttpGet(url);

        HttpResponse response = client.execute(get);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getEntity().writeTo(out);

        return new String(out.toByteArray());
    }

    private void buildContactInfo() {
        Matcher m;

        // Name
        Pattern regexName = Pattern.compile(
                "itemprop=\"?name\"?>([^<]+)<", Pattern.DOTALL);
        String name = null;

        m = regexName.matcher(mOutput);
        if (m.find()) {
            name = m.group(1).trim();
        }

        // Formatted phone number
        Pattern regexPhoneNumber = Pattern.compile(
                "itemprop=\"?telephone\"?>([^<]+)<", Pattern.DOTALL);
        String phoneNumber = null;

        m = regexPhoneNumber.matcher(mOutput);
        if (m.find()) {
            phoneNumber = m.group(1).trim();
        }

        // Address
        Pattern regexStreet = Pattern.compile(
                "itemprop=\"?streetAddress\"?>([^<]+?)(&nbsp;)*<", Pattern.DOTALL);
        Pattern regexCity = Pattern.compile(
                "itemprop=\"?addressLocality\"?>([^<]+)<", Pattern.DOTALL);
        Pattern regexState = Pattern.compile(
                "itemprop=\"?addressRegion\"?>([^<]+)<", Pattern.DOTALL);
        Pattern regexZip = Pattern.compile(
                "itemprop=\"?postalCode\"?>([^<]+)<", Pattern.DOTALL);

        String addressStreet = null;
        String addressCity = null;
        String addressState = null;
        String addressZip = null;

        m = regexStreet.matcher(mOutput);
        if (m.find()) {
            addressStreet = m.group(1).trim();
        }

        m = regexCity.matcher(mOutput);
        if (m.find()) {
            addressCity = m.group(1).trim();
        }

        m = regexState.matcher(mOutput);
        if (m.find()) {
            addressState = m.group(1).trim();
        }

        m = regexZip.matcher(mOutput);
        if (m.find()) {
            addressZip = m.group(1).trim();
        }

        StringBuilder sb = new StringBuilder();

        if (addressStreet != null && addressStreet.length() != 0) {
            sb.append(addressStreet);
        }
        if (addressCity != null && addressCity.length() != 0) {
            sb.append(", ");
            sb.append(addressCity);
        }
        if (addressState != null && addressState.length() != 0) {
            sb.append(", ");
            sb.append(addressState);
        }
        if (addressZip != null && addressZip.length() != 0) {
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
