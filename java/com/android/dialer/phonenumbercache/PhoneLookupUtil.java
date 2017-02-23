/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.phonenumbercache;

import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;

public final class PhoneLookupUtil {

  private PhoneLookupUtil() {}

  /** @return the column name that stores contact id for phone lookup query. */
  public static String getContactIdColumnNameForUri(Uri phoneLookupUri) {
    if (VERSION.SDK_INT >= VERSION_CODES.N) {
      return PhoneLookup.CONTACT_ID;
    }
    // In pre-N, contact id is stored in {@link PhoneLookup#_ID} in non-sip query.
    boolean isSip =
        phoneLookupUri.getBooleanQueryParameter(
            ContactsContract.PhoneLookup.QUERY_PARAMETER_SIP_ADDRESS, false);
    return (isSip) ? PhoneLookup.CONTACT_ID : ContactsContract.PhoneLookup._ID;
  }
}
