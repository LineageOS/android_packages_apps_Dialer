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
 * limitations under the License
 */

package com.android.dialer.phonelookup.cp2;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.text.TextUtils;
import com.android.dialer.phonelookup.PhoneLookupInfo.Cp2Info.Cp2ContactInfo;

/**
 * A class providing projection-related functionality for {@link
 * com.android.dialer.phonelookup.PhoneLookup} implementations for ContactsProvider2 (CP2).
 */
final class Cp2Projections {

  // Projection for performing lookups using the PHONE table
  private static final String[] PHONE_PROJECTION =
      new String[] {
        Phone.DISPLAY_NAME_PRIMARY, // 0
        Phone.PHOTO_THUMBNAIL_URI, // 1
        Phone.PHOTO_ID, // 2
        Phone.TYPE, // 3
        Phone.LABEL, // 4
        Phone.NORMALIZED_NUMBER, // 5
        Phone.CONTACT_ID, // 6
        Phone.LOOKUP_KEY // 7
      };

  // Projection for performing lookups using the PHONE_LOOKUP table
  private static final String[] PHONE_LOOKUP_PROJECTION =
      new String[] {
        PhoneLookup.DISPLAY_NAME_PRIMARY, // 0
        PhoneLookup.PHOTO_THUMBNAIL_URI, // 1
        PhoneLookup.PHOTO_ID, // 2
        PhoneLookup.TYPE, // 3
        PhoneLookup.LABEL, // 4
        PhoneLookup.NORMALIZED_NUMBER, // 5
        PhoneLookup.CONTACT_ID, // 6
        PhoneLookup.LOOKUP_KEY // 7
      };

  // The following indexes should match both PHONE_PROJECTION and PHONE_LOOKUP_PROJECTION above.
  private static final int CP2_INFO_NAME_INDEX = 0;
  private static final int CP2_INFO_PHOTO_URI_INDEX = 1;
  private static final int CP2_INFO_PHOTO_ID_INDEX = 2;
  private static final int CP2_INFO_TYPE_INDEX = 3;
  private static final int CP2_INFO_LABEL_INDEX = 4;
  private static final int CP2_INFO_NORMALIZED_NUMBER_INDEX = 5;
  private static final int CP2_INFO_CONTACT_ID_INDEX = 6;
  private static final int CP2_INFO_LOOKUP_KEY_INDEX = 7;

  private Cp2Projections() {}

  static String[] getProjectionForPhoneTable() {
    return PHONE_PROJECTION;
  }

  static String[] getProjectionForPhoneLookupTable() {
    return PHONE_LOOKUP_PROJECTION;
  }

  /**
   * Builds a {@link Cp2ContactInfo} based on the current row of {@code cursor}, of which the
   * projection is either {@link #PHONE_PROJECTION} or {@link #PHONE_LOOKUP_PROJECTION}.
   */
  static Cp2ContactInfo buildCp2ContactInfoFromCursor(Context appContext, Cursor cursor) {
    String displayName = cursor.getString(CP2_INFO_NAME_INDEX);
    String photoUri = cursor.getString(CP2_INFO_PHOTO_URI_INDEX);
    int photoId = cursor.getInt(CP2_INFO_PHOTO_ID_INDEX);
    int type = cursor.getInt(CP2_INFO_TYPE_INDEX);
    String label = cursor.getString(CP2_INFO_LABEL_INDEX);
    int contactId = cursor.getInt(CP2_INFO_CONTACT_ID_INDEX);
    String lookupKey = cursor.getString(CP2_INFO_LOOKUP_KEY_INDEX);

    Cp2ContactInfo.Builder infoBuilder = Cp2ContactInfo.newBuilder();
    if (!TextUtils.isEmpty(displayName)) {
      infoBuilder.setName(displayName);
    }
    if (!TextUtils.isEmpty(photoUri)) {
      infoBuilder.setPhotoUri(photoUri);
    }
    if (photoId > 0) {
      infoBuilder.setPhotoId(photoId);
    }

    // Phone.getTypeLabel returns "Custom" if given (0, null) which is not of any use. Just
    // omit setting the label if there's no information for it.
    if (type != 0 || !TextUtils.isEmpty(label)) {
      infoBuilder.setLabel(Phone.getTypeLabel(appContext.getResources(), type, label).toString());
    }
    infoBuilder.setContactId(contactId);
    if (!TextUtils.isEmpty(lookupKey)) {
      infoBuilder.setLookupUri(Contacts.getLookupUri(contactId, lookupKey).toString());
    }
    return infoBuilder.build();
  }

  /** Returns the normalized number in the current row of {@code cursor}. */
  static String getNormalizedNumberFromCursor(Cursor cursor) {
    return cursor.getString(CP2_INFO_NORMALIZED_NUMBER_INDEX);
  }
}
