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
import android.content.SharedPreferences;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.DeletedContacts;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.ArrayMap;
import android.support.v4.util.ArraySet;
import android.telecom.Call;
import android.text.TextUtils;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.common.Assert;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo.Cp2Info;
import com.android.dialer.phonelookup.PhoneLookupInfo.Cp2Info.Cp2ContactInfo;
import com.android.dialer.storage.Unencrypted;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;

/** PhoneLookup implementation for local contacts. */
public final class Cp2PhoneLookup implements PhoneLookup {

  private static final String PREF_LAST_TIMESTAMP_PROCESSED =
      "cp2PhoneLookupLastTimestampProcessed";

  private static final String[] CP2_INFO_PROJECTION =
      new String[] {
        Phone.DISPLAY_NAME_PRIMARY, // 0
        Phone.PHOTO_THUMBNAIL_URI, // 1
        Phone.PHOTO_ID, // 2
        Phone.TYPE, // 3
        Phone.LABEL, // 4
        Phone.NORMALIZED_NUMBER, // 5
        Phone.CONTACT_ID, // 6
        Phone.LOOKUP_KEY // 7
      };

  private static final int CP2_INFO_NAME_INDEX = 0;
  private static final int CP2_INFO_PHOTO_URI_INDEX = 1;
  private static final int CP2_INFO_PHOTO_ID_INDEX = 2;
  private static final int CP2_INFO_TYPE_INDEX = 3;
  private static final int CP2_INFO_LABEL_INDEX = 4;
  private static final int CP2_INFO_NUMBER_INDEX = 5;
  private static final int CP2_INFO_CONTACT_ID_INDEX = 6;
  private static final int CP2_INFO_LOOKUP_KEY_INDEX = 7;

  private final Context appContext;
  private final SharedPreferences sharedPreferences;
  private final ListeningExecutorService backgroundExecutorService;

  @Nullable private Long currentLastTimestampProcessed;

  @Inject
  Cp2PhoneLookup(
      @ApplicationContext Context appContext,
      @Unencrypted SharedPreferences sharedPreferences,
      @BackgroundExecutor ListeningExecutorService backgroundExecutorService) {
    this.appContext = appContext;
    this.sharedPreferences = sharedPreferences;
    this.backgroundExecutorService = backgroundExecutorService;
  }

  @Override
  public ListenableFuture<PhoneLookupInfo> lookup(@NonNull Call call) {
    // TODO(zachh): Implementation.
    // TODO(zachh): Note: Should write empty Cp2Info even when no contact found.
    return backgroundExecutorService.submit(PhoneLookupInfo::getDefaultInstance);
  }

  @Override
  public ListenableFuture<Boolean> isDirty(ImmutableSet<DialerPhoneNumber> phoneNumbers) {
    return backgroundExecutorService.submit(() -> isDirtyInternal(phoneNumbers));
  }

  private boolean isDirtyInternal(ImmutableSet<DialerPhoneNumber> phoneNumbers) {
    long lastModified = sharedPreferences.getLong(PREF_LAST_TIMESTAMP_PROCESSED, 0L);
    return contactsUpdated(queryPhoneTableForContactIds(phoneNumbers), lastModified)
        || contactsDeleted(lastModified);
  }

  /** Returns set of contact ids that correspond to {@code phoneNumbers} if the contact exists. */
  private Set<Long> queryPhoneTableForContactIds(ImmutableSet<DialerPhoneNumber> phoneNumbers) {
    Set<Long> contactIds = new ArraySet<>();
    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                Phone.CONTENT_URI,
                new String[] {Phone.CONTACT_ID},
                Phone.NORMALIZED_NUMBER + " IN (" + questionMarks(phoneNumbers.size()) + ")",
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
    try (Cursor cursor = queryContactsTableForContacts(contactIds, lastModified)) {
      return cursor.getCount() > 0;
    }
  }

