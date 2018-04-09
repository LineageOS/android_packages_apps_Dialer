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
import java.util.ArrayList;
import java.util.List;

/**
 * {@link SpeedDialEntryDao} implemented as an SQLite database.
 *
 * @see SpeedDialEntryDao
 */
public final class SpeedDialEntryDatabaseHelper extends SQLiteOpenHelper
    implements SpeedDialEntryDao {

  private static final int DATABASE_VERSION = 1;
  private static final String DATABASE_NAME = "CPSpeedDialEntry";

  // Column names
  private static final String TABLE_NAME = "speed_dial_entries";
  private static final String ID = "id";
  private static final String CONTACT_ID = "contact_id";
  private static final String LOOKUP_KEY = "lookup_key";
  private static final String PHONE_NUMBER = "phone_number";
  private static final String PHONE_LABEL = "phone_label";
  private static final String PHONE_TECHNOLOGY = "phone_technology";

  // Column positions
  private static final int POSITION_ID = 0;
  private static final int POSITION_CONTACT_ID = 1;
  private static final int POSITION_LOOKUP_KEY = 2;
  private static final int POSITION_PHONE_NUMBER = 3;
  private static final int POSITION_PHONE_LABEL = 4;
  private static final int POSITION_PHONE_TECHNOLOGY = 5;

  // Create Table Query
  private static final String CREATE_TABLE_SQL =
      "create table if not exists "
          + TABLE_NAME
          + " ("
          + (ID + " integer primary key, ")
          + (CONTACT_ID + " integer, ")
          + (LOOKUP_KEY + " text, ")
          + (PHONE_NUMBER + " text, ")
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
  public List<SpeedDialEntry> getAllEntries() {
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
                  .setLabel(cursor.getString(POSITION_PHONE_LABEL))
                  .setTechnology(cursor.getInt(POSITION_PHONE_TECHNOLOGY))
                  .build();
        }

        SpeedDialEntry entry =
            SpeedDialEntry.builder()
                .setDefaultChannel(channel)
                .setContactId(cursor.getLong(POSITION_CONTACT_ID))
                .setLookupKey(cursor.getString(POSITION_LOOKUP_KEY))
                .setId(cursor.getInt(POSITION_ID))
                .build();
        entries.add(entry);
      }
    }
    return entries;
  }

  @Override
  public void insert(List<SpeedDialEntry> entries) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      for (SpeedDialEntry entry : entries) {
        if (db.insert(TABLE_NAME, null, buildContentValues(entry)) == -1L) {
          throw Assert.createUnsupportedOperationFailException(
              "Attempted to insert a row that already exists.");
        }
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
      db.close();
    }
  }

  @Override
  public long insert(SpeedDialEntry entry) {
    long updateRowId;
    try (SQLiteDatabase db = getWritableDatabase()) {
      updateRowId = db.insert(TABLE_NAME, null, buildContentValues(entry));
    }
    if (updateRowId == -1) {
      throw Assert.createUnsupportedOperationFailException(
          "Attempted to insert a row that already exists.");
    }
    return updateRowId;
  }

  @Override
  public void update(List<SpeedDialEntry> entries) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      for (SpeedDialEntry entry : entries) {
        int count =
            db.update(
                TABLE_NAME,
                buildContentValues(entry),
                ID + " = ?",
                new String[] {Long.toString(entry.id())});
        if (count != 1) {
          throw Assert.createUnsupportedOperationFailException(
              "Attempted to update an undetermined number of rows: " + count);
        }
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
      db.close();
    }
  }

  private ContentValues buildContentValues(SpeedDialEntry entry) {
    ContentValues values = new ContentValues();
    values.put(ID, entry.id());
    values.put(CONTACT_ID, entry.contactId());
    values.put(LOOKUP_KEY, entry.lookupKey());
    if (entry.defaultChannel() != null) {
      values.put(PHONE_NUMBER, entry.defaultChannel().number());
      values.put(PHONE_LABEL, entry.defaultChannel().label());
      values.put(PHONE_TECHNOLOGY, entry.defaultChannel().technology());
    }
    return values;
  }

  @Override
  public void delete(List<Long> ids) {
    List<String> idStrings = new ArrayList<>();
    for (Long id : ids) {
      idStrings.add(Long.toString(id));
    }

    Selection selection = Selection.builder().and(Selection.column(ID).in(idStrings)).build();
    try (SQLiteDatabase db = getWritableDatabase()) {
      int count = db.delete(TABLE_NAME, selection.getSelection(), selection.getSelectionArgs());
      if (count != ids.size()) {
        throw Assert.createUnsupportedOperationFailException(
            "Attempted to delete an undetermined number of rows: " + count);
      }
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
