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
 * limitations under the License.
 */

package com.android.dialer.shortcuts;

import android.annotation.TargetApi;
import android.content.pm.ShortcutInfo;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.provider.ContactsContract.Contacts;
import android.support.annotation.NonNull;
import com.google.auto.value.AutoValue;

/**
 * Convenience data structure.
 *
 * <p>This differs from {@link ShortcutInfo} in that it doesn't hold an icon or intent, and provides
 * convenience methods for doing things like constructing labels.
 */
@TargetApi(VERSION_CODES.N_MR1) // Shortcuts introduced in N MR1
@AutoValue
abstract class DialerShortcut {

  /** Marker value indicates that shortcut has no setRank. Used by pinned shortcuts. */
  static final int NO_RANK = -1;

  /**
   * Contact ID from contacts provider. Note that this a numeric row ID from the
   * ContactsContract.Contacts._ID column.
   */
  abstract long getContactId();

  /**
   * Lookup key from contacts provider. An example lookup key is: "0r8-47392D". This is the value
   * from ContactsContract.Contacts.LOOKUP_KEY.
   */
  @NonNull
  abstract String getLookupKey();

  /** Display name from contacts provider. */
  @NonNull
  abstract String getDisplayName();

  /**
   * Rank for dynamic shortcuts. This value should be positive or {@link #NO_RANK}.
   *
   * <p>For floating shortcuts (pinned shortcuts with no corresponding dynamic shortcut), setRank
   * has no meaning and the setRank may be set to {@link #NO_RANK}.
   */
  abstract int getRank();

  /** The short label for the shortcut. Used when pinning shortcuts, for example. */
  @NonNull
  String getShortLabel() {
    // Be sure to update getDisplayNameFromShortcutInfo when updating this.
    return getDisplayName();
  }

  /**
   * The long label for the shortcut. Used for shortcuts displayed when pressing and holding the app
   * launcher icon, for example.
   */
  @NonNull
  String getLongLabel() {
    return getDisplayName();
  }

  /** The display name for the provided shortcut. */
  static String getDisplayNameFromShortcutInfo(ShortcutInfo shortcutInfo) {
    return shortcutInfo.getShortLabel().toString();
  }

  /**
   * The id used to identify launcher shortcuts. Used for updating/deleting shortcuts.
   *
   * <p>Lookup keys are used for shortcut IDs. See {@link #getLookupKey()}.
   *
   * <p>If you change this, you probably also need to change {@link #getLookupKeyFromShortcutInfo}.
   */
  @NonNull
  String getShortcutId() {
    return getLookupKey();
  }

  /**
   * Returns the contact lookup key from the provided {@link ShortcutInfo}.
   *
   * <p>Lookup keys are used for shortcut IDs. See {@link #getLookupKey()}.
   */
  @NonNull
  static String getLookupKeyFromShortcutInfo(@NonNull ShortcutInfo shortcutInfo) {
    return shortcutInfo.getId(); // Lookup keys are used for shortcut IDs.
  }

  /**
   * Returns the lookup URI from the provided {@link ShortcutInfo}.
   *
   * <p>Lookup URIs are constructed from lookup key and contact ID. Here is an example lookup URI
   * where lookup key is "0r8-47392D" and contact ID is 8:
   *
   * <p>"content://com.android.contacts/contacts/lookup/0r8-47392D/8"
   */
  @NonNull
  static Uri getLookupUriFromShortcutInfo(@NonNull ShortcutInfo shortcutInfo) {
    long contactId =
        shortcutInfo.getIntent().getLongExtra(ShortcutInfoFactory.EXTRA_CONTACT_ID, -1);
    if (contactId == -1) {
      throw new IllegalStateException("No contact ID found for shortcut: " + shortcutInfo.getId());
    }
    String lookupKey = getLookupKeyFromShortcutInfo(shortcutInfo);
    return Contacts.getLookupUri(contactId, lookupKey);
  }

  /**
   * Contacts provider URI which uses the contact lookup key.
   *
   * <p>Lookup URIs are constructed from lookup key and contact ID. Here is an example lookup URI
   * where lookup key is "0r8-47392D" and contact ID is 8:
   *
   * <p>"content://com.android.contacts/contacts/lookup/0r8-47392D/8"
   */
  @NonNull
  Uri getLookupUri() {
    return Contacts.getLookupUri(getContactId(), getLookupKey());
  }

  /**
   * Given an existing shortcut with the same shortcut ID, returns true if the existing shortcut
   * needs to be updated, e.g. if the contact's name or rank has changed.
   *
   * <p>Does not detect photo updates.
   */
  boolean needsUpdate(@NonNull ShortcutInfo oldInfo) {
    if (this.getRank() != NO_RANK && oldInfo.getRank() != this.getRank()) {
      return true;
    }
    if (!oldInfo.getShortLabel().equals(this.getShortLabel())) {
      return true;
    }
    if (!oldInfo.getLongLabel().equals(this.getLongLabel())) {
      return true;
    }
    return false;
  }

  static Builder builder() {
    return new AutoValue_DialerShortcut.Builder().setRank(NO_RANK);
  }

  @AutoValue.Builder
  abstract static class Builder {

    /**
     * Sets the contact ID. This should be a value from the contact provider's Contact._ID column.
     */
    abstract Builder setContactId(long value);

    /**
     * Sets the lookup key. This should be a contact lookup key as provided by the contact provider.
     */
    abstract Builder setLookupKey(@NonNull String value);

    /** Sets the display name. This should be a value provided by the contact provider. */
    abstract Builder setDisplayName(@NonNull String value);

    /**
     * Sets the rank for the shortcut, used for ordering dynamic shortcuts. This is required for
     * dynamic shortcuts but unused for floating shortcuts because rank has no meaning for floating
     * shortcuts. (Floating shortcuts are shortcuts which are pinned but have no corresponding
     * dynamic shortcut.)
     */
    abstract Builder setRank(int value);

    /** Builds the immutable {@link DialerShortcut} object from this builder. */
    abstract DialerShortcut build();
  }
}
