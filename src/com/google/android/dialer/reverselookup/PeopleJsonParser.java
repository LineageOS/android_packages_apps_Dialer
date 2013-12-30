/*
  * Copyright (C) 2013 Xiao-Long Chen <chenxiaolong@cxl.epac.to>
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

/*
 * This is a reverse-engineered implementation of com.google.android.Dialer.
 * There is no guarantee that this implementation will work correctly or even
 * work at all. Use at your own risk.
 */

package com.google.android.dialer.reverselookup;

import com.android.incallui.Log;
import com.google.android.dialer.util.JsonUtil;
import com.google.common.collect.ImmutableMap;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Pair;

import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PeopleJsonParser {
    private static final String TAG = PeopleJsonParser.class.getSimpleName();
    public static final String DEFAULT_BUSINESS_PHOTO_URI_STRING = new Uri.Builder()
            .scheme("android.resource")
            .authority("com.google.android.dialer")
            .appendPath(String.valueOf(2130837645))
            .build()
            .toString();

    private static final Map<String, String> ADDRESS_FIELDS =
            new ImmutableMap.Builder<String, String>()
            .put("value", "data1")
            .put("type", "data2")
            .build();
    private static final Map<String, Integer> ADDRESS_TYPE_MAP =
            new ImmutableMap.Builder<String, Integer>()
            .put("home", 1)
            .put("work", 2)
            .put("other", 3)
            .build();
    private static final Map<String, String> NAME_FIELDS =
            new ImmutableMap.Builder<String, String>()
            .put("displayName", "data1")
            .put("givenName", "data2")
            .put("familyName", "data3")
            .put("honorificPrefix", "data4")
            .put("middleName", "data5")
            .put("honorificSuffix", "data6")
            .put("phoneticGivenName", "data7")
            .put("phoneticFamilyName", "data9")
            .build();
    private static final Map<String, Integer> PHONE_TYPE_MAP =
            new ImmutableMap.Builder<String, Integer>()
            .put("home", 1)
            .put("work", 3)
            .put("mobile", 2)
            .put("homeFax", 5)
            .put("workFax", 4)
            .put("otherFax", 13)
            .put("pager", 6)
            .put("workMobile", 17)
            .put("workPager", 18)
            .put("main", 12)
            .put("googleVoice", 0)
            .put("other", 7)
            .build();

    public static JSONObject buildContactAddressRow(JSONObject json) throws JSONException {
        JSONObject addresses = getArrayItem(json, "addresses");
        if (addresses == null) {
            return null;
        }

        String value = addresses.getString("value");

        Pair<Integer, String> addressType = parseAddressType(
                addresses.optString("type", null),
                addresses.optString("formattedType", null));

        return new JSONObject()
                .put("data1", value)
                .putOpt("data2", addressType.first)
                .putOpt("data3", addressType.second);
    }

    private static JSONObject buildUrlJsonRecord(String url) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("data1", url);
        return json;
    }

    public static JSONObject findPhoneObject(JSONObject json, String number) throws JSONException {
        JSONArray phoneNumbers = json.optJSONArray("phoneNumbers");
        if (phoneNumbers != null) {
            for (int i = 0; i < phoneNumbers.length(); i++) {
                JSONObject phoneNumber = phoneNumbers.getJSONObject(i);
                if (phoneNumber != null && number.equals(
                        phoneNumber.optString("canonicalizedForm", null))) {
                    return phoneNumber;
                }
            }
            return null;
        }
        return null;
    }

    public static JSONObject getArrayItem(JSONObject json, String s) throws JSONException {
        JSONArray array = json.optJSONArray(s);
        if (array == null || array.length() == 0) {
            return null;
        }
        return array.getJSONObject(0);
    }

    public static String[] getArrayOfStrings(JSONObject json, String s) throws JSONException {
        JSONArray array = json.optJSONArray(s);
        String[] strArray;
        if (array == null || array.length() == 0) {
            strArray = null;
        } else {
            strArray = new String[array.length()];
            for (int i = 0; i < array.length(); i++) {
                strArray[i] = array.getString(i);
            }
        }
        return strArray;
    }

    // TODO: What is s and s2?
    private static String getFirstImageUrl(JSONObject json, String s) throws JSONException {
        JSONArray images = json.optJSONArray("images");
        if (images != null) {
            String s2 = null;
            for (int i = 0; i < images.length(); ++i) {
                JSONObject image = images.getJSONObject(i);
                if (image != null) {
                    JSONObject metadata = image.optJSONObject("metadata");
                    if (metadata == null || !"contact".equals(metadata.optString("container"))) {
                        String url = image.optString("url", null);
                        if (!TextUtils.isEmpty(url)) {
                            if (!url.startsWith(s)) {
                                return url;
                            }
                            s2 = url;
                        }
                    }
                }
            }
            return s2;
        }
        return null;
    }

    private static JSONObject getRemappedDataObject(JSONObject json,
            Map<String, String> map) throws JSONException {
        JSONObject newJson = new JSONObject();
        for (String key : map.keySet()) {
            newJson.putOpt(map.get(key), json.optString(key, null));
        }
        return newJson;
    }

    private static String[] getUrls(JSONObject json) throws JSONException {
        JSONArray urls = json.optJSONArray("urls");
        String[] strUrls;
        if (urls == null) {
            strUrls = null;
        } else {
            strUrls = new String[urls.length()];
            for (int i = 0; i < urls.length(); ++i) {
                strUrls[i] = urls.getJSONObject(i).getString("value");
            }
        }
        return strUrls;
    }

    // TODO: What is s?
    private static void handleMalformedJsonError(String response, String s) {
        Log.e(TAG, s);
        Log.d(TAG, "Json response: " + response);
    }

    // TODO: What is s?
    private static void handleMalformedJsonError(String response, String s, Exception e) {
        Log.e(TAG, s, e);
        Log.d(TAG, "Json response: " + response);
    }

    private static boolean isPersonItem(JSONObject json) throws JSONException {
        String objectType = json.optString("objectType", null);
        return objectType == null || !objectType.equals("page");
    }

    // TODO: What are all these variables?
    public static Pair<Integer, String> parseAddressType(String s, String s2) {
        if (s == null) {
            return Pair.create(null, null);
        }

        Integer n = ADDRESS_TYPE_MAP.get(s);

        if (n != null && n != 0) {
            return Pair.create(n, null);
        } else {
            return Pair.create(n, s2);
        }
    }

    // TODO: What is s2 and s3?
    private static PhoneNumberInfoImpl parseContactJson(JSONObject json,
            String normalizedNumber, String s2, String s3) throws JSONException {
        JSONObject metadata = json.optJSONObject("metadata");
        boolean personItem = true;
        String[] attributions = null;
        if (metadata != null) {
            personItem = isPersonItem(metadata);
            attributions = getArrayOfStrings(metadata, "attributions");
        }

        Integer displayNameSource = personItem ? 40 : 30;
        String number = s2 != null ? s2 : normalizedNumber;

        int type = personItem ? 2 : 12;

        JSONObject contact = new JSONObject();
        JSONObject names = getArrayItem(json, "names");

        String displayName = null;
        if (names != null) {
            displayName = names.getString("displayName");
            contact.put("vnd.android.cursor.item/name",
                    getRemappedDataObject(names, NAME_FIELDS));
        }

        JSONObject phoneObject = findPhoneObject(json, normalizedNumber);

        String label = null;
        if (phoneObject != null) {
            number = phoneObject.getString("value");
            Pair<Integer, String> phoneType = parsePhoneType(
                    phoneObject.getString("type"),
                    phoneObject.optString("formattedType",
                    null));
            type = (int)phoneType.first;
            label = (String)phoneType.second;
        }

        contact.put("vnd.android.cursor.item/phone_v2",
                JsonUtil.newJsonArray(new JSONObject()
                .put("data1", number)
                .put("data2", type)
                .putOpt("data3", label)));

        String photoUri = null;
        if (attributions == null) {
            if (!personItem) {
                contact.putOpt("vnd.android.cursor.item/postal-address_v2",
                        JsonUtil.newJsonArray(buildContactAddressRow(json)));
                populateUrlData(contact, getUrls(json));
            }
            photoUri = getFirstImageUrl(json, s3);
        }

        if (!personItem && photoUri == null) {
            photoUri = DEFAULT_BUSINESS_PHOTO_URI_STRING;
        }

        return new PhoneNumberInfoImpl(
                displayName, normalizedNumber, number, type, label, photoUri,
                new JSONObject()
                .put("display_name", displayName)
                .put("display_name_source", displayNameSource)
                .putOpt("photo_uri", photoUri)
                .put("vnd.android.cursor.item/contact", contact)
                .toString(),
                !personItem);
    }

    // TODO: What is s, s2, s3, s4?
    public static PhoneNumberInfoImpl parsePeopleJson(String s, String s2, String s3, String s4) {
        try {
            JSONObject json = new JSONObject(s);
            String kind = json.getString("kind");
            if (!"plus#peopleList".equals(kind)) {
                handleMalformedJsonError(s,
                        "Unknown 'kind' when trying to parse people response: " + kind);
                return null;
            }
            JSONObject items = getArrayItem(json, "items");
            if (items != null) {
                return parseContactJson(items, s2, s3, s4);
            }
        } catch (JSONException e) {
            handleMalformedJsonError(s, "Required fields not found in json.", e);
        }
        return null;
    }

    // TODO: What are all these variables?
    public static Pair<Integer, String> parsePhoneType(String s, String s2) {
        Integer n = PHONE_TYPE_MAP.get(s);
        if (n != null && n != 0) {
            return Pair.create(n, null);
        } else {
            return Pair.create(n, s2);
        }
    }

    private static void populateUrlData(JSONObject json, String[] urls) throws JSONException {
        if (urls == null) {
            return;
        }
        JSONArray array = new JSONArray();
        for (int length = urls.length, i = 0; i < length; ++i) {
            array.put(buildUrlJsonRecord(urls[i]));
        }
        json.put("vnd.android.cursor.item/website", array);
    }
}
