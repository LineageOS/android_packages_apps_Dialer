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
import org.apache.commons.lang3.text.WordUtils;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PagineBiancheApi {
    private static final String TAG = PagineBiancheApi.class.getSimpleName();

    private static final String REVERSE_LOOKUP_URL =
       "http://www.paginebianche.it/ricerca-da-numero?";
   private static final String PEOPLE_LOOKUP_URL =
       "http://www.paginebianche.it/ricerca?";

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

    public static ContactInfo[] peopleLookup(Context context, String name,
            int maxResults) throws IOException {
        String provider = LookupSettings.getPeopleLookupProvider(context);

        if (LookupSettings.PLP_PAGINEBIANCHE.equals(provider)) {
            Uri.Builder builder = Uri.parse(PEOPLE_LOOKUP_URL)
                    .buildUpon();
            builder.appendQueryParameter("qs", name);
            String lookupUrl = builder.build().toString();
            String output = httpGet(lookupUrl);
            return parseOutputUnitedStates(output, maxResults);
        }
        // no-op
        return null;
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


    private static ContactInfo[] parseOutputItaly(String output,
            int maxResults) throws IOException {
        ArrayList<ContactInfo> people = new ArrayList<ContactInfo>();

        Pattern regex = Pattern.compile(
                "<div\\s+class=\"item (?:item-first)? clearfix\\s*\"\\s+id=\"co_\d+\"", Pattern.DOTALL);
        Matcher m = regex.matcher(output);

        while (m.find()) {
            if (people.size() == maxResults) {
                break;
            }

            // Find section of HTML with contact information
            String section = extractXmlTag(output, m.start(), m.end(), "div");

            // Skip entries with no phone number
            if (!section.contains("class=\"tel\"")) {
                continue;
            }

            // Name
            String name = parseName(section)

            if (name == null) {
                continue;
            }

            // Address
            String address = parseAddress(section)
            String city = parseCity(section)

            // Phone number
            String phoneNumber = parseNumber(section)

            if (phoneNumber == null) {
                Log.e(TAG, "Phone number is null. Either cookie is bad or regex is broken");
                continue;
            }

            ContactInfo info = new ContactInfo();
            info.name = name;
            info.city = city;
            info.address = address;
            info.formattedNumber = phoneNumber;
            info.website = website;

            people.add(info);
        }

        return people.toArray(new ContactInfo[people.size()]);
    }

    private static String extractXmlRegex(String str, String regex, String tag) {
        Pattern p = Pattern.compile(regex, Pattern.DOTALL);
        Matcher m = p.matcher(str);
        if (m.find()) {
            return extractXmlTag(str, m.start(), m.end(), tag);
        }
        return null;
    }

    private static String extractXmlTag(String str, int realBegin, int begin,
            String tag) {
        int end = begin;
        int tags = 1;
        int maxLoop = 30;

        while (tags > 0) {
            end = str.indexOf(tag, end + 1);
            if (end < 0 || maxLoop < 0) {
                break;
            }

            if (str.charAt(end - 1) == '/'
                    && str.charAt(end + tag.length()) == '>') {
                tags--;
            } else if (str.charAt(end - 1) == '<') {
                tags++;
            }

            maxLoop--;
        }

        int realEnd = str.indexOf(">", end) + 1;

        if (tags != 0) {
            Log.e(TAG, "Failed to extract tag <" + tag + "> from XML/HTML");
            return null;
        }

        return str.substring(realBegin, realEnd);
    }

    private static String parseName(String output) {
        Pattern regex = Pattern.compile("\\stitle=\"(.*?)\"");
        Matcher m = regex.matcher(output);

        if (m.find()) {
            String name = m.group(1);
            return WordUtils.capitalizeFully(name);
        }

        return null;
    }

    private static String parseWebsite(String output) {
        Pattern regex = Pattern.compile("<a\\sclass=\"lmap\"\\srel=\"nofollow\"\\shref=\"(.*?)\">\\s*mappa</a>");
        Matcher m = regex.matcher(output);

        if (m.find()) {
            return "http://www.paginebianche.it" + m.group(1);
        }

        return null;
    }

    private static String parseNumber(String output) {
        Pattern regex = Pattern.compile("class=\"tel\".*?class=\"value\">(.*?)<");
        Matcher m = regex.matcher(output);
        if (m.find()) {
            String number = m.group(1);

            if (!number.contains("+39")) {
                return "+39 " + number;
            }

            return number;
        }

        return null;
    }

    private static String parseAddress(String output) {
        Pattern regex = Pattern.compile("class=\"street-address\">(.*?)</div>");
        Matcher m = regex.matcher(output);
        if (m.find()) {
            return m.group(1).replaceAll("</?span.*?>", "") + " - Italia";
        }

        return null;
    }

    private static String parseCity(String output) {
        Pattern regex = Pattern.compile("<span\\sclass=\"locality\">(.*?)</span>");
        Matcher m = regex.matcher(output);
        if (m.find()) {
            return m.group(1);
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
