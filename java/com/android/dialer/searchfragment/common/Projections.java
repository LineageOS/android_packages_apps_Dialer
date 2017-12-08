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

package com.android.dialer.searchfragment.common;

import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;

/** Class containing relevant projections for searching contacts. */
public class Projections {

  public static final int ID = 0;
  public static final int PHONE_TYPE = 1;
  public static final int PHONE_LABEL = 2;
  public static final int PHONE_NUMBER = 3;
  public static final int DISPLAY_NAME = 4;
  public static final int PHOTO_ID = 5;
  public static final int PHOTO_URI = 6;
  public static final int LOOKUP_KEY = 7;
  public static final int CARRIER_PRESENCE = 8;
  public static final int CONTACT_ID = 9;

  @SuppressWarnings("unused")
  public static final int SORT_KEY = 10;

  public static final int SORT_ALTERNATIVE = 11;
  public static final int MIME_TYPE = 12;
  public static final int COMPANY_NAME = 13;
  public static final int NICKNAME = 14;

  public static final String[] CP2_PROJECTION =
      new String[] {
        Phone._ID, // 0
        Phone.TYPE, // 1
        Phone.LABEL, // 2
        Phone.NUMBER, // 3
        Phone.DISPLAY_NAME_PRIMARY, // 4
        Phone.PHOTO_ID, // 5
        Phone.PHOTO_THUMBNAIL_URI, // 6
        Phone.LOOKUP_KEY, // 7
        Phone.CARRIER_PRESENCE, // 8
        Phone.CONTACT_ID, // 9
        Phone.SORT_KEY_PRIMARY, // 10
        Phone.SORT_KEY_ALTERNATIVE, // 11
        // Data.MIMETYPE, // 12
        // Organization.COMPANY, // 13
        // Nickname.NAME // 14
      };

  // Uses alternative display names (i.e. "Bob Dylan" becomes "Dylan, Bob").
  public static final String[] CP2_PROJECTION_ALTERNATIVE =
      new String[] {
        Data._ID, // 0
        Phone.TYPE, // 1
        Phone.LABEL, // 2
        Phone.NUMBER, // 3
        Data.DISPLAY_NAME_ALTERNATIVE, // 4
        Data.PHOTO_ID, // 5
        Data.PHOTO_THUMBNAIL_URI, // 6
        Data.LOOKUP_KEY, // 7
        Data.CARRIER_PRESENCE, // 8
        Data.CONTACT_ID, // 9
        Data.SORT_KEY_PRIMARY, // 11
        Data.SORT_KEY_ALTERNATIVE, // 12
        // Data.MIMETYPE, // 12
        // Organization.COMPANY, // 13
        // Nickname.NAME // 14
      };

  public static final String[] DATA_PROJECTION =
      new String[] {
        Data._ID, // 0
        Phone.TYPE, // 1
        Phone.LABEL, // 2
        Phone.NUMBER, // 3
        Data.DISPLAY_NAME_PRIMARY, // 4
        Data.PHOTO_ID, // 5
        Data.PHOTO_THUMBNAIL_URI, // 6
        Data.LOOKUP_KEY, // 7
        Data.CARRIER_PRESENCE, // 8
        Data.CONTACT_ID, // 9
        Data.MIMETYPE, // 10
        Data.SORT_KEY_PRIMARY, // 11
      };
}
