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

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.WorkerThread;
import android.util.ArrayMap;
import android.util.ArraySet;
import com.android.dialer.common.Assert;

/** A collection of mutations to the annotated call log. */
public final class CallLogMutations {

  private final ArrayMap<Integer, ContentValues> inserts = new ArrayMap<>();
  private final ArrayMap<Integer, ContentValues> updates = new ArrayMap<>();
  private final ArraySet<Integer> deletes = new ArraySet<>();

  /** @param contentValues an entire row not including the ID */
  public void insert(int id, ContentValues contentValues) {
    inserts.put(id, contentValues);
  }

  /** @param contentValues the specific columns to update, not including the ID. */
  public void update(int id, ContentValues contentValues) {
    // TODO: Consider merging automatically.
    updates.put(id, contentValues);
  }

  public void delete(int id) {
    deletes.add(id);
  }

  public boolean isEmpty() {
    return inserts.isEmpty() && updates.isEmpty() && deletes.isEmpty();
  }

  @WorkerThread
  public void applyToDatabase(SQLiteDatabase writableDatabase) {
    Assert.isWorkerThread();

    // TODO: Implementation.
  }
}
