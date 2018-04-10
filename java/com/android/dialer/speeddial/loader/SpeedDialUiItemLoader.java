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
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.support.annotation.WorkerThread;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.common.concurrent.DialerExecutor.SuccessListener;
import com.android.dialer.common.concurrent.DialerFutureSerializer;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.speeddial.database.SpeedDialEntry;
import com.android.dialer.speeddial.database.SpeedDialEntry.Channel;
import com.android.dialer.speeddial.database.SpeedDialEntryDao;
import com.android.dialer.speeddial.database.SpeedDialEntryDatabaseHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.ArrayList;
import java.util.List;
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
public final class SpeedDialUiItemLoader implements UiItemLoader {

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
  @Override
  public ListenableFuture<ImmutableList<SpeedDialUiItem>> loadSpeedDialUiItems() {
    return dialerFutureSerializer.submitAsync(
        () -> backgroundExecutor.submit(this::doInBackground), backgroundExecutor);
  }

  @WorkerThread
  private ImmutableList<SpeedDialUiItem> doInBackground() {
    Assert.isWorkerThread();
    SpeedDialEntryDao db = new SpeedDialEntryDatabaseHelper(appContext);

    // This is the list of contacts that we will display to the user
    List<SpeedDialUiItem> speedDialUiItems = new ArrayList<>();

    // We'll use these lists to update the SpeedDialEntry database
    List<SpeedDialEntry> entriesToInsert = new ArrayList<>();
    List<SpeedDialEntry> entriesToUpdate = new ArrayList<>();
    List<Long> entriesToDelete = new ArrayList<>();

    // Get all SpeedDialEntries and mark them to be updated or deleted
    List<SpeedDialEntry> entries = db.getAllEntries();
    for (SpeedDialEntry entry : entries) {
      SpeedDialUiItem contact = getSpeedDialContact(entry);
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
        speedDialUiItems.add(contact);

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

  @WorkerThread
  private SpeedDialUiItem getSpeedDialContact(SpeedDialEntry entry) {
    Assert.isWorkerThread();
    // TODO(b77725860): Might need to use the lookup uri to get the contact id first, then query
    // based on that.
    SpeedDialUiItem contact;
    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                Phone.CONTENT_URI,
                SpeedDialUiItem.PHONE_PROJECTION,
                Phone.NUMBER + " IS NOT NULL AND " + Phone.LOOKUP_KEY + "=?",
                new String[] {entry.lookupKey()},
                null)) {

      if (cursor == null || cursor.getCount() == 0) {
        // Contact not found, potentially deleted
        LogUtil.e("SpeedDialUiItemLoader.getSpeedDialContact", "Contact not found.");
        return null;
      }

      cursor.moveToFirst();
      contact = SpeedDialUiItem.fromCursor(cursor);
    }

    // Preserve the default channel if it didn't change/still exists
    Channel defaultChannel = entry.defaultChannel();
    if (defaultChannel != null) {
      if (contact.channels().contains(defaultChannel)) {
        contact = contact.toBuilder().setDefaultChannel(defaultChannel).build();
      }
    }

    // TODO(calderwoodra): Consider setting the default channel if there is only one channel
    return contact;
  }

  @WorkerThread
  private List<SpeedDialUiItem> getStrequentContacts() {
    Assert.isWorkerThread();
    Uri uri =
        Contacts.CONTENT_STREQUENT_URI
            .buildUpon()
            .appendQueryParameter(ContactsContract.STREQUENT_PHONE_ONLY, "true")
            .build();
    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(uri, SpeedDialUiItem.PHONE_PROJECTION, null, null, null)) {
      List<SpeedDialUiItem> contacts = new ArrayList<>();
      if (cursor == null || cursor.getCount() == 0) {
        return contacts;
      }

      cursor.moveToPosition(-1);
      while (cursor.moveToNext()) {
        contacts.add(SpeedDialUiItem.fromCursor(cursor));
      }
      return contacts;
    }
  }
}