  private Cursor queryContactsTableForContacts(Set<Long> contactIds, long lastModified) {
    // Filter to after last modified time based only on contacts we care about
    String where =
        Contacts.CONTACT_LAST_UPDATED_TIMESTAMP
            + " > ?"
            + " AND "
            + Contacts._ID
            + " IN ("
            + questionMarks(contactIds.size())
            + ")";

    String[] args = new String[contactIds.size() + 1];
    args[0] = Long.toString(lastModified);
    int i = 1;
    for (Long contactId : contactIds) {
      args[i++] = Long.toString(contactId);
    }

    return appContext
        .getContentResolver()
        .query(
            Contacts.CONTENT_URI,
            new String[] {Contacts._ID, Contacts.CONTACT_LAST_UPDATED_TIMESTAMP},
            where,
            args,
            null);
  }

  /** Returns true if any contacts were deleted after {@code lastModified}. */
  private boolean contactsDeleted(long lastModified) {
    try (Cursor cursor =
        appContext
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

  @Override
  public ListenableFuture<ImmutableMap<DialerPhoneNumber, PhoneLookupInfo>>
      getMostRecentPhoneLookupInfo(
          ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> existingInfoMap) {
    return backgroundExecutorService.submit(() -> bulkUpdateInternal(existingInfoMap));
  }

  private ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> bulkUpdateInternal(
      ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> existingInfoMap) {
    currentLastTimestampProcessed = null;
    long lastModified = sharedPreferences.getLong(PREF_LAST_TIMESTAMP_PROCESSED, 0L);

    // Build a set of each DialerPhoneNumber that was associated with a contact, and is no longer
    // associated with that same contact.
    Set<DialerPhoneNumber> deletedPhoneNumbers =
        getDeletedPhoneNumbers(existingInfoMap, lastModified);

    // For each DialerPhoneNumber that was associated with a contact or added to a contact,
    // build a map of those DialerPhoneNumbers to a set Cp2ContactInfos, where each Cp2ContactInfo
    // represents a contact.
    Map<DialerPhoneNumber, Set<Cp2ContactInfo>> updatedContacts =
        buildMapForUpdatedOrAddedContacts(existingInfoMap, lastModified, deletedPhoneNumbers);

    // Start build a new map of updated info. This will replace existing info.
    ImmutableMap.Builder<DialerPhoneNumber, PhoneLookupInfo> newInfoMapBuilder =
        ImmutableMap.builder();

    // For each DialerPhoneNumber in existing info...
    for (Entry<DialerPhoneNumber, PhoneLookupInfo> entry : existingInfoMap.entrySet()) {
      DialerPhoneNumber dialerPhoneNumber = entry.getKey();
      PhoneLookupInfo existingInfo = entry.getValue();

      // Build off the existing info
      PhoneLookupInfo.Builder infoBuilder = PhoneLookupInfo.newBuilder(existingInfo);

      // If the contact was updated, replace the Cp2ContactInfo list
      if (updatedContacts.containsKey(dialerPhoneNumber)) {
        infoBuilder.setCp2Info(
            Cp2Info.newBuilder().addAllCp2ContactInfo(updatedContacts.get(dialerPhoneNumber)));

        // If it was deleted and not added to a new contact, replace the Cp2ContactInfo list with
        // the default instance of Cp2ContactInfo
      } else if (deletedPhoneNumbers.contains(dialerPhoneNumber)) {
        infoBuilder.setCp2Info(
            Cp2Info.newBuilder().addCp2ContactInfo(Cp2ContactInfo.getDefaultInstance()));
      }

      // If the DialerPhoneNumber didn't change, add the unchanged existing info.
      newInfoMapBuilder.put(dialerPhoneNumber, infoBuilder.build());
    }
    return newInfoMapBuilder.build();
  }

  @Override
  public ListenableFuture<Void> onSuccessfulBulkUpdate() {
    return backgroundExecutorService.submit(
        () -> {
          if (currentLastTimestampProcessed != null) {
            sharedPreferences
                .edit()
                .putLong(PREF_LAST_TIMESTAMP_PROCESSED, currentLastTimestampProcessed)
                .apply();
          }
          return null;
        });
  }

  /**
   * 1. get all contact ids. if the id is unset, add the number to the list of contacts to look up.
   * 2. reduce our list of contact ids to those that were updated after lastModified. 3. Now we have
   * the smallest set of dialer phone numbers to query cp2 against. 4. build and return the map of
   * dialerphonenumbers to their new Cp2ContactInfo
   *
   * @return Map of {@link DialerPhoneNumber} to {@link PhoneLookupInfo} with updated {@link
   *     Cp2ContactInfo}.
   */
  private Map<DialerPhoneNumber, Set<Cp2ContactInfo>> buildMapForUpdatedOrAddedContacts(
      ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> existingInfoMap,
      long lastModified,
      Set<DialerPhoneNumber> deletedPhoneNumbers) {

    // Start building a set of DialerPhoneNumbers that we want to update.
    Set<DialerPhoneNumber> updatedNumbers = new ArraySet<>();

    Set<Long> contactIds = new ArraySet<>();
    for (Entry<DialerPhoneNumber, PhoneLookupInfo> entry : existingInfoMap.entrySet()) {
      DialerPhoneNumber dialerPhoneNumber = entry.getKey();
      PhoneLookupInfo existingInfo = entry.getValue();

      // If the number was deleted, we need to check if it was added to a new contact.
      if (deletedPhoneNumbers.contains(dialerPhoneNumber)) {
        updatedNumbers.add(dialerPhoneNumber);
        continue;
      }

      // Note: Methods in this class must always set at least one Cp2Info, setting it to
      // getDefaultInstance() if there is no information for the contact.
      Assert.checkState(
          existingInfo.getCp2Info().getCp2ContactInfoCount() > 0, "existing info has no cp2 infos");

      // For each Cp2ContactInfo for each existing DialerPhoneNumber...
      // Store the contact id if it exist, else automatically add the DialerPhoneNumber to our
      // set of DialerPhoneNumbers we want to update.
      for (Cp2ContactInfo cp2ContactInfo : existingInfo.getCp2Info().getCp2ContactInfoList()) {
        if (Objects.equals(cp2ContactInfo, Cp2ContactInfo.getDefaultInstance())) {
          // If the number doesn't have any Cp2ContactInfo set to it, for various reasons, we need
          // to look up the number to check if any exists.
          // The various reasons this might happen are:
          //  - An existing contact that wasn't in the call log is now in the call log.
          //  - A number was in the call log before but has now been added to a contact.
          //  - A number is in the call log, but isn't associated with any contact.
          updatedNumbers.add(dialerPhoneNumber);
        } else {
          contactIds.add(cp2ContactInfo.getContactId());
        }
      }
    }

    // Query the contacts table and get those that whose Contacts.CONTACT_LAST_UPDATED_TIMESTAMP is
    // after lastModified, such that Contacts._ID is in our set of contact IDs we build above.
    try (Cursor cursor = queryContactsTableForContacts(contactIds, lastModified)) {
      int contactIdIndex = cursor.getColumnIndex(Contacts._ID);
      int lastUpdatedIndex = cursor.getColumnIndex(Contacts.CONTACT_LAST_UPDATED_TIMESTAMP);
      cursor.moveToPosition(-1);
      while (cursor.moveToNext()) {
        // Find the DialerPhoneNumber for each contact id and add it to our updated numbers set.
        // These, along with our number not associated with any Cp2ContactInfo need to be updated.
        long contactId = cursor.getLong(contactIdIndex);
        updatedNumbers.addAll(getDialerPhoneNumber(existingInfoMap, contactId));
        long lastUpdatedTimestamp = cursor.getLong(lastUpdatedIndex);
        if (currentLastTimestampProcessed == null
            || currentLastTimestampProcessed < lastUpdatedTimestamp) {
          currentLastTimestampProcessed = lastUpdatedTimestamp;
        }
      }
    }

    // Query the Phone table and build Cp2ContactInfo for each DialerPhoneNumber in our
    // updatedNumbers set.
    Map<DialerPhoneNumber, Set<Cp2ContactInfo>> map = new ArrayMap<>();
    try (Cursor cursor = getAllCp2Rows(updatedNumbers)) {
      cursor.moveToPosition(-1);
      while (cursor.moveToNext()) {
        // Map each dialer phone number to it's new cp2 info
        Set<DialerPhoneNumber> phoneNumbers =
            getDialerPhoneNumbers(updatedNumbers, cursor.getString(CP2_INFO_NUMBER_INDEX));
        Cp2ContactInfo info = buildCp2ContactInfoFromUpdatedContactsCursor(appContext, cursor);
        for (DialerPhoneNumber phoneNumber : phoneNumbers) {
          if (map.containsKey(phoneNumber)) {
            map.get(phoneNumber).add(info);
          } else {
            Set<Cp2ContactInfo> cp2ContactInfos = new ArraySet<>();
            cp2ContactInfos.add(info);
            map.put(phoneNumber, cp2ContactInfos);
          }
        }
      }
    }
    return map;
  }

  /**
   * Returns cursor with projection {@link #CP2_INFO_PROJECTION} and only phone numbers that are in
   * {@code updateNumbers}.
   */
  private Cursor getAllCp2Rows(Set<DialerPhoneNumber> updatedNumbers) {
    String where = Phone.NORMALIZED_NUMBER + " IN (" + questionMarks(updatedNumbers.size()) + ")";
    String[] selectionArgs = new String[updatedNumbers.size()];
    int i = 0;
    for (DialerPhoneNumber phoneNumber : updatedNumbers) {
      selectionArgs[i++] = getNormalizedNumber(phoneNumber);
    }

    return appContext
        .getContentResolver()
        .query(Phone.CONTENT_URI, CP2_INFO_PROJECTION, where, selectionArgs, null);
  }

  /**
   * @param cursor with projection {@link #CP2_INFO_PROJECTION}.
   * @return new {@link Cp2ContactInfo} based on current row of {@code cursor}.
   */
  private static Cp2ContactInfo buildCp2ContactInfoFromUpdatedContactsCursor(
      Context appContext, Cursor cursor) {
    String displayName = cursor.getString(CP2_INFO_NAME_INDEX);
    String photoUri = cursor.getString(CP2_INFO_PHOTO_URI_INDEX);
    int photoId = cursor.getInt(CP2_INFO_PHOTO_ID_INDEX);
    int type = cursor.getInt(CP2_INFO_TYPE_INDEX);
    String label = cursor.getString(CP2_INFO_LABEL_INDEX);
    int contactId = cursor.getInt(CP2_INFO_CONTACT_ID_INDEX);
    String lookupKey = cursor.getString(CP2_INFO_LOOKUP_KEY_INDEX);

    Cp2ContactInfo.Builder infoBuilder = Cp2ContactInfo.newBuilder();
    if (!TextUtils.isEmpty(displayName)) {
      infoBuilder.setName(displayName);
    }
    if (!TextUtils.isEmpty(photoUri)) {
      infoBuilder.setPhotoUri(photoUri);
    }
    if (photoId > 0) {
      infoBuilder.setPhotoId(photoId);
    }

    // Phone.getTypeLabel returns "Custom" if given (0, null) which is not of any use. Just
    // omit setting the label if there's no information for it.
    if (type != 0 || !TextUtils.isEmpty(label)) {
      infoBuilder.setLabel(Phone.getTypeLabel(appContext.getResources(), type, label).toString());
    }
    infoBuilder.setContactId(contactId);
    if (!TextUtils.isEmpty(lookupKey)) {
      infoBuilder.setLookupUri(Contacts.getLookupUri(contactId, lookupKey).toString());
    }
    return infoBuilder.build();
  }

  /** Returns set of DialerPhoneNumbers that were associated with now deleted contacts. */
  private Set<DialerPhoneNumber> getDeletedPhoneNumbers(
      ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> existingInfoMap, long lastModified) {
    // Build set of all contact IDs from our existing data. We're going to use this set to query
    // against the DeletedContacts table and see if any of them were deleted.
    Set<Long> contactIds = findContactIdsIn(existingInfoMap);

    // Start building a set of DialerPhoneNumbers that were associated with now deleted contacts.
    try (Cursor cursor = queryDeletedContacts(contactIds, lastModified)) {
      // We now have a cursor/list of contact IDs that were associated with deleted contacts.
      return findDeletedPhoneNumbersIn(existingInfoMap, cursor);
    }
  }

  private Set<Long> findContactIdsIn(ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> map) {
    Set<Long> contactIds = new ArraySet<>();
    for (PhoneLookupInfo info : map.values()) {
      for (Cp2ContactInfo cp2ContactInfo : info.getCp2Info().getCp2ContactInfoList()) {
        contactIds.add(cp2ContactInfo.getContactId());
      }
    }
    return contactIds;
  }

  private Cursor queryDeletedContacts(Set<Long> contactIds, long lastModified) {
    String where =
        DeletedContacts.CONTACT_DELETED_TIMESTAMP
            + " > ?"
            + " AND "
            + DeletedContacts.CONTACT_ID
            + " IN ("
            + questionMarks(contactIds.size())
            + ")";
    String[] args = new String[contactIds.size() + 1];
    args[0] = Long.toString(lastModified);
    int i = 1;
    for (Long contactId : contactIds) {
      args[i++] = Long.toString(contactId);
    }

    return appContext
        .getContentResolver()
        .query(
            DeletedContacts.CONTENT_URI,
            new String[] {DeletedContacts.CONTACT_ID, DeletedContacts.CONTACT_DELETED_TIMESTAMP},
            where,
            args,
            null);
  }

  /** Returns set of DialerPhoneNumbers that are associated with deleted contact IDs. */
  private Set<DialerPhoneNumber> findDeletedPhoneNumbersIn(
      ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> existingInfoMap, Cursor cursor) {
    int contactIdIndex = cursor.getColumnIndexOrThrow(DeletedContacts.CONTACT_ID);
    int deletedTimeIndex = cursor.getColumnIndexOrThrow(DeletedContacts.CONTACT_DELETED_TIMESTAMP);
    Set<DialerPhoneNumber> deletedPhoneNumbers = new ArraySet<>();
    cursor.moveToPosition(-1);
    while (cursor.moveToNext()) {
      long contactId = cursor.getLong(contactIdIndex);
      deletedPhoneNumbers.addAll(getDialerPhoneNumber(existingInfoMap, contactId));
      long deletedTime = cursor.getLong(deletedTimeIndex);
      if (currentLastTimestampProcessed == null || currentLastTimestampProcessed < deletedTime) {
        // TODO(zachh): There's a problem here if a contact for a new row is deleted?
        currentLastTimestampProcessed = deletedTime;
      }
    }
    return deletedPhoneNumbers;
  }

  private static Set<DialerPhoneNumber> getDialerPhoneNumbers(
      Set<DialerPhoneNumber> phoneNumbers, String number) {
    Set<DialerPhoneNumber> matches = new ArraySet<>();
    for (DialerPhoneNumber phoneNumber : phoneNumbers) {
      if (getNormalizedNumber(phoneNumber).equals(number)) {
        matches.add(phoneNumber);
      }
    }
    Assert.checkArgument(
        matches.size() > 0, "Couldn't find DialerPhoneNumber for number: " + number);
    return matches;
  }

  private static Set<DialerPhoneNumber> getDialerPhoneNumber(
      ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> existingInfoMap, long contactId) {
    Set<DialerPhoneNumber> matches = new ArraySet<>();
    for (Entry<DialerPhoneNumber, PhoneLookupInfo> entry : existingInfoMap.entrySet()) {
      for (Cp2ContactInfo cp2ContactInfo : entry.getValue().getCp2Info().getCp2ContactInfoList()) {
        if (cp2ContactInfo.getContactId() == contactId) {
          matches.add(entry.getKey());
        }
      }
    }
    Assert.checkArgument(
        matches.size() > 0, "Couldn't find DialerPhoneNumber for contact ID: " + contactId);
    return matches;
  }

  private static String questionMarks(int count) {
    StringBuilder where = new StringBuilder();
    for (int i = 0; i < count; i++) {
      if (i != 0) {
        where.append(", ");
      }
      where.append("?");
    }
    return where.toString();
  }
}
