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
import android.support.annotation.Nullable;
import android.support.v4.util.ArrayMap;
import android.support.v4.util.ArraySet;
import android.telecom.Call;
import android.text.TextUtils;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo.Cp2Info;
import com.android.dialer.phonelookup.PhoneLookupInfo.Cp2Info.Cp2ContactInfo;
import com.android.dialer.phonelookup.database.contract.PhoneLookupHistoryContract.PhoneLookupHistory;
import com.android.dialer.phonenumberproto.DialerPhoneNumberUtil;
import com.android.dialer.storage.Unencrypted;
import com.android.dialer.telecom.TelecomCallUtil;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.inject.Inject;

/** PhoneLookup implementation for local contacts. */
public final class Cp2PhoneLookup implements PhoneLookup<Cp2Info> {

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
        Phone.NUMBER, // 6
        Phone.CONTACT_ID, // 7
        Phone.LOOKUP_KEY // 8
      };

  private static final int CP2_INFO_NAME_INDEX = 0;
  private static final int CP2_INFO_PHOTO_URI_INDEX = 1;
  private static final int CP2_INFO_PHOTO_ID_INDEX = 2;
  private static final int CP2_INFO_TYPE_INDEX = 3;
  private static final int CP2_INFO_LABEL_INDEX = 4;
  private static final int CP2_INFO_NORMALIZED_NUMBER_INDEX = 5;
  private static final int CP2_INFO_NUMBER_INDEX = 6;
  private static final int CP2_INFO_CONTACT_ID_INDEX = 7;
  private static final int CP2_INFO_LOOKUP_KEY_INDEX = 8;

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
  public ListenableFuture<Cp2Info> lookup(Call call) {
    return backgroundExecutorService.submit(() -> lookupInternal(call));
  }

  private Cp2Info lookupInternal(Call call) {
    String rawNumber = TelecomCallUtil.getNumber(call);
    if (TextUtils.isEmpty(rawNumber)) {
      return Cp2Info.getDefaultInstance();
    }
    Optional<String> e164 = TelecomCallUtil.getE164Number(appContext, call);
    Set<Cp2ContactInfo> cp2ContactInfos = new ArraySet<>();
    try (Cursor cursor =
        e164.isPresent()
            ? queryPhoneTableBasedOnE164(CP2_INFO_PROJECTION, ImmutableSet.of(e164.get()))
            : queryPhoneTableBasedOnRawNumber(CP2_INFO_PROJECTION, ImmutableSet.of(rawNumber))) {
      if (cursor == null) {
        LogUtil.w("Cp2PhoneLookup.lookupInternal", "null cursor");
        return Cp2Info.getDefaultInstance();
      }
      while (cursor.moveToNext()) {
        cp2ContactInfos.add(buildCp2ContactInfoFromPhoneCursor(appContext, cursor));
      }
    }
    return Cp2Info.newBuilder().addAllCp2ContactInfo(cp2ContactInfos).build();
  }

  @Override
  public ListenableFuture<Boolean> isDirty(ImmutableSet<DialerPhoneNumber> phoneNumbers) {
    return backgroundExecutorService.submit(() -> isDirtyInternal(phoneNumbers));
  }

  private boolean isDirtyInternal(ImmutableSet<DialerPhoneNumber> phoneNumbers) {
    long lastModified = sharedPreferences.getLong(PREF_LAST_TIMESTAMP_PROCESSED, 0L);
    // We are always going to need to do this check and it is pretty cheap so do it first.
    if (anyContactsDeletedSince(lastModified)) {
      return true;
    }
    // Hopefully the most common case is there are no contacts updated; we can detect this cheaply.
    if (noContactsModifiedSince(lastModified)) {
      return false;
    }
    // This method is more expensive but is probably the most likely scenario; we are looking for
    // changes to contacts which have been called.
    if (contactsUpdated(queryPhoneTableForContactIds(phoneNumbers), lastModified)) {
      return true;
    }
    // This is the most expensive method so do it last; the scenario is that a contact which has
    // been called got disassociated with a number and we need to clear their information.
    if (contactsUpdated(queryPhoneLookupHistoryForContactIds(), lastModified)) {
      return true;
    }
    return false;
  }

  /**
   * Returns set of contact ids that correspond to {@code dialerPhoneNumbers} if the contact exists.
   */
  private Set<Long> queryPhoneTableForContactIds(
      ImmutableSet<DialerPhoneNumber> dialerPhoneNumbers) {
    Set<Long> contactIds = new ArraySet<>();

    PartitionedNumbers partitionedNumbers = new PartitionedNumbers(dialerPhoneNumbers);

    // First use the E164 numbers to query the NORMALIZED_NUMBER column.
    contactIds.addAll(
        queryPhoneTableForContactIdsBasedOnE164(partitionedNumbers.validE164Numbers()));

    // Then run a separate query using the NUMBER column to handle numbers that can't be formatted.
    contactIds.addAll(
        queryPhoneTableForContactIdsBasedOnRawNumber(partitionedNumbers.unformattableNumbers()));

    return contactIds;
  }

  /** Gets all of the contact ids from PhoneLookupHistory. */
  private Set<Long> queryPhoneLookupHistoryForContactIds() {
    Set<Long> contactIds = new ArraySet<>();
    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                PhoneLookupHistory.CONTENT_URI,
                new String[] {
                  PhoneLookupHistory.PHONE_LOOKUP_INFO,
                },
                null,
                null,
                null)) {

      if (cursor == null) {
        LogUtil.w("Cp2PhoneLookup.queryPhoneLookupHistoryForContactIds", "null cursor");
        return contactIds;
      }

      if (cursor.moveToFirst()) {
        int phoneLookupInfoColumn =
            cursor.getColumnIndexOrThrow(PhoneLookupHistory.PHONE_LOOKUP_INFO);
        do {
          PhoneLookupInfo phoneLookupInfo;
          try {
            phoneLookupInfo = PhoneLookupInfo.parseFrom(cursor.getBlob(phoneLookupInfoColumn));
          } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException(e);
          }
          for (Cp2ContactInfo info : phoneLookupInfo.getCp2Info().getCp2ContactInfoList()) {
            contactIds.add(info.getContactId());
          }
        } while (cursor.moveToNext());
      }
    }

    return contactIds;
  }

  private Set<Long> queryPhoneTableForContactIdsBasedOnE164(Set<String> validE164Numbers) {
    Set<Long> contactIds = new ArraySet<>();
    if (validE164Numbers.isEmpty()) {
      return contactIds;
    }
    try (Cursor cursor =
        queryPhoneTableBasedOnE164(new String[] {Phone.CONTACT_ID}, validE164Numbers)) {
      if (cursor == null) {
        LogUtil.w("Cp2PhoneLookup.queryPhoneTableForContactIdsBasedOnE164", "null cursor");
        return contactIds;
      }
      while (cursor.moveToNext()) {
        contactIds.add(cursor.getLong(0 /* columnIndex */));
      }
    }
    return contactIds;
  }

  private Set<Long> queryPhoneTableForContactIdsBasedOnRawNumber(Set<String> unformattableNumbers) {
    Set<Long> contactIds = new ArraySet<>();
    if (unformattableNumbers.isEmpty()) {
      return contactIds;
    }
    try (Cursor cursor =
        queryPhoneTableBasedOnRawNumber(new String[] {Phone.CONTACT_ID}, unformattableNumbers)) {
      if (cursor == null) {
        LogUtil.w("Cp2PhoneLookup.queryPhoneTableForContactIdsBasedOnE164", "null cursor");
        return contactIds;
      }
      while (cursor.moveToNext()) {
        contactIds.add(cursor.getLong(0 /* columnIndex */));
      }
    }
    return contactIds;
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

  private boolean noContactsModifiedSince(long lastModified) {
    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                Contacts.CONTENT_URI,
                new String[] {Contacts._ID},
                Contacts.CONTACT_LAST_UPDATED_TIMESTAMP + " > ?",
                new String[] {Long.toString(lastModified)},
                Contacts._ID + " limit 1")) {
      if (cursor == null) {
        LogUtil.w("Cp2PhoneLookup.noContactsModifiedSince", "null cursor");
        return false;
      }
      return cursor.getCount() == 0;
    }
  }

  /** Returns true if any contacts were deleted after {@code lastModified}. */
  private boolean anyContactsDeletedSince(long lastModified) {
    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                DeletedContacts.CONTENT_URI,
                new String[] {DeletedContacts.CONTACT_DELETED_TIMESTAMP},
                DeletedContacts.CONTACT_DELETED_TIMESTAMP + " > ?",
                new String[] {Long.toString(lastModified)},
                DeletedContacts.CONTACT_DELETED_TIMESTAMP + " limit 1")) {
      if (cursor == null) {
        LogUtil.w("Cp2PhoneLookup.anyContactsDeletedSince", "null cursor");
        return false;
      }
      return cursor.getCount() > 0;
    }
  }

  @Override
  public ListenableFuture<ImmutableMap<DialerPhoneNumber, Cp2Info>> getMostRecentInfo(
      ImmutableMap<DialerPhoneNumber, Cp2Info> existingInfoMap) {
    return backgroundExecutorService.submit(() -> getMostRecentInfoInternal(existingInfoMap));
  }

  @Override
  public void setSubMessage(PhoneLookupInfo.Builder destination, Cp2Info subMessage) {
    destination.setCp2Info(subMessage);
  }

  @Override
  public Cp2Info getSubMessage(PhoneLookupInfo phoneLookupInfo) {
    return phoneLookupInfo.getCp2Info();
  }

  private ImmutableMap<DialerPhoneNumber, Cp2Info> getMostRecentInfoInternal(
      ImmutableMap<DialerPhoneNumber, Cp2Info> existingInfoMap) {
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
    ImmutableMap.Builder<DialerPhoneNumber, Cp2Info> newInfoMapBuilder = ImmutableMap.builder();

    // For each DialerPhoneNumber in existing info...
    for (Entry<DialerPhoneNumber, Cp2Info> entry : existingInfoMap.entrySet()) {
      DialerPhoneNumber dialerPhoneNumber = entry.getKey();
      Cp2Info existingInfo = entry.getValue();

      // Build off the existing info
      Cp2Info.Builder infoBuilder = Cp2Info.newBuilder(existingInfo);

      // If the contact was updated, replace the Cp2ContactInfo list
      if (updatedContacts.containsKey(dialerPhoneNumber)) {
        infoBuilder.clear().addAllCp2ContactInfo(updatedContacts.get(dialerPhoneNumber));

        // If it was deleted and not added to a new contact, clear all the CP2 information.
      } else if (deletedPhoneNumbers.contains(dialerPhoneNumber)) {
        infoBuilder.clear();
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
   * @return Map of {@link DialerPhoneNumber} to {@link Cp2Info} with updated {@link
   *     Cp2ContactInfo}.
   */
  private Map<DialerPhoneNumber, Set<Cp2ContactInfo>> buildMapForUpdatedOrAddedContacts(
      ImmutableMap<DialerPhoneNumber, Cp2Info> existingInfoMap,
      long lastModified,
      Set<DialerPhoneNumber> deletedPhoneNumbers) {

    // Start building a set of DialerPhoneNumbers that we want to update.
    Set<DialerPhoneNumber> updatedNumbers = new ArraySet<>();

    Set<Long> contactIds = new ArraySet<>();
    for (Entry<DialerPhoneNumber, Cp2Info> entry : existingInfoMap.entrySet()) {
      DialerPhoneNumber dialerPhoneNumber = entry.getKey();
      Cp2Info existingInfo = entry.getValue();

      // If the number was deleted, we need to check if it was added to a new contact.
      if (deletedPhoneNumbers.contains(dialerPhoneNumber)) {
        updatedNumbers.add(dialerPhoneNumber);
        continue;
      }

      /// When the PhoneLookupHistory contains no information for a number, because for example the
      // user just upgraded to the new UI, or cleared data, we need to check for updated info.
      if (existingInfo.getCp2ContactInfoCount() == 0) {
        updatedNumbers.add(dialerPhoneNumber);
      } else {
        // For each Cp2ContactInfo for each existing DialerPhoneNumber...
        // Store the contact id if it exist, else automatically add the DialerPhoneNumber to our
        // set of DialerPhoneNumbers we want to update.
        for (Cp2ContactInfo cp2ContactInfo : existingInfo.getCp2ContactInfoList()) {
          long existingContactId = cp2ContactInfo.getContactId();
          if (existingContactId == 0) {
            // If the number doesn't have a contact id, for various reasons, we need to look up the
            // number to check if any exists. The various reasons this might happen are:
            //  - An existing contact that wasn't in the call log is now in the call log.
            //  - A number was in the call log before but has now been added to a contact.
            //  - A number is in the call log, but isn't associated with any contact.
            updatedNumbers.add(dialerPhoneNumber);
          } else {
            contactIds.add(cp2ContactInfo.getContactId());
          }
        }
      }
    }

    // Query the contacts table and get those that whose Contacts.CONTACT_LAST_UPDATED_TIMESTAMP is
    // after lastModified, such that Contacts._ID is in our set of contact IDs we build above.
    if (!contactIds.isEmpty()) {
      try (Cursor cursor = queryContactsTableForContacts(contactIds, lastModified)) {
        int contactIdIndex = cursor.getColumnIndex(Contacts._ID);
        int lastUpdatedIndex = cursor.getColumnIndex(Contacts.CONTACT_LAST_UPDATED_TIMESTAMP);
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
          // Find the DialerPhoneNumber for each contact id and add it to our updated numbers set.
          // These, along with our number not associated with any Cp2ContactInfo need to be updated.
          long contactId = cursor.getLong(contactIdIndex);
          updatedNumbers.addAll(
              findDialerPhoneNumbersContainingContactId(existingInfoMap, contactId));
          long lastUpdatedTimestamp = cursor.getLong(lastUpdatedIndex);
          if (currentLastTimestampProcessed == null
              || currentLastTimestampProcessed < lastUpdatedTimestamp) {
            currentLastTimestampProcessed = lastUpdatedTimestamp;
          }
        }
      }
    }

    if (updatedNumbers.isEmpty()) {
      return new ArrayMap<>();
    }

    Map<DialerPhoneNumber, Set<Cp2ContactInfo>> map = new ArrayMap<>();

    // Divide the numbers into those we can format to E164 and those we can't. Then run separate
    // queries against the contacts table using the NORMALIZED_NUMBER and NUMBER columns.
    // TODO(zachh): These queries are inefficient without a lastModified column to filter on.
    PartitionedNumbers partitionedNumbers = new PartitionedNumbers(updatedNumbers);
    if (!partitionedNumbers.validE164Numbers().isEmpty()) {
      try (Cursor cursor =
          queryPhoneTableBasedOnE164(CP2_INFO_PROJECTION, partitionedNumbers.validE164Numbers())) {
        if (cursor == null) {
          LogUtil.w("Cp2PhoneLookup.buildMapForUpdatedOrAddedContacts", "null cursor");
        } else {
          while (cursor.moveToNext()) {
            String e164Number = cursor.getString(CP2_INFO_NORMALIZED_NUMBER_INDEX);
            Set<DialerPhoneNumber> dialerPhoneNumbers =
                partitionedNumbers.dialerPhoneNumbersForE164(e164Number);
            Cp2ContactInfo info = buildCp2ContactInfoFromPhoneCursor(appContext, cursor);
            addInfo(map, dialerPhoneNumbers, info);

            // We are going to remove the numbers that we've handled so that we later can detect
            // numbers that weren't handled and therefore need to have their contact information
            // removed.
            updatedNumbers.removeAll(dialerPhoneNumbers);
          }
        }
      }
    }
    if (!partitionedNumbers.unformattableNumbers().isEmpty()) {
      try (Cursor cursor =
          queryPhoneTableBasedOnRawNumber(
              CP2_INFO_PROJECTION, partitionedNumbers.unformattableNumbers())) {
        if (cursor == null) {
          LogUtil.w("Cp2PhoneLookup.buildMapForUpdatedOrAddedContacts", "null cursor");
        } else {
          while (cursor.moveToNext()) {
            String unformattableNumber = cursor.getString(CP2_INFO_NUMBER_INDEX);
            Set<DialerPhoneNumber> dialerPhoneNumbers =
                partitionedNumbers.dialerPhoneNumbersForUnformattable(unformattableNumber);
            Cp2ContactInfo info = buildCp2ContactInfoFromPhoneCursor(appContext, cursor);
            addInfo(map, dialerPhoneNumbers, info);

            // We are going to remove the numbers that we've handled so that we later can detect
            // numbers that weren't handled and therefore need to have their contact information
            // removed.
            updatedNumbers.removeAll(dialerPhoneNumbers);
          }
        }
      }
    }
    // The leftovers in updatedNumbers that weren't removed are numbers that were previously
    // associated with contacts, but are no longer. Remove the contact information for them.
    for (DialerPhoneNumber dialerPhoneNumber : updatedNumbers) {
      map.put(dialerPhoneNumber, ImmutableSet.of());
    }
    return map;
  }

  /**
   * Adds the {@code cp2ContactInfo} to the entries for all specified {@code dialerPhoneNumbers} in
   * the {@code map}.
   */
  private static void addInfo(
      Map<DialerPhoneNumber, Set<Cp2ContactInfo>> map,
      Set<DialerPhoneNumber> dialerPhoneNumbers,
      Cp2ContactInfo cp2ContactInfo) {
    for (DialerPhoneNumber dialerPhoneNumber : dialerPhoneNumbers) {
      if (map.containsKey(dialerPhoneNumber)) {
        map.get(dialerPhoneNumber).add(cp2ContactInfo);
      } else {
        Set<Cp2ContactInfo> cp2ContactInfos = new ArraySet<>();
        cp2ContactInfos.add(cp2ContactInfo);
        map.put(dialerPhoneNumber, cp2ContactInfos);
      }
    }
  }

  private Cursor queryPhoneTableBasedOnE164(String[] projection, Set<String> validE164Numbers) {
    return appContext
        .getContentResolver()
        .query(
            Phone.CONTENT_URI,
            projection,
            Phone.NORMALIZED_NUMBER + " IN (" + questionMarks(validE164Numbers.size()) + ")",
            validE164Numbers.toArray(new String[validE164Numbers.size()]),
            null);
  }

  private Cursor queryPhoneTableBasedOnRawNumber(
      String[] projection, Set<String> unformattableNumbers) {
    return appContext
        .getContentResolver()
        .query(
            Phone.CONTENT_URI,
            projection,
            Phone.NUMBER + " IN (" + questionMarks(unformattableNumbers.size()) + ")",
            unformattableNumbers.toArray(new String[unformattableNumbers.size()]),
            null);
  }

  /**
   * @param cursor with projection {@link #CP2_INFO_PROJECTION}.
   * @return new {@link Cp2ContactInfo} based on current row of {@code cursor}.
   */
  private static Cp2ContactInfo buildCp2ContactInfoFromPhoneCursor(
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
      ImmutableMap<DialerPhoneNumber, Cp2Info> existingInfoMap, long lastModified) {
    // Build set of all contact IDs from our existing data. We're going to use this set to query
    // against the DeletedContacts table and see if any of them were deleted.
    Set<Long> contactIds = findContactIdsIn(existingInfoMap);

    // Start building a set of DialerPhoneNumbers that were associated with now deleted contacts.
    try (Cursor cursor = queryDeletedContacts(contactIds, lastModified)) {
      // We now have a cursor/list of contact IDs that were associated with deleted contacts.
      return findDeletedPhoneNumbersIn(existingInfoMap, cursor);
    }
  }

  private Set<Long> findContactIdsIn(ImmutableMap<DialerPhoneNumber, Cp2Info> map) {
    Set<Long> contactIds = new ArraySet<>();
    for (Cp2Info info : map.values()) {
      for (Cp2ContactInfo cp2ContactInfo : info.getCp2ContactInfoList()) {
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
      ImmutableMap<DialerPhoneNumber, Cp2Info> existingInfoMap, Cursor cursor) {
    int contactIdIndex = cursor.getColumnIndexOrThrow(DeletedContacts.CONTACT_ID);
    int deletedTimeIndex = cursor.getColumnIndexOrThrow(DeletedContacts.CONTACT_DELETED_TIMESTAMP);
    Set<DialerPhoneNumber> deletedPhoneNumbers = new ArraySet<>();
    cursor.moveToPosition(-1);
    while (cursor.moveToNext()) {
      long contactId = cursor.getLong(contactIdIndex);
      deletedPhoneNumbers.addAll(
          findDialerPhoneNumbersContainingContactId(existingInfoMap, contactId));
      long deletedTime = cursor.getLong(deletedTimeIndex);
      if (currentLastTimestampProcessed == null || currentLastTimestampProcessed < deletedTime) {
        // TODO(zachh): There's a problem here if a contact for a new row is deleted?
        currentLastTimestampProcessed = deletedTime;
      }
    }
    return deletedPhoneNumbers;
  }

  private static Set<DialerPhoneNumber> findDialerPhoneNumbersContainingContactId(
      ImmutableMap<DialerPhoneNumber, Cp2Info> existingInfoMap, long contactId) {
    Set<DialerPhoneNumber> matches = new ArraySet<>();
    for (Entry<DialerPhoneNumber, Cp2Info> entry : existingInfoMap.entrySet()) {
      for (Cp2ContactInfo cp2ContactInfo : entry.getValue().getCp2ContactInfoList()) {
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

  /**
   * Divides a set of {@link DialerPhoneNumber DialerPhoneNumbers} by those that can be formatted to
   * E164 and those that cannot.
   */
  private static class PartitionedNumbers {
    private Map<String, Set<DialerPhoneNumber>> e164NumbersToDialerPhoneNumbers = new ArrayMap<>();
    private Map<String, Set<DialerPhoneNumber>> unformattableNumbersToDialerPhoneNumbers =
        new ArrayMap<>();

    PartitionedNumbers(Set<DialerPhoneNumber> dialerPhoneNumbers) {
      DialerPhoneNumberUtil dialerPhoneNumberUtil =
          new DialerPhoneNumberUtil(PhoneNumberUtil.getInstance());
      for (DialerPhoneNumber dialerPhoneNumber : dialerPhoneNumbers) {
        Optional<String> e164 = dialerPhoneNumberUtil.formatToE164(dialerPhoneNumber);
        if (e164.isPresent()) {
          String validE164 = e164.get();
          Set<DialerPhoneNumber> currentNumbers = e164NumbersToDialerPhoneNumbers.get(validE164);
          if (currentNumbers == null) {
            currentNumbers = new ArraySet<>();
            e164NumbersToDialerPhoneNumbers.put(validE164, currentNumbers);
          }
          currentNumbers.add(dialerPhoneNumber);
        } else {
          String unformattableNumber = dialerPhoneNumber.getRawInput().getNumber();
          Set<DialerPhoneNumber> currentNumbers =
              unformattableNumbersToDialerPhoneNumbers.get(unformattableNumber);
          if (currentNumbers == null) {
            currentNumbers = new ArraySet<>();
            unformattableNumbersToDialerPhoneNumbers.put(unformattableNumber, currentNumbers);
          }
          currentNumbers.add(dialerPhoneNumber);
        }
      }
    }

    Set<String> unformattableNumbers() {
      return unformattableNumbersToDialerPhoneNumbers.keySet();
    }

    Set<String> validE164Numbers() {
      return e164NumbersToDialerPhoneNumbers.keySet();
    }

    Set<DialerPhoneNumber> dialerPhoneNumbersForE164(String e164) {
      return e164NumbersToDialerPhoneNumbers.get(e164);
    }

    Set<DialerPhoneNumber> dialerPhoneNumbersForUnformattable(String unformattableNumber) {
      return unformattableNumbersToDialerPhoneNumbers.get(unformattableNumber);
    }
  }
}
