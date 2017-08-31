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

import android.content.ContentValues;
import android.content.Context;
import android.support.annotation.MainThread;
import android.support.annotation.WorkerThread;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract;
import java.util.List;

/**
 * A source of data for one or more columns in the annotated call log.
 *
 * <p>Data sources have three lifecycle operations, which are always called on the same thread and
 * in the same order for a particular "checkDirtyAndRebuild" cycle. However, not all operations are
 * always invoked.
 *
 * <ol>
 *   <li>{@link #isDirty(Context)}: Invoked only if the framework doesn't yet know if a rebuild is
 *       necessary.
 *   <li>{@link #fill(Context, CallLogMutations)}: Invoked only if the framework determined a
 *       rebuild is necessary.
 *   <li>{@link #onSuccessfulFill(Context)}: Invoked if and only if fill was previously called and
 *       the mutations provided by the previous fill operation succeeded in being applied.
 * </ol>
 *
 * <p>Because {@link #isDirty(Context)} is not always invoked, {@link #fill(Context,
 * CallLogMutations)} shouldn't rely on any state saved during {@link #isDirty(Context)}. It
 * <em>is</em> safe to assume that {@link #onSuccessfulFill(Context)} refers to the previous fill
 * operation.
 *
 * <p>The same data source objects may be reused across multiple checkDirtyAndRebuild cycles, so
 * implementors should take care to clear any internal state at the start of a new cycle.
 *
 * <p>{@link #coalesce(List)} may be called from any worker thread at any time.
 */
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
   *
   * @see CallLogDataSource class doc for complete lifecyle information
   */
  @WorkerThread
  boolean isDirty(Context appContext);

  /**
   * Computes the set of mutations necessary to update the annotated call log with respect to this
   * data source.
   *
   * @see CallLogDataSource class doc for complete lifecyle information
   * @param mutations the set of mutations which this method should contribute to. Note that it may
   *     contain inserts from the system call log, and these inserts should be modified by each data
   *     source.
   */
  @WorkerThread
  void fill(Context appContext, CallLogMutations mutations);

  /**
   * Called after database mutations have been applied to all data sources. This is useful for
   * saving state such as the timestamp of the last row processed in an underlying database. Note
   * that all mutations across all data sources are applied in a single transaction.
   *
   * @see CallLogDataSource class doc for complete lifecyle information
   */
  @WorkerThread
  void onSuccessfulFill(Context appContext);

  /**
   * Combines raw annotated call log rows into a single coalesced row.
   *
   * <p>May be called by any worker thread at any time so implementations should take care to be
   * threadsafe. (Ideally no state should be required to implement this.)
   *
   * @param individualRowsSortedByTimestampDesc group of fully populated rows from {@link
   *     AnnotatedCallLogContract.AnnotatedCallLog} which need to be combined for display purposes.
   *     This method should not modify this list.
   * @return a partial {@link AnnotatedCallLogContract.CoalescedAnnotatedCallLog} row containing
   *     only columns which this data source is responsible for, which is the result of aggregating
   *     {@code individualRowsSortedByTimestampDesc}.
   */
  @WorkerThread
  ContentValues coalesce(List<ContentValues> individualRowsSortedByTimestampDesc);

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
