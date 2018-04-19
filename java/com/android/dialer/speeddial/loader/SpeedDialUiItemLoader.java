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

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
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
import com.android.dialer.common.concurrent.DialerExecutor.SuccessListener;
import com.android.dialer.common.concurrent.DialerFutureSerializer;
import com.android.dialer.common.database.Selection;
import com.android.dialer.duo.Duo;
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.speeddial.database.SpeedDialEntry;
import com.android.dialer.speeddial.database.SpeedDialEntry.Channel;
import com.android.dialer.speeddial.database.SpeedDialEntryDao;
import com.android.dialer.speeddial.database.SpeedDialEntryDatabaseHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 *       <li>Build a list of {@link SpeedDialUiItem} from {@link Contacts#STREQUENT_PHONE_ONLY}.
 *       <li>If any starred contacts in that list aren't in the {@link
 *           SpeedDialEntryDatabaseHelper}, insert them now.
 *       <li>Notify the {@link SuccessListener} of the complete list of {@link SpeedDialUiItem
 *           SpeedDialContacts} composed from {@link SpeedDialEntry SpeedDialEntries} and
 *           non-starred {@link Contacts#STREQUENT_PHONE_ONLY}.
 *     </ol>
 */
@SuppressWarnings("AndroidApiChecker")
@TargetApi(VERSION_CODES.N)
@Singleton
public final class SpeedDialUiItemLoader {

  private static final int MAX_DUO_SUGGESTIONS = 3;

  private final Context appContext;
  private final ListeningExecutorService backgroundExecutor;
  // Used to ensure that only one refresh flow runs at a time.
  private final DialerFutureSerializer dialerFutureSerializer = new DialerFutureSerializer();

  @Inject
  public SpeedDialUiItemLoader(
      @ApplicationContext Context appContext,
      @BackgroundExecutor ListeningExecutorService backgroundExecutor) {
    this.appContext = appContext;
    this.backgroundExecutor = backgroundExecutor;
  }

  /**
   * Returns a {@link ListenableFuture} for a list of {@link SpeedDialUiItem SpeedDialUiItems}. This
   * list is composed of starred contacts from {@link SpeedDialEntryDatabaseHelper} and suggestions
   * from {@link Contacts#STREQUENT_PHONE_ONLY}.
   */
  public ListenableFuture<ImmutableList<SpeedDialUiItem>> loadSpeedDialUiItems() {
    return dialerFutureSerializer.submit(this::loadSpeedDialUiItemsInternal, backgroundExecutor);
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
            .query(contactUri, SpeedDialUiItem.PHONE_PROJECTION, null, null, null)) {
      if (cursor == null) {
        LogUtil.e("SpeedDialUiItemLoader.insertNewContactEntry", "Cursor was null");
        return loadSpeedDialUiItemsInternal();
      }
      Assert.checkArgument(cursor.moveToFirst(), "Cursor should never be empty");
      SpeedDialUiItem item = SpeedDialUiItem.fromCursor(cursor);

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
      getSpeedDialEntryDao()
          .insert(
              SpeedDialEntry.builder()
                  .setLookupKey(item.lookupKey())
                  .setContactId(item.contactId())
                  .setDefaultChannel(item.defaultChannel())
                  .build());
    }
    return loadSpeedDialUiItemsInternal();
  }

  @WorkerThread
  private ImmutableList<SpeedDialUiItem> loadSpeedDialUiItemsInternal() {
    Assert.isWorkerThread();
    SpeedDialEntryDao db = getSpeedDialEntryDao();

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

    // Get all Strequent Contacts
    List<SpeedDialUiItem> strequentContacts = getStrequentContacts();

    // For each contact, if it isn't starred, add it as a suggestion.
    // If it is starred and not already accounted for above, then insert into the SpeedDialEntry DB.
    for (SpeedDialUiItem contact : strequentContacts) {
      if (!contact.isStarred()) {
        // Add this contact as a suggestion
        // TODO(77754534): improve suggestions beyond just first channel
        speedDialUiItems.add(
            contact.toBuilder().setDefaultChannel(contact.channels().get(0)).build());

      } else if (speedDialUiItems.stream().noneMatch(c -> c.contactId() == contact.contactId())) {
        entriesToInsert.add(
            SpeedDialEntry.builder()
                .setLookupKey(contact.lookupKey())
                .setContactId(contact.contactId())
                .setDefaultChannel(contact.defaultChannel())
                .build());

        // These are our newly starred contacts
        speedDialUiItems.add(contact);
      }
    }

    db.insertUpdateAndDelete(
        ImmutableList.copyOf(entriesToInsert),
        ImmutableList.copyOf(entriesToUpdate),
        ImmutableList.copyOf(entriesToDelete));
    return ImmutableList.copyOf(speedDialUiItems);
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
          LogUtil.e("SpeedDialUiItemLoader.updateContactIdsAndLookupKeys", "null cursor");
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
    Assert.isWorkerThread();
    // Fetch the contact ids from the SpeedDialEntries
    Set<String> contactIds = new HashSet<>();
    entries.forEach(entry -> contactIds.add(Long.toString(entry.contactId())));
    if (contactIds.isEmpty()) {
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
                SpeedDialUiItem.PHONE_PROJECTION,
                selection.getSelection(),
                selection.getSelectionArgs(),
                null)) {
      Map<SpeedDialEntry, SpeedDialUiItem> map = new ArrayMap<>();
      for (cursor.moveToFirst(); !cursor.isAfterLast(); /* Iterate in the loop */ ) {
        SpeedDialUiItem item = SpeedDialUiItem.fromCursor(cursor);
        for (SpeedDialEntry entry : entries) {
          if (entry.contactId() == item.contactId()) {
            // Update the id to match it's corresponding SpeedDialEntry.
            SpeedDialUiItem.Builder entrySpeedDialItem =
                item.toBuilder().setSpeedDialEntryId(entry.id());

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
  private List<SpeedDialUiItem> getStrequentContacts() {
    Assert.isWorkerThread();
    Set<String> contactIds = new ArraySet<>();

    // Fetch the contact ids of all strequent contacts
    Uri strequentUri =
        Contacts.CONTENT_STREQUENT_URI
            .buildUpon()
            .appendQueryParameter(ContactsContract.STREQUENT_PHONE_ONLY, "true")
            .build();
    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(strequentUri, new String[] {Phone.CONTACT_ID}, null, null, null)) {
      if (cursor == null) {
        LogUtil.e("SpeedDialUiItemLoader.getStrequentContacts", "null cursor");
        return new ArrayList<>();
      }
      if (cursor.getCount() == 0) {
        return new ArrayList<>();
      }
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        contactIds.add(Long.toString(cursor.getLong(0)));
      }
    }

    // Build SpeedDialUiItems from those contact ids
    Selection selection =
        Selection.builder().and(Selection.column(Phone.CONTACT_ID).in(contactIds)).build();
    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                Phone.CONTENT_URI,
                SpeedDialUiItem.PHONE_PROJECTION,
                selection.getSelection(),
                selection.getSelectionArgs(),
                null)) {
      List<SpeedDialUiItem> contacts = new ArrayList<>();
      if (cursor == null) {
        LogUtil.e("SpeedDialUiItemLoader.getStrequentContacts", "null cursor");
        return new ArrayList<>();
      }
      if (cursor.getCount() == 0) {
        return contacts;
      }
      for (cursor.moveToFirst(); !cursor.isAfterLast(); /* Iterate in the loop */ ) {
        contacts.add(SpeedDialUiItem.fromCursor(cursor));
      }
      return contacts;
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

    Duo duo = DuoComponent.get(context).getDuo();
    int maxDuoSuggestions = MAX_DUO_SUGGESTIONS;

    ImmutableList.Builder<SpeedDialUiItem> newSpeedDialItemList = ImmutableList.builder();
    // for each existing item
    for (SpeedDialUiItem item : speedDialUiItems) {
      // If the item is a suggestion
      if (!item.isStarred()) {
        // And duo reachable, insert a duo suggestion
        if (maxDuoSuggestions > 0 && duo.isReachable(context, item.defaultChannel().number())) {
          maxDuoSuggestions--;
          Channel defaultChannel =
              item.defaultChannel().toBuilder().setTechnology(Channel.DUO).build();
          newSpeedDialItemList.add(item.toBuilder().setDefaultChannel(defaultChannel).build());
        }
        // Insert the voice suggestion too
        newSpeedDialItemList.add(item);
      } else if (item.defaultChannel() == null) {
        // If the contact is starred and doesn't have a default channel, insert duo channels
        newSpeedDialItemList.add(insertDuoChannelsToStarredContact(context, item));
      } // if starred and has a default channel, leave it as is, the user knows what they want.
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
}
