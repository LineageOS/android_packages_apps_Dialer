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
 * limitations under the License.
 */

package com.android.dialer.shortcuts;

import android.support.annotation.NonNull;
import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
 final class AutoValue_DialerShortcut extends DialerShortcut {

  private final long contactId;
  private final String lookupKey;
  private final String displayName;
  private final int rank;

  private AutoValue_DialerShortcut(
      long contactId,
      String lookupKey,
      String displayName,
      int rank) {
    this.contactId = contactId;
    this.lookupKey = lookupKey;
    this.displayName = displayName;
    this.rank = rank;
  }

  @Override
  long getContactId() {
    return contactId;
  }

  @NonNull
  @Override
  String getLookupKey() {
    return lookupKey;
  }

  @NonNull
  @Override
  String getDisplayName() {
    return displayName;
  }

  @Override
  int getRank() {
    return rank;
  }

  @Override
  public String toString() {
    return "DialerShortcut{"
        + "contactId=" + contactId + ", "
        + "lookupKey=" + lookupKey + ", "
        + "displayName=" + displayName + ", "
        + "rank=" + rank
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof DialerShortcut) {
      DialerShortcut that = (DialerShortcut) o;
      return (this.contactId == that.getContactId())
           && (this.lookupKey.equals(that.getLookupKey()))
           && (this.displayName.equals(that.getDisplayName()))
           && (this.rank == that.getRank());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (this.contactId >>> 32) ^ this.contactId;
    h *= 1000003;
    h ^= this.lookupKey.hashCode();
    h *= 1000003;
    h ^= this.displayName.hashCode();
    h *= 1000003;
    h ^= this.rank;
    return h;
  }

  static final class Builder extends DialerShortcut.Builder {
    private Long contactId;
    private String lookupKey;
    private String displayName;
    private Integer rank;
    Builder() {
    }
    private Builder(DialerShortcut source) {
      this.contactId = source.getContactId();
      this.lookupKey = source.getLookupKey();
      this.displayName = source.getDisplayName();
      this.rank = source.getRank();
    }
    @Override
    DialerShortcut.Builder setContactId(long contactId) {
      this.contactId = contactId;
      return this;
    }
    @Override
    DialerShortcut.Builder setLookupKey(String lookupKey) {
      this.lookupKey = lookupKey;
      return this;
    }
    @Override
    DialerShortcut.Builder setDisplayName(String displayName) {
      this.displayName = displayName;
      return this;
    }
    @Override
    DialerShortcut.Builder setRank(int rank) {
      this.rank = rank;
      return this;
    }
    @Override
    DialerShortcut build() {
      String missing = "";
      if (this.contactId == null) {
        missing += " contactId";
      }
      if (this.lookupKey == null) {
        missing += " lookupKey";
      }
      if (this.displayName == null) {
        missing += " displayName";
      }
      if (this.rank == null) {
        missing += " rank";
      }
      if (!missing.isEmpty()) {
        throw new IllegalStateException("Missing required properties:" + missing);
      }
      return new AutoValue_DialerShortcut(
          this.contactId,
          this.lookupKey,
          this.displayName,
          this.rank);
    }
  }

}