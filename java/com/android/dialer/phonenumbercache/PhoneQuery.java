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
 * limitations under the License.
 */

package com.android.dialer.phonenumbercache;

import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;

/** The queries to look up the {@link ContactInfo} for a given number in the Call Log. */
final class PhoneQuery {

  static final int PERSON_ID = 0;
  static final int NAME = 1;
  static final int PHONE_TYPE = 2;
  static final int LABEL = 3;
  static final int MATCHED_NUMBER = 4;
  static final int NORMALIZED_NUMBER = 5;
  static final int PHOTO_ID = 6;
  static final int LOOKUP_KEY = 7;
  static final int PHOTO_URI = 8;
  /** Projection to look up a contact's DISPLAY_NAME_ALTERNATIVE */
  static final String[] DISPLAY_NAME_ALTERNATIVE_PROJECTION =
      new String[] {
        Contacts.DISPLAY_NAME_ALTERNATIVE,
      };

  static final int NAME_ALTERNATIVE = 0;

  static final String[] ADDITIONAL_CONTACT_INFO_PROJECTION =
      new String[] {Phone.DISPLAY_NAME_ALTERNATIVE, Phone.CARRIER_PRESENCE};
  static final int ADDITIONAL_CONTACT_INFO_DISPLAY_NAME_ALTERNATIVE = 0;
  static final int ADDITIONAL_CONTACT_INFO_CARRIER_PRESENCE = 1;

  /**
   * Projection to look up the ContactInfo. Does not include DISPLAY_NAME_ALTERNATIVE as that column
   * isn't available in ContactsCommon.PhoneLookup. We should always use this projection starting
   * from NYC onward.
   */
  private static final String[] PHONE_LOOKUP_PROJECTION =
      new String[] {
        PhoneLookup.CONTACT_ID,
        PhoneLookup.DISPLAY_NAME,
        PhoneLookup.TYPE,
        PhoneLookup.LABEL,
        PhoneLookup.NUMBER,
        PhoneLookup.NORMALIZED_NUMBER,
        PhoneLookup.PHOTO_ID,
        PhoneLookup.LOOKUP_KEY,
        PhoneLookup.PHOTO_URI
      };

  static String[] getPhoneLookupProjection() {
      return PHONE_LOOKUP_PROJECTION;
  }
}
