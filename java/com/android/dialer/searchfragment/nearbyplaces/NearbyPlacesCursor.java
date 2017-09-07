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

package com.android.dialer.searchfragment.nearbyplaces;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.support.annotation.Nullable;
import com.android.dialer.searchfragment.common.SearchCursor;

/** {@link SearchCursor} implementation for displaying on nearby places. */
final class NearbyPlacesCursor extends MergeCursor implements SearchCursor {

  private final Cursor nearbyPlacesCursor;
  private final long directoryId;

  /**
   * @param directoryId unique directory id that doesn't collide with other remote/local
   *     directories. directoryIds are needed to load the correct quick contact card.
   */
  static NearbyPlacesCursor newInstance(
      Context context, Cursor nearbyPlacesCursor, long directoryId) {
    MatrixCursor headerCursor = new MatrixCursor(HEADER_PROJECTION);
    headerCursor.addRow(new String[] {context.getString(R.string.nearby_places)});
    return new NearbyPlacesCursor(new Cursor[] {headerCursor, nearbyPlacesCursor}, directoryId);
  }

  private NearbyPlacesCursor(Cursor[] cursors, long directoryId) {
    super(cursors);
    nearbyPlacesCursor = cursors[1];
    this.directoryId = directoryId;
  }

  @Override
  public boolean isHeader() {
    return isFirst();
  }

  @Override
  public boolean updateQuery(@Nullable String query) {
    // When the query changes, a new network request is made for nearby places. Meaning this cursor
    // will be closed and another created, so return false.
    return false;
  }

  @Override
  public int getCount() {
    // If we don't have any contents, we don't want to show the header
    if (nearbyPlacesCursor == null || nearbyPlacesCursor.isClosed()) {
      return 0;
    }

    int count = nearbyPlacesCursor.getCount();
    return count == 0 ? 0 : count + 1;
  }

  @Override
  public long getDirectoryId() {
    return directoryId;
  }
}
