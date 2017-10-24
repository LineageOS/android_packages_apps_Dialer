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

package com.android.dialer.searchfragment.testing;

import android.provider.ContactsContract.Data;

/** Testing class containing cp2 cursor testing utilities. */
public final class TestCursorSchema {

  /**
   * If new rows are added to {@link
   * com.android.dialer.searchfragment.common.Projections#CP2_PROJECTION}, this schema should be
   * updated.
   */
  // TODO(67909522): remove these extra columns and remove all references to "Phone."
  public static final String[] SCHEMA =
      new String[] {
        Data._ID, // 0
        "data2", // 1 Phone Type
        "data3", // 2 Phone Label
        "data1", // 3 Phone Number, Organization Company
        Data.DISPLAY_NAME_PRIMARY, // 4
        Data.PHOTO_ID, // 5
        Data.PHOTO_THUMBNAIL_URI, // 6
        Data.LOOKUP_KEY, // 7
        Data.CARRIER_PRESENCE, // 8
        Data.CONTACT_ID, // 9
        Data.MIMETYPE, // 10
        Data.SORT_KEY_PRIMARY, // 11
        "company_name", // 12, no constant because Organization.COMPANY.equals("data1")
        "nick_name" // 13, no constant because Nickname.NAME.equals("data1")
      };
}
