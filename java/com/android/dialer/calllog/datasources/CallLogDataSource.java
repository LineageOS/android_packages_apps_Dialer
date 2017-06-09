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

package com.android.dialer.calllog.datasources;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.MainThread;
import android.support.annotation.WorkerThread;
import com.android.dialer.calllog.database.CallLogMutations;

/** A source of data for one or more columns in the annotated call log. */
public interface CallLogDataSource {

  /**
   * A lightweight check which runs frequently to detect if the annotated call log is out of date
   * with respect to this data source.
   *
   * <p>This is typically used to detect external changes to the underlying data source which have
   * been made in such a way that the dialer application was not notified.
   *
   * <p>Most implementations of this method will rely on some sort of last modified timestamp. If it
   * is impossible for a data source to be modified without the dialer application being notified,
   * this method may immediately return false.
   */
  @WorkerThread
  boolean isDirty(Context appContext);

  /**
   * Computes the set of mutations necessary to update the annotated call log with respect to this
   * data source.
   *
   * @param mutations the set of mutations which this method should contribute to. Note that it may
   *     contain inserts from the system call log, and these inserts should be modified by each data
   *     source.
   */
  @WorkerThread
  void fill(
      Context appContext,
      SQLiteDatabase readableDatabase,
      long lastRebuildTimeMillis,
      CallLogMutations mutations);

  @MainThread
  void registerContentObservers(
      Context appContext, ContentObserverCallbacks contentObserverCallbacks);

  /**
   * Methods which may optionally be called as a result of a data source's content observer firing.
   */
  interface ContentObserverCallbacks {
    @MainThread
    void markDirtyAndNotify(Context appContext);
  }
}
