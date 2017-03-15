/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.dialer.p13n.inference.protocol;

import android.database.Cursor;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import java.util.List;

/** Provides personalized ranking of outgoing call targets. */
public interface P13nRanker {

  /**
   * Re-orders a list of phone numbers according to likelihood they will be the next outgoing call.
   *
   * @param phoneNumbers the list of candidate numbers to call (may be in contacts list or not)
   */
  @NonNull
  @MainThread
  List<String> rankList(@NonNull List<String> phoneNumbers);

  /**
   * Re-orders a retrieved contact list according to likelihood they will be the next outgoing call.
   *
   * <p>A new cursor with reordered data is returned; the input cursor is unmodified except for its
   * position. If the order is unchanged, this method may return a reference to the unmodified input
   * cursor directly. The order would be unchanged if the ranking cache is not yet ready, or if the
   * input cursor is closed or invalid, or if any other error occurs in the ranking process.
   *
   * @param phoneQueryResults cursor of results of a Dialer search query
   * @param queryLength length of the search query that resulted in the cursor data, if below 0,
   *     assumes no length is specified, thus applies the default behavior which is same as when
   *     queryLength is greater than zero.
   * @return new cursor of data reordered by ranking (or reference to input cursor if order
   *     unchanged)
   */
  @NonNull
  @MainThread
  Cursor rankCursor(@NonNull Cursor phoneQueryResults, int queryLength);

  /**
   * Refreshes ranking cache (pulls fresh contextual features, pre-caches inference results, etc.).
   *
   * <p>Asynchronously runs in background as the process might take a few seconds, notifying a
   * listener upon completion; meanwhile, any calls to {@link #rankList} will simply return the
   * input in same order.
   *
   * @param listener callback for when ranking refresh has completed; null value skips notification.
   */
  @MainThread
  void refresh(@Nullable P13nRefreshCompleteListener listener);

  /** Decides if results should be displayed for no-query search. */
  @MainThread
  boolean shouldShowEmptyListForNullQuery();

  /**
   * Callback class for when ranking refresh has completed.
   *
   * <p>Primary use is to notify {@link com.android.dialer.app.DialtactsActivity} that the ranking
   * functions {@link #rankList} and {@link #rankCursor(Cursor, int)} will now give useful results.
   */
  interface P13nRefreshCompleteListener {

    /** Callback for when ranking refresh has completed. */
    void onP13nRefreshComplete();
  }
}
