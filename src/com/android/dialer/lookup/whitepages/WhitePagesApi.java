/*
 * Copyright (C) 2014 Xiao-Long Chen <chenxiaolong@cxl.epac.to>
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

package com.android.dialer.lookup.whitepages;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.lookup.ContactBuilder;
import com.android.dialer.lookup.LookupSettings;
import com.android.dialer.lookup.LookupUtils;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.util.ArrayList;
import java.util.List;

public class WhitePagesApi {
    private static final String TAG = WhitePagesApi.class.getSimpleName();

    private static final String URL_PREFIX_UNITED_STATES = "http://www.whitepages.com";
    private static final String URL_PREFIX_CANADA = "http://www.whitepages.ca";

    private static final String REVERSE_URL_LOCATION = "/search/ReversePhone";
    private static final String PEOPLE_URL_LOCATION = "/search/FindPerson";

    private static final String META_REFRESH_URL_REGEX =
            "(?i)<meta.?http-equiv=[\"']refresh[\"'].*?url=(.*)[\"'].*?/>";
    private static final String SEARCH_RESULTS_REGEX_BOUNDARY =
            "(?i)itemtype=[\"']?http:\\/\\/schema.org\\/Person[\"']?";
    private static final String NAME_REGEX = "(?i)<span[\\s]+class=[\"']?name[\"']?(.*?)</span";
    private static final String PROFILE_REGEX = "(?i)href=[\"'](.+?)[\"']";
    private static final String NUMBER_REGEX =
            "(?i)<span[\\s]+class=[\"']?name block[\"']?>(.*?)</span";
    private static final String ADDRESS_REGEX =
            "(?i)<div[\\s]+class=[\"']?address-card[\"']?>(.*?)</div";

    private static String urlPrefix;

    private WhitePagesApi() {
    }

    public static List<ContactInfo> peopleLookup(Context context, String filter)
            throws IOException {
        String provider = LookupSettings.getPeopleLookupProvider(context);

        if (LookupSettings.PLP_WHITEPAGES.equals(provider)) {
            urlPrefix = URL_PREFIX_UNITED_STATES;
        } else {
            Log.e(TAG, "provider is invalid");
            return null;
        }

        // enable cookie manager for HttpURLConnection
        CookieManager cookieManager = new CookieManager();
        CookieHandler.setDefault(cookieManager);

        // query site and extract search entries
        Uri uri = Uri.parse(urlPrefix + PEOPLE_URL_LOCATION)
            .buildUpon()
            .appendQueryParameter("who", filter)
            .build();
        String output = httpGet(uri.toString());
        List<String> entries = LookupUtils.allRegexResults(output, "(?i)"
                + SEARCH_RESULTS_REGEX_BOUNDARY + "(.*?)" + SEARCH_RESULTS_REGEX_BOUNDARY, true);

         // abort lookup if nothing found
        if (entries == null || entries.isEmpty()) {
            Log.w(TAG, "output: " + output); // TODO REMOVE
            Log.w(TAG, "nothing found, limit was reached or regex broken");
            return null;
        }

        List<ContactInfo> infos = new ArrayList<ContactInfo>();
        for (String entry : entries) {
            // skip entries with no phone number
            if (entry.contains("has-no-phone-icon")) {
                continue;
            }

            // query profile page for details
            String profileUrl = urlPrefix +
                    LookupUtils.firstRegexResult(entry, PROFILE_REGEX, false);
            ContactInfo info = createContactInfoFromProfilePage(httpGet(profileUrl),
                    profileUrl, null, filter);
            if (info != null) {
                infos.add(info);
            }
        }
        Log.w(TAG, "contacts: " + infos.toString()); // TODO REMOVE
        return infos;
    }

    public static ContactInfo reverseLookup(Context context, String normalizedNumber,
            String formattedNumber) throws IOException {
        String provider = LookupSettings.getReverseLookupProvider(context);

        if (LookupSettings.RLP_WHITEPAGES.equals(provider)) {
            urlPrefix = URL_PREFIX_UNITED_STATES;
        } else if (LookupSettings.RLP_WHITEPAGES_CA.equals(provider)) {
            urlPrefix = URL_PREFIX_CANADA;
        } else {
            Log.e(TAG, "provider is invalid");
            return null;
        }

        // enable cookie manager for HttpURLConnection
        CookieManager cookieManager = new CookieManager();
        CookieHandler.setDefault(cookieManager);

        Log.w(TAG, "normalizedNumber: " + normalizedNumber);
        Log.w(TAG, "formattedNumber: " + formattedNumber);

        // search with normalizedNumber if set, otherwise fall back to formattedNumber
        String phoneNumber = (normalizedNumber != null) ? normalizedNumber : formattedNumber;
        Uri uri = Uri.parse(urlPrefix + REVERSE_URL_LOCATION)
            .buildUpon()
            .appendQueryParameter("full_phone", phoneNumber)
            .build();
        return createContactInfoFromProfilePage(httpGet(uri.toString()),
                uri.toString(), normalizedNumber, formattedNumber);
    }

    private static String httpGet(String url) throws IOException {
        // try to make a regular request first
        String response = LookupUtils.httpGet(url, null);
        // get URL out of meta refresh tag if existing
        String metaRefreshUrl = LookupUtils.firstRegexResult(response,
                META_REFRESH_URL_REGEX, false);
        // follow meta refresh URL by calling this function again
        if (metaRefreshUrl != null) {
            if (metaRefreshUrl.startsWith('/')) {
                metaRefreshUrl = urlPrefix + metaRefreshUrl;
            }
            Log.w(TAG, "following a meta refresh to " + metaRefreshUrl);
            response = httpGet(metaRefreshUrl);
        }
        return response;
    }

    private static ContactInfo createContactInfoFromProfilePage(String profile, String profileUrl,
            String normalizedNumber, String formattedNumber) {
        if (profile == null || profile.isEmpty()) {
            Log.w(TAG, "could not query profile page");
            return null;
        }
        // get name, number and address from profile page
        String name = LookupUtils.fromHtml(
                LookupUtils.firstRegexResult(profile, NAME_REGEX, false));
        String phoneNumber = LookupUtils.fromHtml(
                LookupUtils.firstRegexResult(profile, NUMBER_REGEX, false));
        String address = LookupUtils.fromHtml(
                LookupUtils.firstRegexResult(profile, ADDRESS_REGEX, true));
        if (name == null || name.isEmpty() || phoneNumber == null || phoneNumber.isEmpty()) {
            Log.w(TAG, "name or phoneNumber missing");
            return null;
        }

        Log.w(TAG, "name: " + name);
        Log.w(TAG, "phoneNumber: " + phoneNumber);
        Log.w(TAG, "address: " + address);

        // complete missing number details (e.g. on people lookups)
        if (normalizedNumber == null) {
            normalizedNumber = phoneNumber;
        }
        if (formattedNumber == null) {
            formattedNumber = phoneNumber;
        }

        // build and return contact
        ContactBuilder builder = new ContactBuilder(ContactBuilder.REVERSE_LOOKUP,
                normalizedNumber, formattedNumber);
        builder.setName(ContactBuilder.Name.createDisplayName(name));
        builder.addPhoneNumber(ContactBuilder.PhoneNumber.createMainNumber(phoneNumber));
        builder.addWebsite(ContactBuilder.WebsiteUrl.createProfile(profileUrl));
        if (address != null && !address.isEmpty()) {
            builder.addAddress(ContactBuilder.Address.createFormattedHome(address));
        }
        return builder.build();
    }
}
