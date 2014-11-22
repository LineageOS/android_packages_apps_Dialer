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
import android.text.Html;

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

public class PagineBiancheApi {
    private static final String TAG = PagineBiancheApi.class.getSimpleName();

    private static final String REVERSE_LOOKUP_URL =
       "http://www.paginebianche.it/ricerca-da-numero?";

    private PagineBiancheApi() {
    }

    public static ContactInfo reverseLookup(Context context, String number)
            throws IOException {
        Uri uri = Uri.parse(REVERSE_LOOKUP_URL)
                .buildUpon()
                .appendQueryParameter("qs", number)
                .build();

        String output = httpGet(uri.toString());
        if (output == null) {
            return null;
        }

        // Cut out everything we're not interested in (scripts etc.) to
        // speed up the subsequent matching.
        Pattern regex = Pattern.compile("class=\"org fn\"(.*?)class=\"link_social", Pattern.DOTALL);
        Matcher matcher = regex.matcher(output);
        if (!matcher.find()) {
            return null;
        }

        output = matcher.group(1);

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
        Pattern regex = Pattern.compile("\\stitle=\"(.*?)\"");
        Matcher m = regex.matcher(output);

        if (m.find()) {
            return m.group(1);
        }

        return null;
    }

    private static String parseNumber(String output) {
        Pattern regex = Pattern.compile("class=\"tel\".*?class=\"value\">(.*?)<");
        Matcher m = regex.matcher(output);
        if (m.find()) {
            return m.group(1);
        }

        return null;
    }

    private static String parseAddress(String output) {
        Pattern regex = Pattern.compile("class=\"street-address\">(.*?)</div>");
        Matcher m = regex.matcher(output);
        if (m.find()) {
            return m.group(1).replaceAll("</?span.*?>", "");
        }

        return null;
    }

    public static class ContactInfo {
        String name;
        String address;
        String formattedNumber;
        String website;
    }
}
