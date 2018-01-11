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

package com.android.dialer.phonelookup.database.contract;

import android.net.Uri;
import com.android.dialer.constants.Constants;

/** Contract for the PhoneLookupHistory content provider. */
public class PhoneLookupHistoryContract {
  public static final String AUTHORITY = Constants.get().getPhoneLookupHistoryProviderAuthority();

  public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

  /** PhoneLookupHistory table. */
  public static final class PhoneLookupHistory {

    public static final String TABLE = "PhoneLookupHistory";

    public static final String NUMBER_QUERY_PARAM = "number";

    /** The content URI for this table. */
    public static final Uri CONTENT_URI =
        Uri.withAppendedPath(PhoneLookupHistoryContract.CONTENT_URI, TABLE);

    /** Returns a URI for a specific normalized number */
    public static Uri contentUriForNumber(String normalizedNumber) {
      return CONTENT_URI
          .buildUpon()
          .appendQueryParameter(NUMBER_QUERY_PARAM, Uri.encode(normalizedNumber))
          .build();
    }

    /** The MIME type of a {@link android.content.ContentProvider#getType(Uri)} single entry. */
    public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/phone_lookup_history";

    /**
     * The phone number's E164 representation if it has one, or otherwise normalized number if it
     * cannot be normalized to E164. Required, primary key for the table.
     *
     * <p>Type: TEXT
     */
    public static final String NORMALIZED_NUMBER = "normalized_number";

    /**
     * The {@link com.android.dialer.phonelookup.PhoneLookupInfo} proto for the number. Required.
     *
     * <p>Type: BLOB
     */
    public static final String PHONE_LOOKUP_INFO = "phone_lookup_info";

    /**
     * Epoch time in milliseconds this entry was last modified. Required.
     *
     * <p>Type: INTEGER (long)
     */
    public static final String LAST_MODIFIED = "last_modified";
  }
}
