/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.glidephotomanager.impl;

import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.DisplayNameSources;
import com.android.dialer.glidephotomanager.PhotoInfo;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Generate a lookup URI that will populate the quick contact with the number. Used when the lookup
 * URI is not available (non-contact and no other sources). The info is encoded into a JSON string
 * that is not governed by any public interface. URI format:
 * content://com.android.contacts/contacts/lookup/encoded/[JSON]
 *
 * <p>The JSON is a object containing "display_name", "display_name_source" ({@link
 * DisplayNameSources}), and several {@link ContactsContract.Data} rows keyed by the {@link
 * ContactsContract.Data#MIMETYPE}. In this case only {@link
 * ContactsContract.CommonDataKinds.Phone#CONTENT_ITEM_TYPE} is available.
 *
 * <p>Example JSON:<br>
 * {"display_name":"+1 650-253-0000","display_name_source":30,"vnd.android.cursor.item\/contact":{
 * "vnd.android.cursor.item\/phone_v2":[{"data1":"+1 650-253-0000","data2":12}]}}
 */
final class DefaultLookupUriGenerator {

  static Uri generateUri(PhotoInfo photoInfo) {
    JSONObject lookupJson = new JSONObject();
    try {
      lookupJson.put(Contacts.DISPLAY_NAME, photoInfo.getFormattedNumber());
      // DISPLAY_NAME_SOURCE required by contacts, otherwise the URI will not be recognized.
      lookupJson.put(Contacts.DISPLAY_NAME_SOURCE, DisplayNameSources.PHONE);
      JSONObject contactRows = new JSONObject();
      JSONObject phone = new JSONObject();
      phone.put(CommonDataKinds.Phone.NUMBER, photoInfo.getFormattedNumber());
      contactRows.put(CommonDataKinds.Phone.CONTENT_ITEM_TYPE, new JSONArray().put(phone));

      lookupJson.put(Contacts.CONTENT_ITEM_TYPE, contactRows);
    } catch (JSONException e) {
      throw new AssertionError(e);
    }
    return Contacts.CONTENT_LOOKUP_URI
        .buildUpon()
        .appendPath("encoded")
        .encodedFragment(lookupJson.toString())
        // Directory is required in the URI but it does not exist, use MAX_VALUE to avoid clashing
        // with other directory
        .appendQueryParameter(
            ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(Integer.MAX_VALUE))
        .build();
  }
}
