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
import android.provider.ContactsContract.Contacts;

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

  private ContactsCursorLoader(Context context, String[] contactProjection, String sortKey) {
    super(
        context,
        Contacts.CONTENT_URI
            .buildUpon()
            .appendQueryParameter(Contacts.EXTRA_ADDRESS_BOOK_INDEX, "true")
            .build(),
        contactProjection,
        contactProjection[CONTACT_DISPLAY_NAME] + " IS NOT NULL",
        null,
        sortKey + " ASC");
  }

  public static ContactsCursorLoader createInstanceDisplayNamePrimary(
      Context context, String sortKey) {
    return new ContactsCursorLoader(context, CONTACTS_PROJECTION_DISPLAY_NAME_PRIMARY, sortKey);
  }

  public static ContactsCursorLoader createInstanceDisplayNameAlternative(
      Context context, String sortKey) {
    return new ContactsCursorLoader(context, CONTACTS_PROJECTION_DISPLAY_NAME_ALTERNATIVE, sortKey);
  }
}
