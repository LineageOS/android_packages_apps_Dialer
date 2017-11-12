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

package com.android.dialer.phonelookup.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.SystemClock;
import com.android.dialer.common.LogUtil;
import com.android.dialer.phonelookup.database.contract.PhoneLookupHistoryContract.PhoneLookupHistory;
import java.util.Locale;

/** {@link SQLiteOpenHelper} for the PhoneLookupHistory database. */
class PhoneLookupHistoryDatabaseHelper extends SQLiteOpenHelper {
  private final int maxRows;

  PhoneLookupHistoryDatabaseHelper(Context appContext, int maxRows) {
    super(appContext, "phone_lookup_history.db", null, 1);
    this.maxRows = maxRows;
  }

  private static final String CREATE_TABLE_SQL =
      "create table if not exists "
          + PhoneLookupHistory.TABLE
          + " ("
          + (PhoneLookupHistory.NORMALIZED_NUMBER + " text primary key not null, ")
          + (PhoneLookupHistory.PHONE_LOOKUP_INFO + " blob not null, ")
          + (PhoneLookupHistory.LAST_MODIFIED + " long not null")
          + ");";

  /** Deletes all but the first maxRows rows (by timestamp) to keep the table a manageable size. */
  private static final String CREATE_TRIGGER_SQL =
      "create trigger delete_old_rows after insert on "
          + PhoneLookupHistory.TABLE
          + " when (select count(*) from "
          + PhoneLookupHistory.TABLE
          + ") > %d"
          + " begin delete from "
          + PhoneLookupHistory.TABLE
          + " where "
          + PhoneLookupHistory.NORMALIZED_NUMBER
          + " in (select "
          + PhoneLookupHistory.NORMALIZED_NUMBER
          + " from "
          + PhoneLookupHistory.TABLE
          + " order by "
          + PhoneLookupHistory.LAST_MODIFIED
          + " limit (select count(*)-%d"
          + " from "
          + PhoneLookupHistory.TABLE
          + " )); end;";

  private static final String CREATE_INDEX_ON_LAST_MODIFIED_SQL =
      "create index last_modified_index on "
          + PhoneLookupHistory.TABLE
          + " ("
          + PhoneLookupHistory.LAST_MODIFIED
          + ");";

  @Override
  public void onCreate(SQLiteDatabase db) {
    LogUtil.enterBlock("PhoneLookupHistoryDatabaseHelper.onCreate");
    long startTime = SystemClock.uptimeMillis();
    db.execSQL(CREATE_TABLE_SQL);
    db.execSQL(String.format(Locale.US, CREATE_TRIGGER_SQL, maxRows, maxRows));
    db.execSQL(CREATE_INDEX_ON_LAST_MODIFIED_SQL);
    // TODO(zachh): Consider logging impression.
    LogUtil.i(
        "PhoneLookupHistoryDatabaseHelper.onCreate",
        "took: %dms",
        SystemClock.uptimeMillis() - startTime);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
}
