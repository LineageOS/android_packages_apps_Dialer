/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.dialer.contactsfragment;

import android.content.Context;
import android.content.CursorLoader;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;
import com.android.dialer.contacts.ContactsComponent;

/** Cursor Loader for {@link ContactsFragment}. */
final class ContactsCursorLoader extends CursorLoader {

  public static final int CONTACT_ID = 0;
  public static final int CONTACT_DISPLAY_NAME = 1;
  public static final int CONTACT_PHOTO_ID = 2;
  public static final int CONTACT_PHOTO_URI = 3;
  public static final int CONTACT_LOOKUP_KEY = 4;

  public static final String[] CONTACTS_PROJECTION_DISPLAY_NAME_PRIMARY =
      new String[] {
        Contacts._ID, // 0
        Contacts.DISPLAY_NAME_PRIMARY, // 1
        Contacts.PHOTO_ID, // 2
        Contacts.PHOTO_THUMBNAIL_URI, // 3
        Contacts.LOOKUP_KEY, // 4
      };

  public static final String[] CONTACTS_PROJECTION_DISPLAY_NAME_ALTERNATIVE =
      new String[] {
        Contacts._ID, // 0
        Contacts.DISPLAY_NAME_ALTERNATIVE, // 1
        Contacts.PHOTO_ID, // 2
        Contacts.PHOTO_THUMBNAIL_URI, // 3
        Contacts.LOOKUP_KEY, // 4
      };

  ContactsCursorLoader(Context context, boolean hasPhoneNumbers) {
    super(
        context,
        buildUri(""),
        getProjection(context),
        getWhere(context, hasPhoneNumbers),
        null,
        getSortKey(context) + " ASC");
  }

  private static String[] getProjection(Context context) {
    switch (ContactsComponent.get(context).contactDisplayPreferences().getDisplayOrder()) {
      case PRIMARY:
        return CONTACTS_PROJECTION_DISPLAY_NAME_PRIMARY;
      case ALTERNATIVE:
        return CONTACTS_PROJECTION_DISPLAY_NAME_ALTERNATIVE;
    }
    throw new AssertionError("exhaustive switch");
  }

  private static String getWhere(Context context, boolean hasPhoneNumbers) {
    String where = getProjection(context)[CONTACT_DISPLAY_NAME] + " IS NOT NULL";
    if (hasPhoneNumbers) {
      where += " AND " + Contacts.HAS_PHONE_NUMBER + "=1";
    }
    return where;
  }

  private static String getSortKey(Context context) {

    switch (ContactsComponent.get(context).contactDisplayPreferences().getSortOrder()) {
      case BY_PRIMARY:
        return Contacts.SORT_KEY_PRIMARY;
      case BY_ALTERNATIVE:
        return Contacts.SORT_KEY_ALTERNATIVE;
    }
    throw new AssertionError("exhaustive switch");
  }

  /** Update cursor loader to filter contacts based on the provided query. */
  public void setQuery(String query) {
    setUri(buildUri(query));
  }

  private static Uri buildUri(String query) {
    Uri.Builder baseUri;
    if (TextUtils.isEmpty(query)) {
      baseUri = Contacts.CONTENT_URI.buildUpon();
    } else {
      baseUri = Contacts.CONTENT_FILTER_URI.buildUpon().appendPath(query);
    }
    return baseUri.appendQueryParameter(Contacts.EXTRA_ADDRESS_BOOK_INDEX, "true").build();
  }
}
