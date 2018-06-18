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

import android.support.annotation.MainThread;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * A source of data for one or more columns in the annotated call log.
 *
 * <p>Data sources have three lifecycle operations, which are always called on the same thread and
 * in the same order for a particular "checkDirtyAndRebuild" cycle. However, not all operations are
 * always invoked.
 *
 * <ol>
 *   <li>{@link #isDirty()}: Invoked only if the framework doesn't yet know if a rebuild is
 *       necessary.
 *   <li>{@link #fill(CallLogMutations)}: Invoked only if the framework determined a rebuild is
 *       necessary.
 *   <li>{@link #onSuccessfulFill()}: Invoked if and only if fill was previously called and the
 *       mutations provided by the previous fill operation succeeded in being applied.
 * </ol>
 *
 * <p>Because {@link #isDirty()} is not always invoked, {@link #fill(CallLogMutations)} shouldn't
 * rely on any state saved during {@link #isDirty()}. It <em>is</em> safe to assume that {@link
 * #onSuccessfulFill()} refers to the previous fill operation.
 *
 * <p>The same data source objects may be reused across multiple checkDirtyAndRebuild cycles, so
 * implementors should take care to clear any internal state at the start of a new cycle.
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
  ListenableFuture<Boolean> isDirty();

  /**
   * Computes the set of mutations necessary to update the annotated call log with respect to this
   * data source.
   *
   * @see CallLogDataSource class doc for complete lifecyle information
   * @param mutations the set of mutations which this method should contribute to. Note that it may
   *     contain inserts from the system call log, and these inserts should be modified by each data
   *     source.
   */
  ListenableFuture<Void> fill(CallLogMutations mutations);

  /**
   * Called after database mutations have been applied to all data sources. This is useful for
   * saving state such as the timestamp of the last row processed in an underlying database. Note
   * that all mutations across all data sources are applied in a single transaction.
   *
   * @see CallLogDataSource class doc for complete lifecyle information
   */
  ListenableFuture<Void> onSuccessfulFill();

  @MainThread
  void registerContentObservers();

  @MainThread
  void unregisterContentObservers();

  /**
   * Clear any data written by this data source. This is called when the new call log framework has
   * been disabled (because for example there was a problem with it).
   */
  @MainThread
  ListenableFuture<Void> clearData();

  /**
   * The name of this daa source for logging purposes. This is generally the same as the class name
   * (but should not use methods from {@link Class} because the class names are generally obfuscated
   * by Proguard.
   */
  String getLoggingName();
}
