/*
 * Copyright (C) 2014 Danny Baumann <dannybaumann@web.de>
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

package com.android.dialer.lookup.dastelefonbuch;

import android.content.Context;
import android.net.Uri;

import com.android.dialer.lookup.LookupSettings;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TelefonbuchApi {
    private static final String TAG = TelefonbuchApi.class.getSimpleName();

    private static final String REVERSE_LOOKUP_URL =
            "http://www.dastelefonbuch.de/?s=a20000" +
            "&cmd=search&sort_ok=0&sp=55&vert_ok=0&aktion=23";

    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64; rv:26.0) Gecko/20100101 Firefox/26.0";

    private TelefonbuchApi() {
    }

    public static ContactInfo reverseLookup(Context context, String number)
            throws IOException {
        Uri uri = Uri.parse(REVERSE_LOOKUP_URL)
                .buildUpon()
                .appendQueryParameter("kw", number)
                .build();
        String output = httpGet(uri.toString());

        String name = parseName(output);
        if (name == null) {
            return null;
        }

        String phoneNumber = parseNumber(output);
        String address = parseAddress(output);

        ContactInfo info = new ContactInfo();
        info.name = name;
        info.address = address;
        info.formattedNumber = phoneNumber != null ? phoneNumber : number;
        info.website = uri.toString();

        return info;
    }

    private static String httpGet(String url) throws IOException {
        HttpClient client = new DefaultHttpClient();
        HttpGet get = new HttpGet(url);

        get.setHeader("User-Agent", USER_AGENT);

        HttpResponse response = client.execute(get);
        int status = response.getStatusLine().getStatusCode();

        // Android's org.apache.http doesn't have the RedirectStrategy class
        if (status == HttpStatus.SC_MOVED_PERMANENTLY
                || status == HttpStatus.SC_MOVED_TEMPORARILY) {
            Header[] headers = response.getHeaders("Location");

            if (headers != null && headers.length != 0) {
                String newUrl = headers[headers.length - 1].getValue();
                return httpGet(newUrl);
            } else {
                return null;
            }
        }

        if (status != HttpStatus.SC_OK) {
            return null;
        }

        return EntityUtils.toString(response.getEntity());
    }

    private static String parseName(String output) {
        Pattern regex = Pattern.compile("<a id=\"name0.*?>\\s*\n?(.*?)\n?\\s*</a>",
                Pattern.DOTALL);
        Matcher m = regex.matcher(output);

        if (m.find()) {
            return m.group(1).trim().replaceAll("&amp;", "&");
        }

        return null;
    }

    private static String parseNumber(String output) {
        Pattern regex = Pattern.compile("<span\\s+class=\"ico fon.*?>.*<span>(.*?)</span>",
                Pattern.DOTALL);
        Matcher m = regex.matcher(output);
        if (m.find()) {
            return m.group(1).trim();
        }

        return null;
    }

    private static String parseAddress(String output) {
        String regexBase = "<span\\s+itemprop=\"%s\"\\s?>(.*?)</span>";

        Pattern regexAddress = Pattern.compile(
                String.format(regexBase, "street-address"), Pattern.DOTALL);
        Matcher addressMatcher = regexAddress.matcher(output);
        if (!addressMatcher.find()) {
            return null;
        }

        Pattern regexPostal = Pattern.compile(
                String.format(regexBase, "postal-code"), Pattern.DOTALL);
        Matcher postalMatcher = regexPostal.matcher(output);
        if (!postalMatcher.find()) {
            return null;
        }

        Pattern regexLocation = Pattern.compile(
                String.format(regexBase, "locality"), Pattern.DOTALL);
        Matcher locationMatcher = regexLocation.matcher(output);
        if (!locationMatcher.find()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(addressMatcher.group(1).trim()).append(", ");
        sb.append(postalMatcher.group(1).trim()).append(" ");
        sb.append(locationMatcher.group(1).trim());

        return sb.toString();
    }

    public static class ContactInfo {
        String name;
        String city;
        String address;
        String formattedNumber;
        String website;
    }
}
