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
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class WhitePagesApi {
    private static final String TAG = WhitePagesApi.class.getSimpleName();

    private static final String BASE_URL = "https://www.whitepages.com";
    private static final String REVERSE_URL_LOCATION = "/search/ReversePhone";
    private static final String PEOPLE_URL_LOCATION = "/search/FindPerson";

    private static final String SEARCH_RESULTS_JSON_REGEX =
            "(?i)data-react-props=[\"']{1}(.*?)[\"']{1}";
    private static final String PROFILE_REGEX =
            "(?i)href=[\"']{1}(\\/name\\/.*?)[\"']{1}";
    private static final String NAME_REGEX =
            "(?i)<span[\\s]+class=[\"']?name.*?[\"']?>(.*?)</span";
    private static final String NUMBER_REGEX =
            "(?i)<a.*?href=[\"']{1}tel:(.*?)[\"']{1}";
    private static final String ADDRESS_REGEX =
            "(?i)<address[\\s]+class=[\"']?address adr[\"']?>(.*?)</address>";

    private static final int MAX_QUERY_PROFILES = 3;

    private WhitePagesApi() {
    }

    public static List<ContactInfo> peopleLookup(Context context, String filter)
            throws IOException {
        // query site and extract JSON search results
        Uri uri = Uri.parse(BASE_URL + PEOPLE_URL_LOCATION)
            .buildUpon()
            .appendQueryParameter("who", filter)
            .build();
        String output = LookupUtils.httpGet(uri.toString(), null);
        String json = LookupUtils.fromHtml(
                LookupUtils.firstRegexResult(output, SEARCH_RESULTS_JSON_REGEX, false));
        if (json == null || json.isEmpty()) {
            Log.w(TAG, "could not extract JSON results");
            return null;
        }

        // parse JSON search results and query relevant entries for details
        List<ContactInfo> infos = new ArrayList<ContactInfo>();
        try {
            JSONObject jsonObj = new JSONObject(json);
            JSONArray jsonResults = jsonObj.getJSONArray("results");
            // we want to query only MAX_QUERY_PROFILES here, because this process is slow
            // and our thread has only a limited amount of time to handle the lookup
            for (int i = 0; i < jsonResults.length() && i < MAX_QUERY_PROFILES; i++) {
                JSONObject jsonEntry = jsonResults.getJSONObject(i);
                // ignore entries without a phone number
                if (!jsonEntry.getBoolean("hasPhone")) {
                    continue;
                }
                // query profile page for details and add contact to list
                ContactInfo info = createContactInfoFromProfilePage(
                        BASE_URL + jsonEntry.getString("detailsPath"), null, null);
                if (info != null) {
                    infos.add(info);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSON error", e);
        }
        return infos;
    }

    public static ContactInfo reverseLookup(Context context, String normalizedNumber,
            String formattedNumber) throws IOException {
        // search with normalizedNumber if set, otherwise fall back to formattedNumber
        String phoneNumber = (normalizedNumber != null) ? normalizedNumber : formattedNumber;
        Uri uri = Uri.parse(BASE_URL + REVERSE_URL_LOCATION)
            .buildUpon()
            .appendQueryParameter("full_phone", phoneNumber)
            .build();
        String output = LookupUtils.httpGet(uri.toString(), null);
        // select first associated profile and create contact from it
        String profileUrl = LookupUtils.firstRegexResult(output, PROFILE_REGEX, false);
        if (profileUrl == null || profileUrl.isEmpty()) {
            Log.w(TAG, "no profile page found");
            return null;
        }
        return createContactInfoFromProfilePage(BASE_URL + profileUrl,
                normalizedNumber, formattedNumber);
    }

    private static ContactInfo createContactInfoFromProfilePage(String profileUrl,
            String normalizedNumber, String formattedNumber) throws IOException {
        Log.i(TAG, "query profile page " + profileUrl);
        String profile = LookupUtils.httpGet(profileUrl, null);
        // get name, number and address from profile page
        String name = LookupUtils.fromHtml(
                LookupUtils.firstRegexResult(profile, NAME_REGEX, false));
        String phoneNumber = LookupUtils.fromHtml(
                LookupUtils.firstRegexResult(profile, NUMBER_REGEX, false));
        String address = LookupUtils.fromHtml(
                LookupUtils.firstRegexResult(profile, ADDRESS_REGEX, true));
        if (name == null || name.isEmpty() || phoneNumber == null || phoneNumber.isEmpty()) {
            Log.w(TAG, "name or phoneNumber not found");
            return null;
        }

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
