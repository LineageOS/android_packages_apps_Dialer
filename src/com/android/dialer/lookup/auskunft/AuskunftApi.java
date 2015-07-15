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
import android.util.Log;

import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.lookup.ContactBuilder;
import com.android.dialer.lookup.LookupUtils;

import org.apache.http.client.methods.HttpGet;

import java.io.IOException;

public final class AuskunftApi {
    private static final String TAG = AuskunftApi.class.getSimpleName();

    private static final String PEOPLE_LOOKUP_URL =
            "http://www.auskunft.at/Auskunft/Search";

    private static final String SEARCH_RESULTS_REGEX =
            "(?i)id=[\"']?contentarea_searchresult(.*?)<!--[\\s]?contentarea_searchresult";
    private static final String NAME_REGEX =
            "(?i)<td[\\s]+class=[\"']?searchresult_entry_firstline[\"']?>(.*?)</td>";
    private static final String NUMBER_REGEX =
            "(?i)href=[\"']{1}tel:(.*?)[\"']{1}";
    private static final String ADDRESS_REGEX =
            "(?i)<td[\\s]+class=[\"']?searchresult_entry_secondline[\"']?>(.*?)</td>";

    private static final String BUSINESS_IDENTIFIER = "(Firma)";

    private AuskunftApi() {
    }

    public static ContactInfo[] query(String filter) {
        // build URI
        Uri uri = Uri.parse(PEOPLE_LOOKUP_URL)
                .buildUpon()
                .appendQueryParameter("mobileSearch", "yes")
                .appendQueryParameter("searchinput", filter)
                .build();

        // get search results from HTML to speedup subsequent matching and avoid errors
        String output = null;
        try {
            output = LookupUtils.firstRegexResult(
                    LookupUtils.httpGet(new HttpGet(uri.toString())),
                    SEARCH_RESULTS_REGEX, true);
        } catch (IOException exception) {
            Log.e(TAG, exception.toString());
            return null;
        }

        // get all names, abort people lookup if nothing found
        String[] names = LookupUtils.allRegexResults(output, NAME_REGEX, true);
        if (names == null || names.length <= 0) {
            return null;
        }

        // get all numbers and addresses
        String[] numbers = LookupUtils.allRegexResults(output, NUMBER_REGEX, true);
        String[] addresses = LookupUtils.allRegexResults(output, ADDRESS_REGEX, true);        

        // abort on invalid data (all the data arrays must have the same size because we query
        // all the results at once and expect them to be in the right order)
        if (numbers == null || addresses == null || names.length != numbers.length ||
                numbers.length != addresses.length) {
            Log.w(TAG, "names, numbers and address data do not match");
            return null;
        }

        // build and return contact list
        ContactInfo[] details = new ContactInfo[names.length];
        for (int i=0; i<names.length; i++) {
            // figure out if we have a business contact
            boolean isBusiness = names[i].contains(BUSINESS_IDENTIFIER);
            // cleanup results
            names[i] = cleanupResult(names[i]);
            numbers[i] = cleanupResult(numbers[i]);
            addresses[i] = cleanupResult(addresses[i]);
            // build contact and add to list
            ContactBuilder builder = new ContactBuilder(
                    ContactBuilder.PEOPLE_LOOKUP, numbers[i], numbers[i]);
            builder.setName(ContactBuilder.Name.createDisplayName(names[i]));
            builder.addPhoneNumber(
                    ContactBuilder.PhoneNumber.createMainNumber(numbers[i]));
            builder.addWebsite(ContactBuilder.WebsiteUrl.createProfile(uri.toString()));
            builder.addAddress(ContactBuilder.Address.createFormattedHome(addresses[i]));
            builder.setIsBusiness(isBusiness);
            details[i] = builder.build();
        }
        return details;
    }

    private static String cleanupResult(String result) {
        // get displayable text
        result = LookupUtils.fromHtml(result);
        // replace newlines with spaces
        result = result.replaceAll("\\r|\\n", " ");
        // replace multiple spaces with one
        result = result.replaceAll("\\s+", " ");
        // remove business identifier that is originally not part of the name
        result = result.replace(BUSINESS_IDENTIFIER, "");
        // final trimming
        result = result.trim();

        return result;
    }
}
