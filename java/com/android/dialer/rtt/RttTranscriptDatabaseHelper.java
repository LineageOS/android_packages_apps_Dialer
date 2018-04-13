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

package com.android.dialer.rtt;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.SystemClock;
import com.android.dialer.common.LogUtil;
import com.android.dialer.rtt.RttTranscriptContract.RttTranscriptColumn;

/** Database helper class for RTT transcript. */
final class RttTranscriptDatabaseHelper extends SQLiteOpenHelper {

  static final String TABLE = "rtt_transcript";

  private static final String CREATE_TABLE_SQL =
      "create table if not exists "
          + TABLE
          + " ("
          + (RttTranscriptColumn.TRANSCRIPT_ID + " integer primary key, ")
          + (RttTranscriptColumn.TRANSCRIPT_DATA + " blob not null")
          + ");";

  RttTranscriptDatabaseHelper(Context context) {
    super(context, "rtt_transcript.db", null, 1);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    LogUtil.enterBlock("RttTranscriptDatabaseHelper.onCreate");
    long startTime = SystemClock.elapsedRealtime();
    db.execSQL(CREATE_TABLE_SQL);
    LogUtil.i(
        "RttTranscriptDatabaseHelper.onCreate",
        "took: %dms",
        SystemClock.elapsedRealtime() - startTime);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
}
