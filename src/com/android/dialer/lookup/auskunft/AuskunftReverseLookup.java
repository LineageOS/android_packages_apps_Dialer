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

import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.lookup.ContactBuilder;
import com.android.dialer.lookup.LookupUtils;
import com.android.dialer.lookup.ReverseLookup;

import org.apache.http.client.methods.HttpGet;

import java.io.IOException;

public class AuskunftReverseLookup extends ReverseLookup {
    private static final String TAG = AuskunftReverseLookup.class.getSimpleName();

    private static final String REVERSE_LOOKUP_URL =
            "http://www.auskunft.at/Auskunft/Search";

    private static final String SEARCH_RESULTS_REGEX =
            "(?i)id=[\"']?contentarea_searchresult(.*?)<!--[\\s]?contentarea_searchresult";
    private static final String NAME_REGEX =
            "(?i)<td[\\s]+class=[\"']?searchresult_entry_firstline[\"']?>(.*?)</td>";
    private static final String NUMBER_REGEX =
            "(?i)href=[\"']{1}callto:(.*?)[\"']{1}";
    private static final String ADDRESS_REGEX =
            "(?i)adresse.*?class=[\"']?searchresult_entry_text[\"']?>(.*?)</a>";

    private static final String BUSINESS_IDENTIFIER = "(Firma)";

    // a typical international formatted Austrian number has a minimal length of 10 characters
    // e.g. +432236401, this is used for the heuristic reverse lookup of call-through numbers
    private static final int MIN_NUMBER_LENGTH = 10;

    public AuskunftReverseLookup(Context context) {
    }

    /**
     * Perform phone number lookup.
     *
     * @param context The application context
     * @param normalizedNumber The normalized phone number
     * @param formattedNumber The formatted phone number
     * @return The phone number info object
     */
    public ContactInfo lookupNumber(Context context,
            String normalizedNumber, String formattedNumber) throws IOException {
        // only Austrian numbers are supported
        if (normalizedNumber.startsWith("+") && !normalizedNumber.startsWith("+43")) {
            return null;
        }

        // query the lookup provider heuristically in a loop, removing the last character of the
        // number gradually to find the base number of unlisted call-through numbers, ideally we
        // only need 1 or 2 queries because most of the numbers are either listed or not that long
        do {
            ContactInfo info = lookupHelper(normalizedNumber, formattedNumber);
            if (info != null) {
                return info;
            }
            // remove the last character of the normalized number string
            normalizedNumber = normalizedNumber.substring(0, normalizedNumber.length() - 1);
        } while (normalizedNumber.length() >= MIN_NUMBER_LENGTH);

        return null;
    }

    private ContactInfo lookupHelper(String normalizedNumber, String formattedNumber)
            throws IOException {
        // build URI
        Uri uri = Uri.parse(REVERSE_LOOKUP_URL)
                .buildUpon()
                .appendQueryParameter("searchinput", normalizedNumber)
                .build();

        // get search results from HTML to speedup subsequent matching
        String output = LookupUtils.firstRegexResult(
                LookupUtils.httpGet(new HttpGet(uri.toString())),
                SEARCH_RESULTS_REGEX, true);

        // get name, abort reverse lookup if nothing found
        String name = LookupUtils.fromHtml(
            LookupUtils.firstRegexResult(output, NAME_REGEX, true));
        if (name == null || name.isEmpty()) {
            return null;
        }
        boolean isBusiness = name.contains(BUSINESS_IDENTIFIER);

        // get phoneNumber and address
        String phoneNumber = LookupUtils.fromHtml(
                LookupUtils.firstRegexResult(output, NUMBER_REGEX, true));
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            phoneNumber = normalizedNumber;
        }
        String address = LookupUtils.fromHtml(
                LookupUtils.firstRegexResult(output, ADDRESS_REGEX, true));

        // cleanup results
        name = cleanupResult(name);
        phoneNumber = cleanupResult(phoneNumber);
        address = cleanupResult(address);

        // build and return contact
        ContactBuilder builder = new ContactBuilder(
                ContactBuilder.REVERSE_LOOKUP,
                normalizedNumber, formattedNumber);
        builder.setName(ContactBuilder.Name.createDisplayName(name));
        builder.addPhoneNumber(ContactBuilder.PhoneNumber.createMainNumber(phoneNumber));
        builder.addWebsite(ContactBuilder.WebsiteUrl.createProfile(uri.toString()));
        builder.addAddress(ContactBuilder.Address.createFormattedHome(address));
        builder.setIsBusiness(isBusiness);

        return builder.build();
    }

    private String cleanupResult(String result) {
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
