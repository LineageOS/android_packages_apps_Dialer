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
import android.support.annotation.WorkerThread;
import com.android.dialer.common.Assert;

/** Static methods and constants for interacting with the annotated call log table. */
public final class AnnotatedCallLog {

  private static final String DATABASE_NAME = "annotated_call_log.db";

  public static final String TABLE_NAME = "AnnotatedCallLog";

  /** Column names for the annotated call log table. */
  public static final class Columns {
    public static final String ID = "_id";
    public static final String TIMESTAMP = "timestamp";
    public static final String CONTACT_NAME = "contact_name";
  }

  private AnnotatedCallLog() {}

  @WorkerThread
  public static SQLiteDatabase getWritableDatabase(Context appContext) {
    Assert.isWorkerThread();

    return new AnnotatedCallLogDatabaseHelper(appContext, DATABASE_NAME).getWritableDatabase();
  }

  @WorkerThread
  public static SQLiteDatabase getReadableDatabase(Context appContext) {
    Assert.isWorkerThread();

    return new AnnotatedCallLogDatabaseHelper(appContext, DATABASE_NAME).getReadableDatabase();
  }
}
