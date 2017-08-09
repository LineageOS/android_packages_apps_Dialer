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

package com.android.dialer.searchfragment.list;

import android.support.annotation.IntDef;
import com.android.dialer.common.Assert;
import com.android.dialer.searchfragment.common.SearchCursor;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Manages all of the cursors needed for {@link SearchAdapter}.
 *
 * <p>This class accepts three cursors:
 *
 * <ul>
 *   <li>A contacts cursor {@link #setContactsCursor(SearchCursor)}
 *   <li>A google search results cursor {@link #setNearbyPlacesCursor(SearchCursor)}
 *   <li>A work directory cursor {@link #setCorpDirectoryCursor(SearchCursor)}
 * </ul>
 *
 * <p>The key purpose of this class is to compose three aforementioned cursors together to function
 * as one cursor. The key methods needed to utilize this class as a cursor are:
 *
 * <ul>
 *   <li>{@link #getCursor(int)}
 *   <li>{@link #getCount()}
 *   <li>{@link #getRowType(int)}
 * </ul>
 */
final class SearchCursorManager {

  /** IntDef for the different types of rows that can be shown when searching. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    SearchCursorManager.RowType.INVALID,
    SearchCursorManager.RowType.CONTACT_HEADER,
    SearchCursorManager.RowType.CONTACT_ROW,
    SearchCursorManager.RowType.NEARBY_PLACES_HEADER,
    SearchCursorManager.RowType.NEARBY_PLACES_ROW,
    SearchCursorManager.RowType.DIRECTORY_HEADER,
    SearchCursorManager.RowType.DIRECTORY_ROW
  })
  @interface RowType {
    int INVALID = 0;
    /** Header to mark the start of contact rows. */
    int CONTACT_HEADER = 1;
    /** A row containing contact information for contacts stored locally on device. */
    int CONTACT_ROW = 2;
    /** Header to mark the end of contact rows and start of nearby places rows. */
    int NEARBY_PLACES_HEADER = 3;
    /** A row containing nearby places information/search results. */
    int NEARBY_PLACES_ROW = 4;
    /** Header to mark the end of the previous row set and start of directory rows. */
    int DIRECTORY_HEADER = 5;
    /** A row containing contact information for contacts stored externally in corp directories. */
    int DIRECTORY_ROW = 6;
  }

  private SearchCursor contactsCursor = null;
  private SearchCursor nearbyPlacesCursor = null;
  private SearchCursor corpDirectoryCursor = null;

  void setContactsCursor(SearchCursor cursor) {
    if (cursor == contactsCursor) {
      return;
    }

    if (contactsCursor != null && !contactsCursor.isClosed()) {
      contactsCursor.close();
    }

    if (cursor != null && cursor.getCount() > 0) {
      contactsCursor = cursor;
    } else {
      contactsCursor = null;
    }
  }

  void setNearbyPlacesCursor(SearchCursor cursor) {
    if (cursor == nearbyPlacesCursor) {
      return;
    }

    if (nearbyPlacesCursor != null && !nearbyPlacesCursor.isClosed()) {
      nearbyPlacesCursor.close();
    }

    if (cursor != null && cursor.getCount() > 0) {
      nearbyPlacesCursor = cursor;
    } else {
      nearbyPlacesCursor = null;
    }
  }

  void setCorpDirectoryCursor(SearchCursor cursor) {
    if (cursor == corpDirectoryCursor) {
      return;
    }

    if (corpDirectoryCursor != null && !corpDirectoryCursor.isClosed()) {
      corpDirectoryCursor.close();
    }

    if (cursor != null && cursor.getCount() > 0) {
      corpDirectoryCursor = cursor;
    } else {
      corpDirectoryCursor = null;
    }
  }

  boolean setQuery(String query) {
    boolean updated = false;
    if (contactsCursor != null) {
      updated = contactsCursor.updateQuery(query);
    }

    if (nearbyPlacesCursor != null) {
      updated |= nearbyPlacesCursor.updateQuery(query);
    }

    if (corpDirectoryCursor != null) {
      updated |= corpDirectoryCursor.updateQuery(query);
    }
    return updated;
  }

  /** Returns the sum of counts of all cursors, including headers. */
  int getCount() {
    int count = 0;
    if (contactsCursor != null) {
      count += contactsCursor.getCount();
    }

    if (nearbyPlacesCursor != null) {
      count += nearbyPlacesCursor.getCount();
    }

    if (corpDirectoryCursor != null) {
      count += corpDirectoryCursor.getCount();
    }

    return count;
  }

  @RowType
  int getRowType(int position) {
    SearchCursor cursor = getCursor(position);
    if (cursor == contactsCursor) {
      return cursor.isHeader() ? RowType.CONTACT_HEADER : RowType.CONTACT_ROW;
    }

    if (cursor == nearbyPlacesCursor) {
      return cursor.isHeader() ? RowType.NEARBY_PLACES_HEADER : RowType.NEARBY_PLACES_ROW;
    }

    if (cursor == corpDirectoryCursor) {
      return cursor.isHeader() ? RowType.DIRECTORY_HEADER : RowType.DIRECTORY_ROW;
    }
    throw Assert.createIllegalStateFailException("No valid row type.");
  }

  /**
   * Gets cursor corresponding to position in coalesced list of search cursors. Callers should
   * ensure that {@link #getRowType(int)} doesn't correspond to header position, otherwise an
   * exception will be thrown.
   *
   * @param position in coalesced list of search cursors
   * @return Cursor moved to position specific to passed in position.
   */
  SearchCursor getCursor(int position) {
    if (contactsCursor != null) {
      int count = contactsCursor.getCount();

      if (position - count < 0) {
        contactsCursor.moveToPosition(position);
        return contactsCursor;
      }
      position -= count;
    }

    if (nearbyPlacesCursor != null) {
      int count = nearbyPlacesCursor.getCount();

      if (position - count < 0) {
        nearbyPlacesCursor.moveToPosition(position);
        return nearbyPlacesCursor;
      }
      position -= count;
    }

    if (corpDirectoryCursor != null) {
      int count = corpDirectoryCursor.getCount();

      if (position - count < 0) {
        corpDirectoryCursor.moveToPosition(position);
        return corpDirectoryCursor;
      }
      position -= count;
    }

    throw Assert.createIllegalStateFailException("No valid cursor.");
  }

  String getHeaderText(int position) {
    return getCursor(position).getString(SearchCursor.HEADER_TEXT_POSITION);
  }

  /** removes all cursors. */
  void clear() {
    if (contactsCursor != null) {
      contactsCursor.close();
      contactsCursor = null;
    }

    if (nearbyPlacesCursor != null) {
      nearbyPlacesCursor.close();
      nearbyPlacesCursor = null;
    }

    if (corpDirectoryCursor != null) {
      corpDirectoryCursor.close();
      corpDirectoryCursor = null;
    }
  }
}
