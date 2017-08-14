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

import android.database.Cursor;
import android.support.annotation.IntDef;
import android.support.annotation.StringRes;
import com.android.dialer.common.Assert;
import com.android.dialer.searchfragment.cp2.SearchContactCursor;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Manages all of the cursors needed for {@link SearchAdapter}.
 *
 * <p>This class accepts three cursors:
 *
 * <ul>
 *   <li>A contacts cursor {@link #setContactsCursor(Cursor)}
 *   <li>A google search results cursor {@link #setNearbyPlacesCursor(Cursor)}
 *   <li>A work directory cursor {@link #setCorpDirectoryCursor(Cursor)}
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
    SearchCursorManager.RowType.CONTACT_ROW,
    SearchCursorManager.RowType.NEARBY_PLACES_HEADER,
    SearchCursorManager.RowType.NEARBY_PLACES_ROW,
    SearchCursorManager.RowType.DIRECTORY_HEADER,
    SearchCursorManager.RowType.DIRECTORY_ROW
  })
  @interface RowType {
    int INVALID = 0;
    /** A row containing contact information for contacts stored locally on device. */
    int CONTACT_ROW = 1;
    /** Header to mark the end of contact rows and start of nearby places rows. */
    int NEARBY_PLACES_HEADER = 2;
    /** A row containing nearby places information/search results. */
    int NEARBY_PLACES_ROW = 3;
    /** Header to mark the end of the previous row set and start of directory rows. */
    int DIRECTORY_HEADER = 4;
    /** A row containing contact information for contacts stored externally in corp directories. */
    int DIRECTORY_ROW = 5;
  }

  private Cursor contactsCursor = null;
  private Cursor nearbyPlacesCursor = null;
  private Cursor corpDirectoryCursor = null;

  void setContactsCursor(Cursor cursor) {
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

  void setNearbyPlacesCursor(Cursor cursor) {
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

  void setCorpDirectoryCursor(Cursor cursor) {
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

  void setQuery(String query) {
    if (contactsCursor != null) {
      // TODO: abstract this
      ((SearchContactCursor) contactsCursor).filter(query);
    }
  }

  /** @return the sum of counts of all cursors, including headers. */
  int getCount() {
    int count = 0;
    if (contactsCursor != null) {
      count += contactsCursor.getCount();
    }

    if (nearbyPlacesCursor != null) {
      count++; // header
      count += nearbyPlacesCursor.getCount();
    }

    if (corpDirectoryCursor != null) {
      count++; // header
      count += corpDirectoryCursor.getCount();
    }

    return count;
  }

  @RowType
  int getRowType(int position) {
    if (contactsCursor != null) {
      position -= contactsCursor.getCount();

      if (position < 0) {
        return SearchCursorManager.RowType.CONTACT_ROW;
      }
    }

    if (nearbyPlacesCursor != null) {
      if (position == 0) {
        return SearchCursorManager.RowType.NEARBY_PLACES_HEADER;
      } else {
        position--; // header
      }

      position -= nearbyPlacesCursor.getCount();

      if (position < 0) {
        return SearchCursorManager.RowType.NEARBY_PLACES_ROW;
      }
    }

    if (corpDirectoryCursor != null) {
      if (position == 0) {
        return SearchCursorManager.RowType.DIRECTORY_HEADER;
      } else {
        position--; // header
      }

      position -= corpDirectoryCursor.getCount();

      if (position < 0) {
        return SearchCursorManager.RowType.DIRECTORY_ROW;
      }
    }

    throw Assert.createIllegalStateFailException("No valid row type.");
  }

  /**
   * Gets cursor corresponding to position in coelesced list of search cursors. Callers should
   * ensure that {@link #getRowType(int)} doesn't correspond to header position, otherwise an
   * exception will be thrown.
   *
   * @param position in coalecsed list of search cursors
   * @return Cursor moved to position specific to passed in position.
   */
  Cursor getCursor(int position) {
    if (contactsCursor != null) {
      int count = contactsCursor.getCount();

      if (position - count < 0) {
        contactsCursor.moveToPosition(position);
        return contactsCursor;
      }
      position -= count;
    }

    if (nearbyPlacesCursor != null) {
      Assert.checkArgument(position != 0, "No valid cursor, position is nearby places header.");
      position--; // header
      int count = nearbyPlacesCursor.getCount();

      if (position - count < 0) {
        nearbyPlacesCursor.moveToPosition(position);
        return nearbyPlacesCursor;
      }
      position -= count;
    }

    if (corpDirectoryCursor != null) {
      Assert.checkArgument(position != 0, "No valid cursor, position is directory search header.");
      position--; // header
      int count = corpDirectoryCursor.getCount();

      if (position - count < 0) {
        corpDirectoryCursor.moveToPosition(position);
        return corpDirectoryCursor;
      }
      position -= count;
    }

    throw Assert.createIllegalStateFailException("No valid cursor.");
  }

  @StringRes
  int getHeaderText(int position) {
    @RowType int rowType = getRowType(position);
    switch (rowType) {
      case RowType.NEARBY_PLACES_HEADER:
        return R.string.nearby_places;
      case RowType.DIRECTORY_HEADER: // TODO
      case RowType.DIRECTORY_ROW:
      case RowType.CONTACT_ROW:
      case RowType.NEARBY_PLACES_ROW:
      case RowType.INVALID:
      default:
        throw Assert.createIllegalStateFailException(
            "Invalid row type, position " + position + " is rowtype " + rowType);
    }
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
