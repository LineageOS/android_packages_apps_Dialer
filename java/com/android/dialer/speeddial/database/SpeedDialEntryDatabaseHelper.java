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

package com.android.dialer.speeddial.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.database.Selection;
import com.android.dialer.speeddial.database.SpeedDialEntry.Channel;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link SpeedDialEntryDao} implemented as an SQLite database.
 *
 * @see SpeedDialEntryDao
 */
public final class SpeedDialEntryDatabaseHelper extends SQLiteOpenHelper
    implements SpeedDialEntryDao {

  /**
   * If the pinned position is absent, then we need to write an impossible value in the table like
   * -1 so that it doesn't default to 0. When we read this value from the table, we'll translate it
   * to Optional.absent() in the resulting {@link SpeedDialEntry}.
   */
  private static final int PINNED_POSITION_ABSENT = -1;

  private static final int DATABASE_VERSION = 2;
  private static final String DATABASE_NAME = "CPSpeedDialEntry";

  // Column names
  private static final String TABLE_NAME = "speed_dial_entries";
  private static final String ID = "id";
  private static final String PINNED_POSITION = "pinned_position";
  private static final String CONTACT_ID = "contact_id";
  private static final String LOOKUP_KEY = "lookup_key";
  private static final String PHONE_NUMBER = "phone_number";
  private static final String PHONE_TYPE = "phone_type";
  private static final String PHONE_LABEL = "phone_label";
  private static final String PHONE_TECHNOLOGY = "phone_technology";

  // Column positions
  private static final int POSITION_ID = 0;
  private static final int POSITION_PINNED_POSITION = 1;
  private static final int POSITION_CONTACT_ID = 2;
  private static final int POSITION_LOOKUP_KEY = 3;
  private static final int POSITION_PHONE_NUMBER = 4;
  private static final int POSITION_PHONE_TYPE = 5;
  private static final int POSITION_PHONE_LABEL = 6;
  private static final int POSITION_PHONE_TECHNOLOGY = 7;

  // Create Table Query
  private static final String CREATE_TABLE_SQL =
      "create table if not exists "
          + TABLE_NAME
          + " ("
          + (ID + " integer primary key, ")
          + (PINNED_POSITION + " integer, ")
          + (CONTACT_ID + " integer, ")
          + (LOOKUP_KEY + " text, ")
          + (PHONE_NUMBER + " text, ")
          + (PHONE_TYPE + " integer, ")
          + (PHONE_LABEL + " text, ")
          + (PHONE_TECHNOLOGY + " integer ")
          + ");";

  private static final String DELETE_TABLE_SQL = "drop table if exists " + TABLE_NAME;

  public SpeedDialEntryDatabaseHelper(Context context) {
    super(context, DATABASE_NAME, null, DATABASE_VERSION);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(CREATE_TABLE_SQL);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    // TODO(calderwoodra): handle upgrades more elegantly
    db.execSQL(DELETE_TABLE_SQL);
    this.onCreate(db);
  }

  @Override
  public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    // TODO(calderwoodra): handle upgrades more elegantly
    this.onUpgrade(db, oldVersion, newVersion);
  }

  @Override
  public ImmutableList<SpeedDialEntry> getAllEntries() {
    List<SpeedDialEntry> entries = new ArrayList<>();

    String query = "SELECT * FROM " + TABLE_NAME;
    try (SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(query, null)) {
      cursor.moveToPosition(-1);
      while (cursor.moveToNext()) {
        String number = cursor.getString(POSITION_PHONE_NUMBER);
        Channel channel = null;
        if (!TextUtils.isEmpty(number)) {
          channel =
              Channel.builder()
                  .setNumber(number)
                  .setPhoneType(cursor.getInt(POSITION_PHONE_TYPE))
                  .setLabel(Optional.of(cursor.getString(POSITION_PHONE_LABEL)).or(""))
                  .setTechnology(cursor.getInt(POSITION_PHONE_TECHNOLOGY))
                  .build();
        }

        Optional<Integer> pinnedPosition = Optional.of(cursor.getInt(POSITION_PINNED_POSITION));
        if (pinnedPosition.or(PINNED_POSITION_ABSENT) == PINNED_POSITION_ABSENT) {
          pinnedPosition = Optional.absent();
        }

        SpeedDialEntry entry =
            SpeedDialEntry.builder()
                .setDefaultChannel(channel)
                .setContactId(cursor.getLong(POSITION_CONTACT_ID))
                .setLookupKey(cursor.getString(POSITION_LOOKUP_KEY))
                .setPinnedPosition(pinnedPosition)
                .setId(cursor.getLong(POSITION_ID))
                .build();
        entries.add(entry);
      }
    }
    return ImmutableList.copyOf(entries);
  }

  @Override
  public ImmutableMap<SpeedDialEntry, Long> insert(ImmutableList<SpeedDialEntry> entries) {
    if (entries.isEmpty()) {
      return ImmutableMap.of();
    }

    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      ImmutableMap<SpeedDialEntry, Long> insertedEntriesToIdsMap = insert(db, entries);
      db.setTransactionSuccessful();
      return insertedEntriesToIdsMap;
    } finally {
      db.endTransaction();
      db.close();
    }
  }

  private ImmutableMap<SpeedDialEntry, Long> insert(
      SQLiteDatabase writeableDatabase, ImmutableList<SpeedDialEntry> entries) {
    ImmutableMap.Builder<SpeedDialEntry, Long> insertedEntriesToIdsMap = ImmutableMap.builder();
    for (SpeedDialEntry entry : entries) {
      Assert.checkArgument(entry.id() == null);
      long id = writeableDatabase.insert(TABLE_NAME, null, buildContentValuesWithoutId(entry));
      if (id == -1L) {
        throw Assert.createUnsupportedOperationFailException(
            "Attempted to insert a row that already exists.");
      }
      // It's impossible to insert two identical entries but this is an important assumption we need
      // to verify because there's an assumption that each entry will correspond to exactly one id.
      // ImmutableMap#put verifies this check for us.
      insertedEntriesToIdsMap.put(entry, id);
    }
    return insertedEntriesToIdsMap.build();
  }

  @Override
  public long insert(SpeedDialEntry entry) {
    long updateRowId;
    try (SQLiteDatabase db = getWritableDatabase()) {
      updateRowId = db.insert(TABLE_NAME, null, buildContentValuesWithoutId(entry));
    }
    if (updateRowId == -1) {
      throw Assert.createUnsupportedOperationFailException(
          "Attempted to insert a row that already exists.");
    }
    return updateRowId;
  }

  @Override
  public void update(ImmutableList<SpeedDialEntry> entries) {
    if (entries.isEmpty()) {
      return;
    }

    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      update(db, entries);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
      db.close();
    }
  }

  private void update(SQLiteDatabase writeableDatabase, ImmutableList<SpeedDialEntry> entries) {
    for (SpeedDialEntry entry : entries) {
      int count =
          writeableDatabase.update(
              TABLE_NAME,
              buildContentValuesWithId(entry),
              ID + " = ?",
              new String[] {Long.toString(entry.id())});
      if (count != 1) {
        throw Assert.createUnsupportedOperationFailException(
            "Attempted to update an undetermined number of rows: " + count);
      }
    }
  }

  private ContentValues buildContentValuesWithId(SpeedDialEntry entry) {
    return buildContentValues(entry, true);
  }

  private ContentValues buildContentValuesWithoutId(SpeedDialEntry entry) {
    return buildContentValues(entry, false);
  }

  private ContentValues buildContentValues(SpeedDialEntry entry, boolean includeId) {
    ContentValues values = new ContentValues();
    if (includeId) {
      values.put(ID, entry.id());
    }
    values.put(PINNED_POSITION, entry.pinnedPosition().or(PINNED_POSITION_ABSENT));
    values.put(CONTACT_ID, entry.contactId());
    values.put(LOOKUP_KEY, entry.lookupKey());
    if (entry.defaultChannel() != null) {
      values.put(PHONE_NUMBER, entry.defaultChannel().number());
      values.put(PHONE_TYPE, entry.defaultChannel().phoneType());
      values.put(PHONE_LABEL, entry.defaultChannel().label());
      values.put(PHONE_TECHNOLOGY, entry.defaultChannel().technology());
    }
    return values;
  }

  @Override
  public void delete(ImmutableList<Long> ids) {
    if (ids.isEmpty()) {
      return;
    }

    try (SQLiteDatabase db = getWritableDatabase()) {
      delete(db, ids);
    }
  }

  private void delete(SQLiteDatabase writeableDatabase, ImmutableList<Long> ids) {
    List<String> idStrings = new ArrayList<>();
    for (Long id : ids) {
      idStrings.add(Long.toString(id));
    }

    Selection selection = Selection.builder().and(Selection.column(ID).in(idStrings)).build();
    int count =
        writeableDatabase.delete(
            TABLE_NAME, selection.getSelection(), selection.getSelectionArgs());
    if (count != ids.size()) {
      throw Assert.createUnsupportedOperationFailException(
          "Attempted to delete an undetermined number of rows: " + count);
    }
  }

  @Override
  public ImmutableMap<SpeedDialEntry, Long> insertUpdateAndDelete(
      ImmutableList<SpeedDialEntry> entriesToInsert,
      ImmutableList<SpeedDialEntry> entriesToUpdate,
      ImmutableList<Long> entriesToDelete) {
    if (entriesToInsert.isEmpty() && entriesToUpdate.isEmpty() && entriesToDelete.isEmpty()) {
      return ImmutableMap.of();
    }
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      ImmutableMap<SpeedDialEntry, Long> insertedEntriesToIdsMap = insert(db, entriesToInsert);
      update(db, entriesToUpdate);
      delete(db, entriesToDelete);
      db.setTransactionSuccessful();
      return insertedEntriesToIdsMap;
    } finally {
      db.endTransaction();
      db.close();
    }
  }

  @Override
  public void deleteAll() {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      // Passing null into where clause will delete all rows
      db.delete(TABLE_NAME, /* whereClause=*/ null, null);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
      db.close();
    }
  }
}
