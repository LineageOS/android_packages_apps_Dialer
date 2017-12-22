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
 * limitations under the License
 */

package com.android.dialer.speeddial;

import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;

/** Cursor Loader for strequent contacts. */
final class StrequentContactsCursorLoader extends CursorLoader {

  static final int PHONE_ID = 0;
  static final int PHONE_DISPLAY_NAME = 1;
  static final int PHONE_STARRED = 2;
  static final int PHONE_PHOTO_URI = 3;
  static final int PHONE_LOOKUP_KEY = 4;
  static final int PHONE_PHOTO_ID = 5;
  static final int PHONE_NUMBER = 6;
  static final int PHONE_TYPE = 7;
  static final int PHONE_LABEL = 8;
  static final int PHONE_IS_SUPER_PRIMARY = 9;
  static final int PHONE_PINNED = 10;
  static final int PHONE_CONTACT_ID = 11;

  static final String[] PHONE_PROJECTION =
      new String[] {
        Phone._ID, // 0
        Phone.DISPLAY_NAME, // 1
        Phone.STARRED, // 2
        Phone.PHOTO_URI, // 3
        Phone.LOOKUP_KEY, // 4
        Phone.PHOTO_ID, // 5
        Phone.NUMBER, // 6
        Phone.TYPE, // 7
        Phone.LABEL, // 8
        Phone.IS_SUPER_PRIMARY, // 9
        Phone.PINNED, // 10
        Phone.CONTACT_ID, // 11
      };

  StrequentContactsCursorLoader(Context context) {
    super(
        context,
        buildUri(),
        PHONE_PROJECTION,
        null /* selection */,
        null /* selectionArgs */,
        null /* sortOrder */);
    // TODO(calderwoodra): implement alternative display names
  }

  static void addToCursor(MatrixCursor dest, Cursor source) {
    dest.newRow()
        .add(PHONE_PROJECTION[PHONE_ID], source.getLong(PHONE_ID))
        .add(PHONE_PROJECTION[PHONE_DISPLAY_NAME], source.getString(PHONE_DISPLAY_NAME))
        .add(PHONE_PROJECTION[PHONE_STARRED], source.getInt(PHONE_STARRED))
        .add(PHONE_PROJECTION[PHONE_PHOTO_URI], source.getString(PHONE_PHOTO_URI))
        .add(PHONE_PROJECTION[PHONE_LOOKUP_KEY], source.getString(PHONE_LOOKUP_KEY))
        .add(PHONE_PROJECTION[PHONE_NUMBER], source.getString(PHONE_NUMBER))
        .add(PHONE_PROJECTION[PHONE_TYPE], source.getInt(PHONE_TYPE))
        .add(PHONE_PROJECTION[PHONE_LABEL], source.getString(PHONE_LABEL))
        .add(PHONE_PROJECTION[PHONE_IS_SUPER_PRIMARY], source.getInt(PHONE_IS_SUPER_PRIMARY))
        .add(PHONE_PROJECTION[PHONE_PINNED], source.getInt(PHONE_PINNED))
        .add(PHONE_PROJECTION[PHONE_CONTACT_ID], source.getLong(PHONE_CONTACT_ID));
  }

  private static Uri buildUri() {
    return Contacts.CONTENT_STREQUENT_URI
        .buildUpon()
        .appendQueryParameter(ContactsContract.STREQUENT_PHONE_ONLY, "true")
        .build();
  }

  @Override
  public Cursor loadInBackground() {
    return SpeedDialCursor.newInstance(super.loadInBackground());
  }
}
