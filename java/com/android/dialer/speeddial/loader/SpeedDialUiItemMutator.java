/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.speeddial.loader;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.os.Trace;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.support.annotation.MainThread;
import android.support.annotation.WorkerThread;
import android.util.ArrayMap;
import android.util.ArraySet;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.common.concurrent.DefaultFutureCallback;
import com.android.dialer.common.concurrent.DialerExecutor.SuccessListener;
import com.android.dialer.common.concurrent.DialerFutureSerializer;
import com.android.dialer.common.database.Selection;
import com.android.dialer.contacts.ContactsComponent;
import com.android.dialer.contacts.displaypreference.ContactDisplayPreferences;
import com.android.dialer.contacts.displaypreference.ContactDisplayPreferences.DisplayOrder;
import com.android.dialer.contacts.hiresphoto.HighResolutionPhotoRequester;
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.speeddial.database.SpeedDialEntry;
import com.android.dialer.speeddial.database.SpeedDialEntry.Channel;
import com.android.dialer.speeddial.database.SpeedDialEntryDao;
import com.android.dialer.speeddial.database.SpeedDialEntryDatabaseHelper;
import com.android.dialer.util.CallUtil;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Loads a list of {@link SpeedDialUiItem SpeedDialUiItems}.
 *
 * @see #loadSpeedDialUiItems()
 *     <ol>
 *       <li>Retrieve the list of {@link SpeedDialEntry} from {@link SpeedDialEntryDatabaseHelper}.
 *       <li>Build a list of {@link SpeedDialUiItem} based on {@link SpeedDialEntry#lookupKey()} in
 *           {@link Phone#CONTENT_URI}.
 *       <li>Remove any {@link SpeedDialEntry} that is no longer starred or whose contact was
 *           deleted.
 *       <li>Update each {@link SpeedDialEntry} contact id, lookup key and channel.
 *       <li>Build a list of {@link SpeedDialUiItem} from starred contacts.
 *       <li>If any contacts in that list aren't in the {@link SpeedDialEntryDatabaseHelper}, insert
 *           them now.
 *       <li>Notify the {@link SuccessListener} of the complete list of {@link SpeedDialUiItem
 *           SpeedDialContacts} composed from {@link SpeedDialEntry SpeedDialEntries} and
 *           non-starred {@link Contacts#STREQUENT_PHONE_ONLY}.
 *     </ol>
 */
@Singleton
public final class SpeedDialUiItemMutator {

  private final Context appContext;
  private final ListeningExecutorService backgroundExecutor;
  // Used to ensure that only one refresh flow runs at a time.
  private final DialerFutureSerializer dialerFutureSerializer = new DialerFutureSerializer();
  private final ContactDisplayPreferences contactDisplayPreferences;
  private final HighResolutionPhotoRequester highResolutionPhotoRequester;

  @Inject
  public SpeedDialUiItemMutator(
      @ApplicationContext Context appContext,
      @BackgroundExecutor ListeningExecutorService backgroundExecutor,
      ContactDisplayPreferences contactDisplayPreferences,
      HighResolutionPhotoRequester highResolutionPhotoRequester) {
    this.appContext = appContext;
    this.backgroundExecutor = backgroundExecutor;
    this.contactDisplayPreferences = contactDisplayPreferences;
    this.highResolutionPhotoRequester = highResolutionPhotoRequester;
  }

  /**
   * Returns a {@link ListenableFuture} for a list of {@link SpeedDialUiItem SpeedDialUiItems}. This
   * list is composed of starred contacts from {@link SpeedDialEntryDatabaseHelper}.
   */
  public ListenableFuture<ImmutableList<SpeedDialUiItem>> loadSpeedDialUiItems() {
    return dialerFutureSerializer.submit(this::loadSpeedDialUiItemsInternal, backgroundExecutor);
  }

  /**
   * Delete the SpeedDialUiItem.
   *
   * <p>If the item is starred, it's entry will be removed from the SpeedDialEntry database.
   * Additionally, if the contact only has one entry in the database, it will be unstarred.
   *
   * <p>If the item isn't starred, it's usage data will be deleted.
   *
   * @return the updated list of SpeedDialUiItems.
   */
  public ListenableFuture<ImmutableList<SpeedDialUiItem>> removeSpeedDialUiItem(
      SpeedDialUiItem speedDialUiItem) {
    return dialerFutureSerializer.submit(
        () -> removeSpeedDialUiItemInternal(speedDialUiItem), backgroundExecutor);
  }

  @WorkerThread
  private ImmutableList<SpeedDialUiItem> removeSpeedDialUiItemInternal(
      SpeedDialUiItem speedDialUiItem) {
    Assert.isWorkerThread();
    Assert.checkArgument(speedDialUiItem.isStarred());
    removeStarredSpeedDialUiItem(speedDialUiItem);
    return loadSpeedDialUiItemsInternal();
  }

  /**
   * Delete the SpeedDialEntry associated with the passed in SpeedDialUiItem. Additionally, if the
   * entry being deleted is the only entry for that contact, unstar it in the cp2.
   */
  @WorkerThread
  private void removeStarredSpeedDialUiItem(SpeedDialUiItem speedDialUiItem) {
    Assert.isWorkerThread();
    Assert.checkArgument(speedDialUiItem.isStarred());
    SpeedDialEntryDao db = getSpeedDialEntryDao();
    ImmutableList<SpeedDialEntry> entries = db.getAllEntries();

    SpeedDialEntry entryToDelete = null;
    int entriesForTheSameContact = 0;
    for (SpeedDialEntry entry : entries) {
      if (entry.contactId() == speedDialUiItem.contactId()) {
        entriesForTheSameContact++;
      }

      if (Objects.equals(entry.id(), speedDialUiItem.speedDialEntryId())) {
        Assert.checkArgument(entryToDelete == null);
        entryToDelete = entry;
      }
    }
    db.delete(ImmutableList.of(entryToDelete.id()));
    if (entriesForTheSameContact == 1) {
      unstarContact(speedDialUiItem);
    }
  }

  @WorkerThread
  private void unstarContact(SpeedDialUiItem speedDialUiItem) {
    Assert.isWorkerThread();
    ContentValues contentValues = new ContentValues();
    contentValues.put(Phone.STARRED, 0);
    appContext
        .getContentResolver()
        .update(
            Contacts.CONTENT_URI,
            contentValues,
            Contacts._ID + " = ?",
            new String[] {Long.toString(speedDialUiItem.contactId())});
  }

  /**
   * Takes a contact uri from {@link Phone#CONTENT_URI} and updates {@link Phone#STARRED} to be
   * true, if it isn't already or Inserts the contact into the {@link SpeedDialEntryDatabaseHelper}
   */
  public ListenableFuture<ImmutableList<SpeedDialUiItem>> starContact(Uri contactUri) {
    return dialerFutureSerializer.submit(
        () -> insertNewContactEntry(contactUri), backgroundExecutor);
  }

  @WorkerThread
  private ImmutableList<SpeedDialUiItem> insertNewContactEntry(Uri contactUri) {
    Assert.isWorkerThread();
    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                contactUri,
                SpeedDialUiItem.getPhoneProjection(isPrimaryDisplayNameOrder()),
                null,
                null,
                null)) {
      if (cursor == null) {
        LogUtil.e("SpeedDialUiItemMutator.insertNewContactEntry", "Cursor was null");
        return loadSpeedDialUiItemsInternal();
      }
      Assert.checkArgument(cursor.moveToFirst(), "Cursor should never be empty");
      SpeedDialUiItem item =
          SpeedDialUiItem.fromCursor(
              appContext.getResources(), cursor, CallUtil.isVideoEnabled(appContext));

      // Star the contact if it isn't starred already, then return.
      if (!item.isStarred()) {
        ContentValues values = new ContentValues();
        values.put(Phone.STARRED, "1");
        appContext
            .getContentResolver()
            .update(
                Contacts.CONTENT_URI,
                values,
                Contacts._ID + " = ?",
                new String[] {Long.toString(item.contactId())});
      }

      // Insert a new entry into the SpeedDialEntry database
      getSpeedDialEntryDao().insert(item.buildSpeedDialEntry());
    }
    return loadSpeedDialUiItemsInternal();
  }

  @WorkerThread
  private ImmutableList<SpeedDialUiItem> loadSpeedDialUiItemsInternal() {
    Trace.beginSection("loadSpeedDialUiItemsInternal");
    Assert.isWorkerThread();
    Trace.beginSection("getAllEntries");
    SpeedDialEntryDao db = getSpeedDialEntryDao();
    Trace.endSection(); // getAllEntries

    // This is the list of contacts that we will display to the user
    List<SpeedDialUiItem> speedDialUiItems = new ArrayList<>();

    // We'll use these lists to update the SpeedDialEntry database
    List<SpeedDialEntry> entriesToInsert = new ArrayList<>();
    List<SpeedDialEntry> entriesToUpdate = new ArrayList<>();
    List<Long> entriesToDelete = new ArrayList<>();

    // Get all SpeedDialEntries and update their contact ids and lookupkeys.
    List<SpeedDialEntry> entries = db.getAllEntries();
    entries = updateContactIdsAndLookupKeys(entries);

    // Build SpeedDialUiItems from our updated entries.
    Map<SpeedDialEntry, SpeedDialUiItem> entriesToUiItems = getSpeedDialUiItemsFromEntries(entries);
    Assert.checkArgument(
        entries.size() == entriesToUiItems.size(),
        "Updated entries are incomplete: " + entries.size() + " != " + entriesToUiItems.size());

    // Mark the SpeedDialEntries to be updated or deleted
    Trace.beginSection("updateOrDeleteEntries");
    for (SpeedDialEntry entry : entries) {
      SpeedDialUiItem contact = entriesToUiItems.get(entry);
      // Remove contacts that no longer exist or are no longer starred
      if (contact == null || !contact.isStarred()) {
        entriesToDelete.add(entry.id());
        continue;
      }

      // Contact exists, so update its entry in SpeedDialEntry Database
      entriesToUpdate.add(
          entry
              .toBuilder()
              .setLookupKey(contact.lookupKey())
              .setContactId(contact.contactId())
              .setDefaultChannel(contact.defaultChannel())
              .build());

      // These are our existing starred entries
      speedDialUiItems.add(contact);
    }
    Trace.endSection(); // updateOrDeleteEntries

    // Get all starred contacts
    List<SpeedDialUiItem> starredContacts = getStarredContacts();
    // If it is starred and not already accounted for above, then insert into the SpeedDialEntry DB.
    Trace.beginSection("addStarredContact");
    for (SpeedDialUiItem contact : starredContacts) {
      if (speedDialUiItems.stream().noneMatch(c -> c.contactId() == contact.contactId())) {
        entriesToInsert.add(contact.buildSpeedDialEntry());

        // These are our newly starred contacts
        speedDialUiItems.add(contact);
      }
    }
    Trace.endSection(); // addStarredContact

    Trace.beginSection("insertUpdateAndDelete");
    requestHighResolutionPhoto(entriesToInsert);
    ImmutableMap<SpeedDialEntry, Long> insertedEntriesToIdsMap =
        db.insertUpdateAndDelete(
            ImmutableList.copyOf(entriesToInsert),
            ImmutableList.copyOf(entriesToUpdate),
            ImmutableList.copyOf(entriesToDelete));
    Trace.endSection(); // insertUpdateAndDelete
    Trace.endSection(); // loadSpeedDialUiItemsInternal
    return speedDialUiItemsWithUpdatedIds(speedDialUiItems, insertedEntriesToIdsMap);
  }

  @WorkerThread
  private void requestHighResolutionPhoto(List<SpeedDialEntry> newEntries) {
    ContactsComponent.get(appContext).highResolutionPhotoLoader();
    for (SpeedDialEntry entry : newEntries) {
      Uri uri;
      uri = Contacts.getLookupUri(entry.contactId(), entry.lookupKey());

      Futures.addCallback(
          highResolutionPhotoRequester.request(uri),
          new DefaultFutureCallback<>(),
          MoreExecutors.directExecutor());
    }
  }

  /**
   * Since newly starred contacts sometimes aren't in the SpeedDialEntry database, we couldn't set
   * their ids when we created our initial list of {@link SpeedDialUiItem speedDialUiItems}. Now
   * that we've inserted the entries into the database and we have their ids, build a new list of
   * speedDialUiItems with the now known ids.
   */
  private ImmutableList<SpeedDialUiItem> speedDialUiItemsWithUpdatedIds(
      List<SpeedDialUiItem> speedDialUiItems,
      ImmutableMap<SpeedDialEntry, Long> insertedEntriesToIdsMap) {
    if (insertedEntriesToIdsMap.isEmpty()) {
      // There were no newly inserted entries, so all entries ids are set already.
      return ImmutableList.copyOf(speedDialUiItems);
    }

    ImmutableList.Builder<SpeedDialUiItem> updatedItems = ImmutableList.builder();
    for (SpeedDialUiItem speedDialUiItem : speedDialUiItems) {
      SpeedDialEntry entry = speedDialUiItem.buildSpeedDialEntry();
      if (insertedEntriesToIdsMap.containsKey(entry)) {
        // Get the id for newly inserted entry, update our SpeedDialUiItem and add it to our list
        Long id = Assert.isNotNull(insertedEntriesToIdsMap.get(entry));
        updatedItems.add(speedDialUiItem.toBuilder().setSpeedDialEntryId(id).build());
        continue;
      }

      // Starred contacts that aren't in the map, should already have speed dial entry ids.
      // Non-starred contacts aren't in the speed dial entry database, so they
      // shouldn't have speed dial entry ids.
      Assert.checkArgument(
          speedDialUiItem.isStarred() == (speedDialUiItem.speedDialEntryId() != null),
          "Contact must be starred with a speed dial entry id, or not starred with no id "
              + "(suggested contacts)");
      updatedItems.add(speedDialUiItem);
    }
    return updatedItems.build();
  }

  /**
   * Returns the same list of SpeedDialEntries that are passed in except their contact ids and
   * lookup keys are updated to current values.
   *
   * <p>Unfortunately, we need to look up each contact individually to update the contact id and
   * lookup key. Luckily though, this query is highly optimized on the framework side and very
   * quick.
   */
  @WorkerThread
  private List<SpeedDialEntry> updateContactIdsAndLookupKeys(List<SpeedDialEntry> entries) {
    Assert.isWorkerThread();
    List<SpeedDialEntry> updatedEntries = new ArrayList<>();
    for (SpeedDialEntry entry : entries) {
      try (Cursor cursor =
          appContext
              .getContentResolver()
              .query(
                  Contacts.getLookupUri(entry.contactId(), entry.lookupKey()),
                  new String[] {Contacts._ID, Contacts.LOOKUP_KEY},
                  null,
                  null,
                  null)) {
        if (cursor == null) {
          LogUtil.e("SpeedDialUiItemMutator.updateContactIdsAndLookupKeys", "null cursor");
          return new ArrayList<>();
        }
        if (cursor.getCount() == 0) {
          // No need to update this entry, the contact was deleted. We'll clear it up later.
          updatedEntries.add(entry);
          continue;
        }
        // Since all cursor rows will be have the same contact id and lookup key, just grab the
        // first one.
        cursor.moveToFirst();
        updatedEntries.add(
            entry
                .toBuilder()
                .setContactId(cursor.getLong(0))
                .setLookupKey(cursor.getString(1))
                .build());
      }
    }
    return updatedEntries;
  }

  /**
   * Returns a map of SpeedDialEntries to their corresponding SpeedDialUiItems. Mappings to null
   * elements imply that the contact was deleted.
   */
  @WorkerThread
  private Map<SpeedDialEntry, SpeedDialUiItem> getSpeedDialUiItemsFromEntries(
      List<SpeedDialEntry> entries) {
    Trace.beginSection("getSpeedDialUiItemsFromEntries");
    Assert.isWorkerThread();
    // Fetch the contact ids from the SpeedDialEntries
    Set<String> contactIds = new ArraySet<>();
    entries.forEach(entry -> contactIds.add(Long.toString(entry.contactId())));
    if (contactIds.isEmpty()) {
      Trace.endSection();
      return new ArrayMap<>();
    }

    // Build SpeedDialUiItems from those contact ids and map them to their entries
    Selection selection =
        Selection.builder().and(Selection.column(Phone.CONTACT_ID).in(contactIds)).build();
    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                Phone.CONTENT_URI,
                SpeedDialUiItem.getPhoneProjection(isPrimaryDisplayNameOrder()),
                selection.getSelection(),
                selection.getSelectionArgs(),
                null)) {
      Map<SpeedDialEntry, SpeedDialUiItem> map = new ArrayMap<>();
      for (cursor.moveToFirst(); !cursor.isAfterLast(); /* Iterate in the loop */ ) {
        SpeedDialUiItem item =
            SpeedDialUiItem.fromCursor(
                appContext.getResources(), cursor, CallUtil.isVideoEnabled(appContext));
        for (SpeedDialEntry entry : entries) {
          if (entry.contactId() == item.contactId()) {
            // Update the id and pinned position to match it's corresponding SpeedDialEntry.
            SpeedDialUiItem.Builder entrySpeedDialItem =
                item.toBuilder()
                    .setSpeedDialEntryId(entry.id())
                    .setPinnedPosition(entry.pinnedPosition());

            // Preserve the default channel if it didn't change/still exists
            Channel defaultChannel = entry.defaultChannel();
            if (defaultChannel != null) {
              if (item.channels().contains(defaultChannel)
                  || isValidDuoDefaultChannel(item.channels(), defaultChannel)) {
                entrySpeedDialItem.setDefaultChannel(defaultChannel);
              }
            }

            // It's impossible for two contacts to exist with the same contact id, so if this entry
            // was previously matched to a SpeedDialUiItem and is being matched again, something
            // went horribly wrong.
            Assert.checkArgument(
                map.put(entry, entrySpeedDialItem.build()) == null,
                "Each SpeedDialEntry only has one correct SpeedDialUiItem");
          }
        }
      }

      // Contact must have been deleted
      for (SpeedDialEntry entry : entries) {
        map.putIfAbsent(entry, null);
      }
      Trace.endSection();
      return map;
    }
  }

  /**
   * Since we can't check duo reachabliity on background threads, we have to assume the contact is
   * still duo reachable. So we just check it is and return true if the Duo number is still
   * associated with the contact.
   */
  private static boolean isValidDuoDefaultChannel(
      ImmutableList<Channel> channels, Channel defaultChannel) {
    if (defaultChannel.technology() != Channel.DUO) {
      return false;
    }

    for (Channel channel : channels) {
      if (channel.number().equals(defaultChannel.number())) {
        return true;
      }
    }
    return false;
  }

  @WorkerThread
  private List<SpeedDialUiItem> getStarredContacts() {
    Trace.beginSection("getStrequentContacts");
    Assert.isWorkerThread();
    Set<String> contactIds = new ArraySet<>();

    // Fetch the contact ids of all starred contacts
    Uri strequentUri =
        Contacts.CONTENT_STREQUENT_URI
            .buildUpon()
            .appendQueryParameter(ContactsContract.STREQUENT_PHONE_ONLY, "true")
            .build();
    Selection selection = Selection.column(Phone.STARRED).is("=", 1);
    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                strequentUri,
                new String[] {Phone.CONTACT_ID},
                selection.getSelection(),
                selection.getSelectionArgs(),
                null)) {
      if (cursor == null) {
        LogUtil.e("SpeedDialUiItemMutator.getStarredContacts", "null cursor");
        Trace.endSection();
        return new ArrayList<>();
      }
      if (cursor.getCount() == 0) {
        Trace.endSection();
        return new ArrayList<>();
      }
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        contactIds.add(Long.toString(cursor.getLong(0)));
      }
    }

    // Build SpeedDialUiItems from those contact ids
    selection = Selection.builder().and(Selection.column(Phone.CONTACT_ID).in(contactIds)).build();
    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                Phone.CONTENT_URI,
                SpeedDialUiItem.getPhoneProjection(isPrimaryDisplayNameOrder()),
                selection.getSelection(),
                selection.getSelectionArgs(),
                null)) {
      List<SpeedDialUiItem> contacts = new ArrayList<>();
      if (cursor == null) {
        LogUtil.e("SpeedDialUiItemMutator.getStrequentContacts", "null cursor");
        Trace.endSection();
        return new ArrayList<>();
      }
      if (cursor.getCount() == 0) {
        Trace.endSection();
        return contacts;
      }
      for (cursor.moveToFirst(); !cursor.isAfterLast(); /* Iterate in the loop */ ) {
        contacts.add(
            SpeedDialUiItem.fromCursor(
                appContext.getResources(), cursor, CallUtil.isVideoEnabled(appContext)));
      }
      Trace.endSection();
      return contacts;
    }
  }

  /**
   * Persists the position of the {@link SpeedDialUiItem items} as the pinned position according to
   * the order they were passed in.
   */
  @WorkerThread
  public void updatePinnedPosition(List<SpeedDialUiItem> speedDialUiItems) {
    Assert.isWorkerThread();
    if (speedDialUiItems == null || speedDialUiItems.isEmpty()) {
      return;
    }

    // Update the positions in the SpeedDialEntry database
    ImmutableList.Builder<SpeedDialEntry> entriesToUpdate = ImmutableList.builder();
    for (int i = 0; i < speedDialUiItems.size(); i++) {
      SpeedDialUiItem item = speedDialUiItems.get(i);
      if (item.isStarred()) {
        entriesToUpdate.add(
            item.buildSpeedDialEntry().toBuilder().setPinnedPosition(Optional.of(i)).build());
      }
    }
    getSpeedDialEntryDao().update(entriesToUpdate.build());

    // Update the positions in CP2
    // Build a list of SpeedDialUiItems where each contact is only represented once but the order
    // is maintained. For example, assume you have a list of contacts with contact ids:
    //   > { 1, 1, 2, 1, 2, 3 }
    // This list will be reduced to:
    //   > { 1, 2, 3 }
    // and their positions in the resulting list will be written to the CP2 Contacts.PINNED column.
    List<SpeedDialUiItem> cp2SpeedDialUiItems = new ArrayList<>();
    Set<Long> contactIds = new ArraySet<>();
    for (SpeedDialUiItem item : speedDialUiItems) {
      if (contactIds.add(item.contactId())) {
        cp2SpeedDialUiItems.add(item);
      }
    }

    // Code copied from PhoneFavoritesTileAdapter#handleDrop
    ArrayList<ContentProviderOperation> operations = new ArrayList<>();
    for (int i = 0; i < cp2SpeedDialUiItems.size(); i++) {
      SpeedDialUiItem item = cp2SpeedDialUiItems.get(i);
      // Pinned positions in the database start from 1 instead of being zero-indexed like
      // arrays, so offset by 1.
      int databasePinnedPosition = i + 1;
      if (item.pinnedPosition().isPresent()
          && item.pinnedPosition().get() == databasePinnedPosition) {
        continue;
      }

      Uri uri = Uri.withAppendedPath(Contacts.CONTENT_URI, String.valueOf(item.contactId()));
      ContentValues values = new ContentValues();
      values.put(Contacts.PINNED, databasePinnedPosition);
      operations.add(ContentProviderOperation.newUpdate(uri).withValues(values).build());
    }
    if (operations.isEmpty()) {
      // Nothing to update
      return;
    }
    try {
      appContext.getContentResolver().applyBatch(ContactsContract.AUTHORITY, operations);
      // TODO(calderwoodra): log
    } catch (RemoteException | OperationApplicationException e) {
      LogUtil.e(
          "SpeedDialUiItemMutator.updatePinnedPosition",
          "Exception thrown when pinning contacts",
          e);
    }
  }

  /**
   * Returns a new list with duo reachable channels inserted. Duo channels won't replace ViLTE
   * channels.
   */
  @MainThread
  public ImmutableList<SpeedDialUiItem> insertDuoChannels(
      Context context, ImmutableList<SpeedDialUiItem> speedDialUiItems) {
    Assert.isMainThread();

    ImmutableList.Builder<SpeedDialUiItem> newSpeedDialItemList = ImmutableList.builder();
    // for each existing item
    for (SpeedDialUiItem item : speedDialUiItems) {
      if (item.defaultChannel() == null) {
        // If the contact is starred and doesn't have a default channel, insert duo channels
        newSpeedDialItemList.add(insertDuoChannelsToStarredContact(context, item));
      } else {
        // if starred and has a default channel, leave it as is, the user knows what they want.
        newSpeedDialItemList.add(item);
      }
    }
    return newSpeedDialItemList.build();
  }

  @MainThread
  private SpeedDialUiItem insertDuoChannelsToStarredContact(Context context, SpeedDialUiItem item) {
    Assert.isMainThread();
    Assert.checkArgument(item.isStarred());

    // build a new list of channels
    ImmutableList.Builder<Channel> newChannelsList = ImmutableList.builder();
    Channel previousChannel = item.channels().get(0);
    newChannelsList.add(previousChannel);

    for (int i = 1; i < item.channels().size(); i++) {
      Channel currentChannel = item.channels().get(i);
      // If the previous and current channel are voice channels, that means the previous number
      // didn't have a video channel.
      // If the previous number is duo reachable, insert a duo channel.
      if (!previousChannel.isVideoTechnology()
          && !currentChannel.isVideoTechnology()
          && DuoComponent.get(context).getDuo().isReachable(context, previousChannel.number())) {
        newChannelsList.add(previousChannel.toBuilder().setTechnology(Channel.DUO).build());
      }
      newChannelsList.add(currentChannel);
      previousChannel = currentChannel;
    }

    // Check the last channel
    if (!previousChannel.isVideoTechnology()
        && DuoComponent.get(context).getDuo().isReachable(context, previousChannel.number())) {
      newChannelsList.add(previousChannel.toBuilder().setTechnology(Channel.DUO).build());
    }
    return item.toBuilder().setChannels(newChannelsList.build()).build();
  }

  private SpeedDialEntryDao getSpeedDialEntryDao() {
    return new SpeedDialEntryDatabaseHelper(appContext);
  }

  private boolean isPrimaryDisplayNameOrder() {
    return contactDisplayPreferences.getDisplayOrder() == DisplayOrder.PRIMARY;
  }
}
