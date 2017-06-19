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

/** Class containing relevant projections for searching contacts. */
public class Projections {

  public static final int PHONE_ID = 0;
  public static final int PHONE_TYPE = 1;
  public static final int PHONE_LABEL = 2;
  public static final int PHONE_NUMBER = 3;
  public static final int PHONE_DISPLAY_NAME = 4;
  public static final int PHONE_PHOTO_ID = 5;
  public static final int PHONE_PHOTO_URI = 6;
  public static final int PHONE_LOOKUP_KEY = 7;
  public static final int PHONE_CARRIER_PRESENCE = 8;

  @SuppressWarnings("unused")
  public static final int PHONE_SORT_KEY = 9;

  public static final String[] PHONE_PROJECTION =
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
        Phone.SORT_KEY_PRIMARY // 9
      };
}
