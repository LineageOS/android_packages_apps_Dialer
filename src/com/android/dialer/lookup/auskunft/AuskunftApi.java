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

import java.io.IOException;
import java.util.List;

public final class AuskunftApi {
    private static final String TAG = AuskunftApi.class.getSimpleName();

    private static final String PEOPLE_LOOKUP_URL =
            "https://auskunft.at/suche";

    private static final String SEARCH_RESULTS_REGEX =
            "(?i)class=[\"']?search-list(.*?)class=[\"']?pagination";
    private static final String NAME_REGEX =
            "(?i)<h1[\\s]+itemprop=[\"']?name[\"']?>(.*?)</h1";
    private static final String NUMBER_REGEX =
            "(?i)phone[\"'][\\s]+?href=[\"']{1}tel:(.*?)[\"']{1}";
    private static final String ADDRESS_REGEX =
            "(?i)<span[\\s]+itemprop=[\"']?streetAddress[\"']?>(.*?)</a";

    private static final String BUSINESS_IDENTIFIER = "(Firma)";

    private AuskunftApi() {
    }

    public static ContactInfo[] query(String filter, int lookupType, String normalizedNumber,
            String formattedNumber) throws IOException {
        // build URI
        Uri uri = Uri.parse(PEOPLE_LOOKUP_URL)
                .buildUpon()
                .appendQueryParameter("query", filter)
                .build();

        // get search results from HTML to speedup subsequent matching and avoid errors
        String output = LookupUtils.firstRegexResult(LookupUtils.httpGet(uri.toString(), null),
                SEARCH_RESULTS_REGEX, true);

        // get all names, abort lookup if nothing found
        List<String> names = LookupUtils.allRegexResults(output, NAME_REGEX, true);
        if (names == null || names.isEmpty()) {
            Log.w(TAG, "nothing found");
            return null;
        }

        // get all numbers and addresses
        List<String> numbers = LookupUtils.allRegexResults(output, NUMBER_REGEX, true);
        List<String> addresses = LookupUtils.allRegexResults(output, ADDRESS_REGEX, true);

        // abort on invalid data (all the data arrays must have the same size because we query
        // all the results at once and expect them to be in the right order)
        if (numbers == null || addresses == null || names.size() != numbers.size() ||
                numbers.size() != addresses.size()) {
            Log.w(TAG, "names, numbers and address data do not match");
            return null;
        }

        // build and return contact list
        ContactInfo[] details = new ContactInfo[names.size()];
        for (int i = 0; i < names.size(); i++) {
            // figure out if we have a business contact
            boolean isBusiness = names.get(i).contains(BUSINESS_IDENTIFIER);
            // cleanup results
            String name = cleanupResult(names.get(i));
            String number = cleanupResult(numbers.get(i));
            String address = cleanupResult(addresses.get(i));
            // set normalized and formatted number if we're not doing a reverse lookup
            if (lookupType != ContactBuilder.REVERSE_LOOKUP) {
                normalizedNumber = formattedNumber = number;
            }
            // build contact and add to list
            ContactBuilder builder = new ContactBuilder(lookupType, normalizedNumber,
                    formattedNumber);
            builder.setName(ContactBuilder.Name.createDisplayName(name));
            builder.addPhoneNumber(
                    ContactBuilder.PhoneNumber.createMainNumber(number));
            builder.addWebsite(ContactBuilder.WebsiteUrl.createProfile(uri.toString()));
            builder.addAddress(ContactBuilder.Address.createFormattedHome(address));
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
