/*
 * Copyright (C) 2014 The OmniROM Project
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

// Partially based on OmniROM's implementation

package com.android.dialer.lookup.openstreetmap;

import android.content.Context;
import android.location.Location;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.util.Log;

import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.lookup.ContactBuilder;
import com.android.dialer.lookup.ForwardLookup;
import com.android.dialer.lookup.LookupUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

public class OpenStreetMapForwardLookup extends ForwardLookup {
    private static final String TAG =
            OpenStreetMapForwardLookup.class.getSimpleName();

    /** Search within radius (meters) */
    private static final int RADIUS = 30000;

    /** Query URL */
    private static final String LOOKUP_URL =
            "https://overpass-api.de/api/interpreter";

    private static final String LOOKUP_QUERY =
            "[out:json];node[name~\"%s\"][phone](around:%d,%f,%f);out body;";

    private static final String RESULT_ELEMENTS = "elements";
    private static final String RESULT_TAGS = "tags";
    private static final String TAG_NAME = "name";
    private static final String TAG_PHONE = "phone";
    private static final String TAG_HOUSENUMBER = "addr:housenumber";
    private static final String TAG_STREET = "addr:street";
    private static final String TAG_CITY = "addr:city";
    private static final String TAG_POSTCODE = "addr:postcode";
    private static final String TAG_WEBSITE = "website";

    public OpenStreetMapForwardLookup(Context context) {
    }

    @Override
    public ContactInfo[] lookup(Context context,
            String filter, Location lastLocation) {

        // The OSM API doesn't support case-insentive searches, but does
        // support regular expressions.
        String regex = "";
        for (int i = 0; i < filter.length(); i++) {
            char c = filter.charAt(i);
            regex += "[" + Character.toUpperCase(c)
                    + Character.toLowerCase(c) + "]";
        }

        String request = String.format(Locale.ENGLISH, LOOKUP_QUERY, regex,
                RADIUS, lastLocation.getLatitude(), lastLocation.getLongitude());

        try {
            return getEntries(new JSONObject(LookupUtils.httpPost(LOOKUP_URL, null, request)));
        } catch (IOException e) {
            Log.e(TAG, "Failed to execute query", e);
        } catch (JSONException e) {
            Log.e(TAG, "JSON error", e);
        }

        return null;
    }

    private ContactInfo[] getEntries(JSONObject results)
            throws JSONException {
        ArrayList<ContactInfo> details =
                new ArrayList<ContactInfo>();

        JSONArray elements = results.getJSONArray(RESULT_ELEMENTS);

        for (int i = 0; i < elements.length(); i++) {
            try {
                JSONObject element = elements.getJSONObject(i);
                JSONObject tags = element.getJSONObject(RESULT_TAGS);

                String displayName = tags.getString(TAG_NAME);
                String phoneNumber = tags.getString(TAG_PHONE);

                // Take the first number if there are multiple
                if (phoneNumber.contains(";")) {
                    phoneNumber = phoneNumber.split(";")[0];
                    phoneNumber = phoneNumber.trim();
                }

                // The address is split
                String addressHouseNumber =
                        tags.optString(TAG_HOUSENUMBER, null);
                String addressStreet = tags.optString(TAG_STREET, null);
                String addressCity = tags.optString(TAG_CITY, null);
                String addressPostCode = tags.optString(TAG_POSTCODE, null);

                String address = String.format(
                        "%s %s, %s %s",
                        addressHouseNumber != null ? addressHouseNumber : "",
                        addressStreet != null ? addressStreet : "",
                        addressCity != null ? addressCity : "",
                        addressPostCode != null ? addressPostCode : "");

                address = address.trim().replaceAll("\\s+", " ");

                if (address.length() == 0) {
                    address = null;
                }

                String website = tags.optString(TAG_WEBSITE, null);

                ContactBuilder builder = new ContactBuilder(
                        ContactBuilder.FORWARD_LOOKUP, null, phoneNumber);

                builder.setName(ContactBuilder.Name.createDisplayName(displayName));
                builder.addPhoneNumber(ContactBuilder.PhoneNumber.createMainNumber(phoneNumber));

                ContactBuilder.Address a = new ContactBuilder.Address();
                a.formattedAddress = address;
                a.city = addressCity;
                a.street = addressStreet;
                a.postCode = addressPostCode;
                a.type = StructuredPostal.TYPE_WORK;
                builder.addAddress(a);

                ContactBuilder.WebsiteUrl w = new ContactBuilder.WebsiteUrl();
                w.url = website;
                w.type = Website.TYPE_HOMEPAGE;
                builder.addWebsite(w);

                builder.setPhotoUri(ContactBuilder.PHOTO_URI_BUSINESS);

                details.add(builder.build());
            } catch (JSONException e) {
                Log.e(TAG, "Skipping the suggestions at index " + i, e);
            }
        }

        if (details.size() > 0) {
            return details.toArray(new ContactInfo[details.size()]);
        } else {
            return null;
        }
    }
}
