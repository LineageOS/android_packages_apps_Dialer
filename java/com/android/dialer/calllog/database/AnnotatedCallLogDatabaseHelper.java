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
import android.provider.CallLog.Calls;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.inject.ApplicationContext;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;

/** {@link SQLiteOpenHelper} for the AnnotatedCallLog database. */
@Singleton
public class AnnotatedCallLogDatabaseHelper extends SQLiteOpenHelper {

  private static final String FILENAME = "annotated_call_log.db";

  private final Context appContext;
  private final int maxRows;
  private final ListeningExecutorService backgroundExecutor;

  @Inject
  public AnnotatedCallLogDatabaseHelper(
      @ApplicationContext Context appContext,
      @AnnotatedCallLogMaxRows int maxRows,
      @BackgroundExecutor ListeningExecutorService backgroundExecutor) {
    super(appContext, FILENAME, null, 2);

    this.appContext = appContext;
    this.maxRows = maxRows;
    this.backgroundExecutor = backgroundExecutor;
  }

  private static final String CREATE_TABLE_SQL =
      "create table if not exists "
          + AnnotatedCallLog.TABLE
          + " ("
          + (AnnotatedCallLog._ID + " integer primary key, ")
          + (AnnotatedCallLog.TIMESTAMP + " integer, ")
          + (AnnotatedCallLog.NUMBER + " blob, ")
          + (AnnotatedCallLog.FORMATTED_NUMBER + " text, ")
          + (AnnotatedCallLog.NUMBER_PRESENTATION + " integer, ")
          + (AnnotatedCallLog.DURATION + " integer, ")
          + (AnnotatedCallLog.DATA_USAGE + " integer, ")
          + (AnnotatedCallLog.IS_READ + " integer not null, ")
          + (AnnotatedCallLog.NEW + " integer not null, ")
          + (AnnotatedCallLog.GEOCODED_LOCATION + " text, ")
          + (AnnotatedCallLog.PHONE_ACCOUNT_COMPONENT_NAME + " text, ")
          + (AnnotatedCallLog.PHONE_ACCOUNT_ID + " text, ")
          + (AnnotatedCallLog.FEATURES + " integer, ")
          + (AnnotatedCallLog.TRANSCRIPTION + " integer, ")
          + (AnnotatedCallLog.VOICEMAIL_URI + " text, ")
          + (AnnotatedCallLog.CALL_TYPE + " integer not null, ")
          + (AnnotatedCallLog.NUMBER_ATTRIBUTES + " blob, ")
          + (AnnotatedCallLog.IS_VOICEMAIL_CALL + " integer, ")
          + (AnnotatedCallLog.VOICEMAIL_CALL_TAG + " text, ")
          + (AnnotatedCallLog.TRANSCRIPTION_STATE + " integer, ")
          + (AnnotatedCallLog.CALL_MAPPING_ID + " text")
          + ");";

  private static final String ALTER_TABLE_SQL_ADD_CALL_MAPPING_ID_COLUMN =
      "alter table "
          + AnnotatedCallLog.TABLE
          + " add column "
          + AnnotatedCallLog.CALL_MAPPING_ID
          + " text;";
  private static final String UPDATE_CALL_MAPPING_ID_COLUMN =
      "update "
          + AnnotatedCallLog.TABLE
          + " set "
          + AnnotatedCallLog.CALL_MAPPING_ID
          + " = "
          + AnnotatedCallLog.TIMESTAMP;

  /**
   * Deletes all but the first maxRows rows (by timestamp, excluding voicemails) to keep the table a
   * manageable size.
   */
  private static final String CREATE_TRIGGER_SQL =
      "create trigger delete_old_rows after insert on "
          + AnnotatedCallLog.TABLE
          + " when (select count(*) from "
          + AnnotatedCallLog.TABLE
          + " where "
          + AnnotatedCallLog.CALL_TYPE
          + " != "
          + Calls.VOICEMAIL_TYPE
          + ") > %d"
          + " begin delete from "
          + AnnotatedCallLog.TABLE
          + " where "
          + AnnotatedCallLog._ID
          + " in (select "
          + AnnotatedCallLog._ID
          + " from "
          + AnnotatedCallLog.TABLE
          + " where "
          + AnnotatedCallLog.CALL_TYPE
          + " != "
          + Calls.VOICEMAIL_TYPE
          + " order by timestamp limit (select count(*)-%d"
          + " from "
          + AnnotatedCallLog.TABLE
          + " where "
          + AnnotatedCallLog.CALL_TYPE
          + " != "
          + Calls.VOICEMAIL_TYPE
          + ")); end;";

  private static final String CREATE_INDEX_ON_CALL_TYPE_SQL =
      "create index call_type_index on "
          + AnnotatedCallLog.TABLE
          + " ("
          + AnnotatedCallLog.CALL_TYPE
          + ");";

  private static final String CREATE_INDEX_ON_NUMBER_SQL =
      "create index number_index on "
          + AnnotatedCallLog.TABLE
          + " ("
          + AnnotatedCallLog.NUMBER
          + ");";

  @Override
  public void onCreate(SQLiteDatabase db) {
    LogUtil.enterBlock("AnnotatedCallLogDatabaseHelper.onCreate");
    long startTime = System.currentTimeMillis();
    db.execSQL(CREATE_TABLE_SQL);
    db.execSQL(String.format(Locale.US, CREATE_TRIGGER_SQL, maxRows, maxRows));
    db.execSQL(CREATE_INDEX_ON_CALL_TYPE_SQL);
    db.execSQL(CREATE_INDEX_ON_NUMBER_SQL);
    // TODO(zachh): Consider logging impression.
    LogUtil.i(
        "AnnotatedCallLogDatabaseHelper.onCreate",
        "took: %dms",
        System.currentTimeMillis() - startTime);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    if (oldVersion == 1 && newVersion == 2) {
      db.execSQL(ALTER_TABLE_SQL_ADD_CALL_MAPPING_ID_COLUMN);
      db.execSQL(UPDATE_CALL_MAPPING_ID_COLUMN);
    }
  }

  /** Closes the database and deletes it. */
  public ListenableFuture<Void> delete() {
    return backgroundExecutor.submit(
        () -> {
          close();
          appContext.deleteDatabase(FILENAME);
          return null;
        });
  }
}
