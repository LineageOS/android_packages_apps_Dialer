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

import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import com.android.contacts.common.ContactsUtils.UserType;
import com.android.contacts.common.util.UriUtils;
import com.android.dialer.logging.ContactSource;

/** Information for a contact as needed by the Call Log. */
public class ContactInfo {

  public static final ContactInfo EMPTY = new ContactInfo();
  public Uri lookupUri;
  /**
   * Contact lookup key. Note this may be a lookup key for a corp contact, in which case "lookup by
   * lookup key" doesn't work on the personal profile.
   */
  public String lookupKey;

  public String name;
  public String nameAlternative;
  public int type;
  public String label;
  public String number;
  public String formattedNumber;
  public String geoDescription;
  /*
   * ContactInfo.normalizedNumber is a column value returned by PhoneLookup query. By definition,
   * it's E164 representation.
   * http://developer.android.com/reference/android/provider/ContactsContract.PhoneLookupColumns.
   * html#NORMALIZED_NUMBER.
   *
   * The fallback value, when PhoneLookup fails or else, should be either null or
   * PhoneNumberUtils.formatNumberToE164.
   */
  public String normalizedNumber;
  /** The photo for the contact, if available. */
  public long photoId;
  /** The high-res photo for the contact, if available. */
  public Uri photoUri;

  public boolean isBadData;
  public String objectId;
  public @UserType long userType;
  public @NonNull ContactSource.Type sourceType = ContactSource.Type.UNKNOWN_SOURCE_TYPE;
  /**
   * True if local contact exists. This is only used for Cequint Caller ID so it won't overwrite
   * photo if local contact exists.
   */
  public boolean contactExists;

  /** @see android.provider.ContactsContract.CommonDataKinds.Phone#CARRIER_PRESENCE */
  public int carrierPresence;

  @Override
  public int hashCode() {
    // Uses only name and contactUri to determine hashcode.
    // This should be sufficient to have a reasonable distribution of hash codes.
    // Moreover, there should be no two people with the same lookupUri.
    final int prime = 31;
    int result = 1;
    result = prime * result + ((lookupUri == null) ? 0 : lookupUri.hashCode());
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    ContactInfo other = (ContactInfo) obj;
    if (!UriUtils.areEqual(lookupUri, other.lookupUri)) {
      return false;
    }
    if (!TextUtils.equals(name, other.name)) {
      return false;
    }
    if (!TextUtils.equals(nameAlternative, other.nameAlternative)) {
      return false;
    }
    if (type != other.type) {
      return false;
    }
    if (!TextUtils.equals(label, other.label)) {
      return false;
    }
    if (!TextUtils.equals(number, other.number)) {
      return false;
    }
    if (!TextUtils.equals(formattedNumber, other.formattedNumber)) {
      return false;
    }
    if (!TextUtils.equals(normalizedNumber, other.normalizedNumber)) {
      return false;
    }
    if (photoId != other.photoId) {
      return false;
    }
    if (!UriUtils.areEqual(photoUri, other.photoUri)) {
      return false;
    }
    if (!TextUtils.equals(objectId, other.objectId)) {
      return false;
    }
    if (userType != other.userType) {
      return false;
    }
    if (carrierPresence != other.carrierPresence) {
      return false;
    }
    if (!TextUtils.equals(geoDescription, other.geoDescription)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ContactInfo{"
        + "lookupUri="
        + lookupUri
        + ", name='"
        + name
        + '\''
        + ", nameAlternative='"
        + nameAlternative
        + '\''
        + ", type="
        + type
        + ", label='"
        + label
        + '\''
        + ", number='"
        + number
        + '\''
        + ", formattedNumber='"
        + formattedNumber
        + '\''
        + ", normalizedNumber='"
        + normalizedNumber
        + '\''
        + ", photoId="
        + photoId
        + ", photoUri="
        + photoUri
        + ", objectId='"
        + objectId
        + '\''
        + ", userType="
        + userType
        + ", carrierPresence="
        + carrierPresence
        + ", geoDescription="
        + geoDescription
        + '}';
  }
}
