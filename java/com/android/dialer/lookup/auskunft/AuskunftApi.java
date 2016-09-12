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

import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.lookup.ContactBuilder;
import com.android.dialer.lookup.LookupUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class AuskunftApi {
    private static final String TAG = AuskunftApi.class.getSimpleName();

    private static final String PEOPLE_LOOKUP_URL =
            "https://auskunft.at/suche";

    private static final String SEARCH_RESULTS_REGEX =
            "(?i)<section[\\s]+class=[\"']?search-entry(.*?)?</section";
    private static final String NAME_REGEX =
            "(?i)<h1[\\s]+itemprop=[\"']?name[\"']?>(.*?)</h1";
    private static final String NUMBER_REGEX =
            "(?i)phone[\"'][\\s]+?href=[\"']{1}tel:(.*?)[\"']{1}";
    private static final String ADDRESS_REGEX =
            "(?i)<span[\\s]+itemprop=[\"']?streetAddress[\"']?>(.*?)</a";

    private static final String BUSINESS_IDENTIFIER = "(Firma)";

    private AuskunftApi() {
    }

    public static List<ContactInfo> query(String filter, int lookupType, String normalizedNumber,
            String formattedNumber) throws IOException {
        // build URI
        Uri uri = Uri.parse(PEOPLE_LOOKUP_URL)
                .buildUpon()
                .appendQueryParameter("query", filter)
                .build();

        // get all search entry sections
        List<String> entries = LookupUtils.allRegexResults(LookupUtils.httpGet(uri.toString(),
                null), SEARCH_RESULTS_REGEX, true);

        // abort lookup if nothing found
        if (entries == null || entries.isEmpty()) {
            Log.w(TAG, "nothing found");
            return null;
        }

        // build response by iterating through the search entries and parsing their HTML data
        List<ContactInfo> infos = new ArrayList<ContactInfo>();
        for (String entry : entries) {
            // parse wanted data and replace null values
            String name = replaceNullResult(LookupUtils.firstRegexResult(entry, NAME_REGEX, true));
            String address = replaceNullResult(LookupUtils.firstRegexResult(
                    entry, ADDRESS_REGEX, true));
            String number = replaceNullResult(LookupUtils.firstRegexResult(
                    entry, NUMBER_REGEX, true));
            // ignore entry if name or number is empty (should not occur)
            // missing addresses won't be a problem (but do occur)
            if (name.isEmpty() || number.isEmpty()) {
                continue;
            }
            // figure out if we have a business contact
            boolean isBusiness = name.contains(BUSINESS_IDENTIFIER);
            // cleanup results
            name = cleanupResult(name);
            number = cleanupResult(number);
            address = cleanupResult(address);
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
            infos.add(builder.build());
        }
        return infos;
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

    private static String replaceNullResult(String result) {
        return (result == null) ? "" : result;
    }
}
