/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.dialer.app.contactinfo;

import android.support.annotation.IntDef;
import android.text.TextUtils;
import com.android.dialer.phonenumbercache.ContactInfo;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** A request for contact details for the given number, used by the ContactInfoCache. */
public final class ContactInfoRequest implements Comparable<ContactInfoRequest> {

  private static final AtomicLong NEXT_SEQUENCE_NUMBER = new AtomicLong(0);

  private final long sequenceNumber;

  /** The number to look-up. */
  public final String number;
  /** The country in which a call to or from this number was placed or received. */
  public final String countryIso;
  /** The cached contact information stored in the call log. */
  public final ContactInfo callLogInfo;

  /** Is the request a remote lookup. Remote requests are treated as lower priority. */
  @TYPE public final int type;

  /** Specifies the type of the request is. */
  @IntDef(
    value = {
      TYPE_LOCAL,
      TYPE_LOCAL_AND_REMOTE,
      TYPE_REMOTE,
    }
  )
  @Retention(RetentionPolicy.SOURCE)
  public @interface TYPE {}

  public static final int TYPE_LOCAL = 0;
  /** If cannot find the contact locally, do remote lookup later. */
  public static final int TYPE_LOCAL_AND_REMOTE = 1;

  public static final int TYPE_REMOTE = 2;

  public ContactInfoRequest(
      String number, String countryIso, ContactInfo callLogInfo, @TYPE int type) {
    this.sequenceNumber = NEXT_SEQUENCE_NUMBER.getAndIncrement();
    this.number = number;
    this.countryIso = countryIso;
    this.callLogInfo = callLogInfo;
    this.type = type;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof ContactInfoRequest)) {
      return false;
    }

    ContactInfoRequest other = (ContactInfoRequest) obj;

    if (!TextUtils.equals(number, other.number)) {
      return false;
    }
    if (!TextUtils.equals(countryIso, other.countryIso)) {
      return false;
    }
    if (!Objects.equals(callLogInfo, other.callLogInfo)) {
      return false;
    }

    if (type != other.type) {
      return false;
    }

    return true;
  }

  public boolean isLocalRequest() {
    return type == TYPE_LOCAL || type == TYPE_LOCAL_AND_REMOTE;
  }

  @Override
  public int hashCode() {
    return Objects.hash(sequenceNumber, number, countryIso, callLogInfo, type);
  }

  @Override
  public int compareTo(ContactInfoRequest other) {
    // Local query always comes first.
    if (isLocalRequest() && !other.isLocalRequest()) {
      return -1;
    }
    if (!isLocalRequest() && other.isLocalRequest()) {
      return 1;
    }
    // First come first served.
    return sequenceNumber < other.sequenceNumber ? -1 : 1;
  }
}
