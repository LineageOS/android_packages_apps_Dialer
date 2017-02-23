/*
 * Copyright (C) 2011 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.contacts.common;

import android.content.Context;
import android.content.CursorLoader;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.support.annotation.VisibleForTesting;

/**
 * Used to create {@link CursorLoader} which finds contacts information from the strequents table.
 *
 * <p>Only returns contacts with phone numbers.
 */
public final class ContactTileLoaderFactory {

  /**
   * The _ID field returned for strequent items actually contains data._id instead of contacts._id
   * because the query is performed on the data table. In order to obtain the contact id for
   * strequent items, use Phone.contact_id instead.
   */
  @VisibleForTesting
  public static final String[] COLUMNS_PHONE_ONLY =
      new String[] {
        Contacts._ID,
        Contacts.DISPLAY_NAME_PRIMARY,
        Contacts.STARRED,
        Contacts.PHOTO_URI,
        Contacts.LOOKUP_KEY,
        Phone.NUMBER,
        Phone.TYPE,
        Phone.LABEL,
        Phone.IS_SUPER_PRIMARY,
        Contacts.PINNED,
        Phone.CONTACT_ID,
        Contacts.DISPLAY_NAME_ALTERNATIVE,
      };

  public static CursorLoader createStrequentPhoneOnlyLoader(Context context) {
    Uri uri =
        Contacts.CONTENT_STREQUENT_URI
            .buildUpon()
            .appendQueryParameter(ContactsContract.STREQUENT_PHONE_ONLY, "true")
            .build();

    return new CursorLoader(context, uri, COLUMNS_PHONE_ONLY, null, null, null);
  }
}
