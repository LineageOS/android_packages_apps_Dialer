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

package com.android.dialer.phonelookup.cp2;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.DeletedContacts;
import android.support.v4.util.ArraySet;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.common.concurrent.DialerExecutors;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Set;

/** PhoneLookup implementation for local contacts. */
public final class Cp2PhoneLookup implements PhoneLookup {

  private final Context context;

  Cp2PhoneLookup(Context context) {
    this.context = context;
  }

  @Override
  public ListenableFuture<Boolean> isDirty(
      ImmutableSet<DialerPhoneNumber> phoneNumbers, long lastModified) {
    // TODO(calderwoodra): consider a different thread pool
    return MoreExecutors.listeningDecorator(DialerExecutors.getLowPriorityThreadPool(context))
        .submit(() -> isDirtyInternal(phoneNumbers, lastModified));
  }

  private boolean isDirtyInternal(ImmutableSet<DialerPhoneNumber> phoneNumbers, long lastModified) {
    return contactsUpdated(getContactIdsFromPhoneNumbers(phoneNumbers), lastModified)
        || contactsDeleted(lastModified);
  }

  /** Returns set of contact ids that correspond to {@code phoneNumbers} if the contact exists. */
  private Set<Long> getContactIdsFromPhoneNumbers(ImmutableSet<DialerPhoneNumber> phoneNumbers) {
    Set<Long> contactIds = new ArraySet<>();
    try (Cursor cursor =
        context
            .getContentResolver()
            .query(
                Phone.CONTENT_URI,
                new String[] {Phone.CONTACT_ID},
                columnInSetWhereStatement(Phone.NORMALIZED_NUMBER, phoneNumbers.size()),
                contactIdsSelectionArgs(phoneNumbers),
                null)) {
      cursor.moveToPosition(-1);
      while (cursor.moveToNext()) {
        contactIds.add(cursor.getLong(0 /* columnIndex */));
      }
    }
    return contactIds;
  }

  private static String[] contactIdsSelectionArgs(ImmutableSet<DialerPhoneNumber> phoneNumbers) {
    String[] args = new String[phoneNumbers.size()];
    int i = 0;
    for (DialerPhoneNumber phoneNumber : phoneNumbers) {
      args[i++] = getNormalizedNumber(phoneNumber);
    }
    return args;
  }

  private static String getNormalizedNumber(DialerPhoneNumber phoneNumber) {
    // TODO(calderwoodra): implement normalization logic that matches contacts.
    return phoneNumber.getRawInput().getNumber();
  }

  /** Returns true if any contacts were modified after {@code lastModified}. */
  private boolean contactsUpdated(Set<Long> contactIds, long lastModified) {
    try (Cursor cursor =
        context
            .getContentResolver()
            .query(
                Contacts.CONTENT_URI,
                new String[] {Contacts._ID},
                contactsIsDirtyWhereStatement(contactIds.size()),
                contactsIsDirtySelectionArgs(lastModified, contactIds),
                null)) {
      return cursor.getCount() > 0;
    }
  }

  private static String contactsIsDirtyWhereStatement(int numberOfContactIds) {
    StringBuilder where = new StringBuilder();
    // Filter to after last modified time
    where.append(Contacts.CONTACT_LAST_UPDATED_TIMESTAMP).append(" > ?");

    // Filter based only on contacts we care about
    where.append(" AND ").append(columnInSetWhereStatement(Contacts._ID, numberOfContactIds));
    return where.toString();
  }

  private String[] contactsIsDirtySelectionArgs(long lastModified, Set<Long> contactIds) {
    String[] args = new String[contactIds.size() + 1];
    args[0] = Long.toString(lastModified);
    int i = 1;
    for (Long contactId : contactIds) {
      args[i++] = Long.toString(contactId);
    }
    return args;
  }

  /** Returns true if any contacts were deleted after {@code lastModified}. */
  private boolean contactsDeleted(long lastModified) {
    try (Cursor cursor =
        context
            .getContentResolver()
            .query(
                DeletedContacts.CONTENT_URI,
                new String[] {DeletedContacts.CONTACT_DELETED_TIMESTAMP},
                DeletedContacts.CONTACT_DELETED_TIMESTAMP + " > ?",
                new String[] {Long.toString(lastModified)},
                null)) {
      return cursor.getCount() > 0;
    }
  }

  private static String columnInSetWhereStatement(String columnName, int setSize) {
    StringBuilder where = new StringBuilder();
    where.append(columnName).append(" IN (");
    for (int i = 0; i < setSize; i++) {
      if (i != 0) {
        where.append(", ");
      }
      where.append("?");
    }
    return where.append(")").toString();
  }

  @Override
  public ListenableFuture<ImmutableMap<DialerPhoneNumber, PhoneLookupInfo>> bulkUpdate(
      ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> existingInfoMap, long lastModified) {
    // TODO(calderwoodra)
    return null;
  }
}
