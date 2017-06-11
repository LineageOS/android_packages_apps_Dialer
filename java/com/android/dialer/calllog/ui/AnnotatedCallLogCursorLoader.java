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

package com.android.dialer.calllog.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import com.android.dialer.calllog.database.AnnotatedCallLog;
import com.android.dialer.calllog.database.AnnotatedCallLog.Columns;

/** CursorLoader which reads the annotated call log. */
class AnnotatedCallLogCursorLoader extends CursorLoader {

  AnnotatedCallLogCursorLoader(Context context) {
    super(context);
  }

  @TargetApi(Build.VERSION_CODES.M) // Uses try-with-resources
  @Override
  public Cursor loadInBackground() {
    try (SQLiteDatabase readableDatabase = AnnotatedCallLog.getReadableDatabase(getContext())) {
      return readableDatabase.rawQuery(
          "SELECT * FROM "
              + AnnotatedCallLog.TABLE_NAME
              + " ORDER BY "
              + Columns.TIMESTAMP
              + " DESC",
          null /* selectionArgs */);
    }
  }
}
