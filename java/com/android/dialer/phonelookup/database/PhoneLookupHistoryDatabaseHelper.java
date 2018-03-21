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
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.phonelookup.database.contract.PhoneLookupHistoryContract.PhoneLookupHistory;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;

/** {@link SQLiteOpenHelper} for the PhoneLookupHistory database. */
@Singleton
public class PhoneLookupHistoryDatabaseHelper extends SQLiteOpenHelper {

  private static final String FILENAME = "phone_lookup_history.db";

  private final Context appContext;
  private final ListeningExecutorService backgroundExecutor;

  @Inject
  PhoneLookupHistoryDatabaseHelper(
      @ApplicationContext Context appContext,
      @BackgroundExecutor ListeningExecutorService backgroundExecutor) {
    super(appContext, FILENAME, null, 1);

    this.appContext = appContext;
    this.backgroundExecutor = backgroundExecutor;
  }

  // TODO(zachh): LAST_MODIFIED is no longer read and can be deleted.
  private static final String CREATE_TABLE_SQL =
      "create table if not exists "
          + PhoneLookupHistory.TABLE
          + " ("
          + (PhoneLookupHistory.NORMALIZED_NUMBER + " text primary key not null, ")
          + (PhoneLookupHistory.PHONE_LOOKUP_INFO + " blob not null, ")
          + (PhoneLookupHistory.LAST_MODIFIED + " long not null")
          + ");";

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
    db.execSQL(CREATE_INDEX_ON_LAST_MODIFIED_SQL);
    // TODO(zachh): Consider logging impression.
    LogUtil.i(
        "PhoneLookupHistoryDatabaseHelper.onCreate",
        "took: %dms",
        SystemClock.uptimeMillis() - startTime);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

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
