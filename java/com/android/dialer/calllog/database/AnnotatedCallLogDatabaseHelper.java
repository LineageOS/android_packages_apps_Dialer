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

package com.android.dialer.calllog.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.common.LogUtil;
import java.util.Locale;

/** {@link SQLiteOpenHelper} for the AnnotatedCallLog database. */
class AnnotatedCallLogDatabaseHelper extends SQLiteOpenHelper {
  private final int maxRows;

  AnnotatedCallLogDatabaseHelper(Context appContext, int maxRows) {
    super(appContext, "annotated_call_log.db", null, 1);
    this.maxRows = maxRows;
  }

  private static final String CREATE_TABLE_SQL =
      new StringBuilder()
          .append("create table if not exists " + AnnotatedCallLog.TABLE + " (")
          .append(AnnotatedCallLog._ID + " integer primary key, ")
          .append(AnnotatedCallLog.TIMESTAMP + " integer, ")
          .append(AnnotatedCallLog.CONTACT_NAME + " string, ")
          .append(AnnotatedCallLog.NUMBER + " blob")
          .append(");")
          .toString();

  /** Deletes all but the first maxRows rows (by timestamp) to keep the table a manageable size. */
  private static final String CREATE_TRIGGER_SQL =
      "create trigger delete_old_rows after insert on "
          + AnnotatedCallLog.TABLE
          + " when (select count(*) from "
          + AnnotatedCallLog.TABLE
          + ") > %d"
          + " begin delete from "
          + AnnotatedCallLog.TABLE
          + " where "
          + AnnotatedCallLog._ID
          + " in (select "
          + AnnotatedCallLog._ID
          + " from "
          + AnnotatedCallLog.TABLE
          + " order by timestamp limit (select count(*)-%d"
          + " from "
          + AnnotatedCallLog.TABLE
          + " )); end;";

  @Override
  public void onCreate(SQLiteDatabase db) {
    LogUtil.enterBlock("AnnotatedCallLogDatabaseHelper.onCreate");
    long startTime = System.currentTimeMillis();
    db.execSQL(CREATE_TABLE_SQL);
    db.execSQL(String.format(Locale.US, CREATE_TRIGGER_SQL, maxRows, maxRows));
    // TODO: Consider logging impression.
    LogUtil.i(
        "AnnotatedCallLogDatabaseHelper.onCreate",
        "took: %dms",
        System.currentTimeMillis() - startTime);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
}
