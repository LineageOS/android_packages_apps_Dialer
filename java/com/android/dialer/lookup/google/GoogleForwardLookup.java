/*
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

package com.android.dialer.lookup.google;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.text.Html;
import android.util.Log;

import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.lookup.ContactBuilder;
import com.android.dialer.lookup.ForwardLookup;
import com.android.dialer.lookup.LookupUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class GoogleForwardLookup extends ForwardLookup {
  private static final String TAG = GoogleForwardLookup.class.getSimpleName();

  private static final boolean DEBUG = false;

  private static final String QUERY_FILTER = "q";
  private static final String QUERY_LANGUAGE = "hl";
  private static final String QUERY_LOCATION = "sll";
  private static final String QUERY_RADIUS = "radius";
  private static final String QUERY_RANDOM = "gs_gbg";

  private static final String RESULT_ADDRESS = "a";
  private static final String RESULT_NUMBER = "b";
  private static final String RESULT_DISTANCE = "c";
  private static final String RESULT_PHOTO_URI = "d";
  private static final String RESULT_WEBSITE = "f";
  private static final String RESULT_CITY = "g";

  /** Base for the query URL */
  private static final String LOOKUP_URL = "https://www.google.com/complete/search?gs_ri=dialer";

  /** Minimum query length
   * (default for dialer_nearby_places_min_query_len) */
  private static final int MIN_QUERY_LEN = 2;

  /** Maximum query length
   * (default for dialer_nearby_places_max_query_len) */
  private static final int MAX_QUERY_LEN = 50;

  /** Radius (in miles)
   * (default for dialer_nearby_places_directory_radius_meters) */
  private static final int RADIUS = 1000;

  /** User agent string */
  private final String userAgent;

  public GoogleForwardLookup(Context context) {
    StringBuilder sb = new StringBuilder("GoogleDialer ");
    try {
      sb.append(context.getPackageManager().getPackageInfo(
          context.getPackageName(), 0).versionName);
      sb.append(" ");
      sb.append(Build.FINGERPRINT);
    } catch (PackageManager.NameNotFoundException e) {
      sb.setLength(0);
    }
    userAgent = sb.toString();
  }

  @Override
  public List<ContactInfo> lookup(Context context, String filter, Location lastLocation) {
    int length = filter.length();

    if (length >= MIN_QUERY_LEN) {
      if (length > MAX_QUERY_LEN) {
        filter = filter.substring(0, MAX_QUERY_LEN);
      }

      try {
        Uri.Builder builder = Uri.parse(LOOKUP_URL).buildUpon();

        // Query string
        builder.appendQueryParameter(QUERY_FILTER, filter);

        // Language
        builder.appendQueryParameter(QUERY_LANGUAGE,
            context.getResources().getConfiguration().locale.getLanguage());

        // Location (latitude and longitude)
        builder.appendQueryParameter(QUERY_LOCATION,
            String.format("%f,%f", lastLocation.getLatitude(), lastLocation.getLongitude()));

        // Radius distance
        builder.appendQueryParameter(QUERY_RADIUS, Integer.toString(RADIUS));

        // Random string (not really required)
        builder.appendQueryParameter(QUERY_RANDOM, getRandomNoiseString());

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", userAgent);
        JSONArray results = new JSONArray(
            LookupUtils.httpGet(builder.build().toString(), headers));

        if (DEBUG) Log.v(TAG, "Results: " + results);

        return getEntries(results);
      } catch (IOException e) {
        Log.e(TAG, "Failed to execute query", e);
      } catch (JSONException e) {
        Log.e(TAG, "JSON error", e);
      }
    }

    return null;
  }

  /**
   * Parse JSON results and return them as an array of ContactInfo
   *
   * @param results The JSON results returned from the server
   * @return Array of ContactInfo containing the result information
   */
  private List<ContactInfo> getEntries(JSONArray results) throws JSONException {
    ArrayList<ContactInfo> details = new ArrayList<>();
    JSONArray entries = results.getJSONArray(1);

    for (int i = 0; i < entries.length(); i++) {
      try {
        JSONArray entry = entries.getJSONArray(i);

        String displayName = decodeHtml(entry.getString(0));

        JSONObject params = entry.getJSONObject(3);

        String phoneNumber = decodeHtml(params.getString(RESULT_NUMBER));

        String address = decodeHtml(params.getString(RESULT_ADDRESS));
        String city = decodeHtml(params.getString(RESULT_CITY));

        String profileUrl = params.optString(RESULT_WEBSITE, null);
        String photoUri = params.optString(RESULT_PHOTO_URI, null);

        ContactBuilder.Address a = new ContactBuilder.Address();
        a.formattedAddress = address;
        a.city = city;
        a.type = StructuredPostal.TYPE_WORK;

        details.add(ContactBuilder.forForwardLookup(phoneNumber)
            .setName(ContactBuilder.Name.createDisplayName(displayName))
            .addPhoneNumber(ContactBuilder.PhoneNumber.createMainNumber(phoneNumber))
            .addWebsite(ContactBuilder.WebsiteUrl.createProfile(profileUrl))
            .addAddress(a)
            .setPhotoUri(photoUri != null ? photoUri : ContactBuilder.PHOTO_URI_BUSINESS)
            .build());
      } catch (JSONException e) {
        Log.e(TAG, "Skipping the suggestions at index " + i, e);
      }
    }

    return details;
  }

  /**
   * Generate a random string of alphanumeric characters of length [4, 36)
   *
   * @return Random alphanumeric string
   */
  private String getRandomNoiseString() {
    StringBuilder garbage = new StringBuilder();
    int length = getRandomInteger(32) + 4;

    for (int i = 0; i < length; i++) {
      int asciiCode;

      if (Math.random() >= 0.3) {
        if (Math.random() <= 0.5) {
          // Lowercase letters
          asciiCode = getRandomInteger(26) + 97;
        } else {
          // Uppercase letters
          asciiCode = getRandomInteger(26) + 65;
        }
      } else {
        // Numbers
        asciiCode = getRandomInteger(10) + 48;
      }

      garbage.append(Character.toString((char) asciiCode));
    }

    return garbage.toString();
  }

  /**
   * Generate number in the range [0, max).
   *
   * @param max Upper limit (non-inclusive)
   * @return Random number inside [0, max)
   */
  private int getRandomInteger(int max) {
    return (int) Math.floor(Math.random() * max);
  }

  /**
   * Convert HTML to unformatted plain text.
   *
   * @param s HTML content
   * @return Unformatted plain text
   */
  private String decodeHtml(String s) {
    return Html.fromHtml(s).toString();
  }
}
