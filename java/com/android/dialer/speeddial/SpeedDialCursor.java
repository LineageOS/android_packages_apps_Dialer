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

package com.android.dialer.speeddial;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.support.annotation.IntDef;
import android.support.annotation.StringRes;
import android.util.ArraySet;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.speeddial.room.SpeedDialEntry;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Cursor for favorites contacts. */
public final class SpeedDialCursor extends MergeCursor {

  /**
   * Caps the speed dial list to contain at most 20 contacts, including favorites and suggestions.
   * It is only a soft limit though, for the case that there are more than 20 favorite contacts.
   */
  private static final int SPEED_DIAL_CONTACT_LIST_SOFT_LIMIT = 20;

  private static final String[] HEADER_CURSOR_PROJECTION = {"header"};
  private static final int HEADER_COLUMN_POSITION = 0;
  private boolean hasFavorites;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({RowType.HEADER, RowType.STARRED, RowType.SUGGESTION})
  @interface RowType {
    int HEADER = 0;
    int STARRED = 1;
    int SUGGESTION = 2;
  }

  public static SpeedDialCursor newInstance(Cursor strequentCursor, List<SpeedDialEntry> entries) {
    if (strequentCursor == null || strequentCursor.getCount() == 0) {
      return null;
    }
    SpeedDialCursor cursor = new SpeedDialCursor(buildCursors(strequentCursor, entries));
    strequentCursor.close();
    return cursor;
  }

  private static Cursor[] buildCursors(Cursor strequentCursor, List<SpeedDialEntry> entries) {
    MatrixCursor starred = new MatrixCursor(StrequentContactsCursorLoader.PHONE_PROJECTION);
    MatrixCursor suggestions = new MatrixCursor(StrequentContactsCursorLoader.PHONE_PROJECTION);

    // Build starred cursor
    for (SpeedDialEntry entry : entries) {
      if (!moveToContactEntry(strequentCursor, entry)) {
        LogUtil.e("SpeedDialCursor.buildCursors", "Entry not found: " + entry);
        continue;
      }

      if (strequentCursor.getInt(StrequentContactsCursorLoader.PHONE_STARRED) != 1) {
        LogUtil.e("SpeedDialCursor.buildCursors", "SpeedDialEntry contact is no longer starred");
        continue;
      }
      StrequentContactsCursorLoader.addToCursor(starred, strequentCursor);
    }

    // Build suggestions cursor
    strequentCursor.moveToFirst();
    Set<Long> contactIds = new ArraySet<>();
    do {
      if (strequentCursor.getInt(StrequentContactsCursorLoader.PHONE_STARRED) == 1) {
        // Starred contact
        continue;
      }

      long contactId = strequentCursor.getLong(StrequentContactsCursorLoader.PHONE_CONTACT_ID);
      if (!contactIds.add(contactId)) {
        // duplicate contact
        continue;
      }

      StrequentContactsCursorLoader.addToCursor(suggestions, strequentCursor);
    } while (strequentCursor.moveToNext()
        && starred.getCount() + suggestions.getCount() < SPEED_DIAL_CONTACT_LIST_SOFT_LIMIT);

    List<Cursor> cursorList = new ArrayList<>();
    cursorList.add(createHeaderCursor(R.string.favorites_header));
    if (starred.getCount() > 0) {
      cursorList.add(starred);
    }
    if (suggestions.getCount() > 0) {
      cursorList.add(createHeaderCursor(R.string.suggestions_header));
      cursorList.add(suggestions);
    }
    return cursorList.toArray(new Cursor[cursorList.size()]);
  }

  private static boolean moveToContactEntry(Cursor strequentCursor, SpeedDialEntry entry) {
    boolean matchFound;
    strequentCursor.moveToFirst();
    do {
      long contactId = strequentCursor.getLong(StrequentContactsCursorLoader.PHONE_CONTACT_ID);
      String number = strequentCursor.getString(StrequentContactsCursorLoader.PHONE_NUMBER);
      matchFound = contactId == entry.contactId || Objects.equals(number, entry.number);
    } while (!matchFound && strequentCursor.moveToNext());
    return matchFound;
  }

  private static Cursor createHeaderCursor(@StringRes int header) {
    MatrixCursor cursor = new MatrixCursor(HEADER_CURSOR_PROJECTION);
    cursor.newRow().add(HEADER_CURSOR_PROJECTION[HEADER_COLUMN_POSITION], header);
    return cursor;
  }

  @RowType
  int getRowType(int position) {
    moveToPosition(position);
    if (getColumnCount() == 1) {
      return RowType.HEADER;
    } else if (getInt(StrequentContactsCursorLoader.PHONE_STARRED) == 1) {
      return RowType.STARRED;
    } else {
      return RowType.SUGGESTION;
    }
  }

  @SuppressLint("DefaultLocale")
  @StringRes
  int getHeader() {
    if (getRowType(getPosition()) != RowType.HEADER) {
      throw Assert.createIllegalStateFailException(
          String.format("Current position (%d) is not a header.", getPosition()));
    }
    return getInt(HEADER_COLUMN_POSITION);
  }

  public boolean hasFavorites() {
    return hasFavorites;
  }

  private SpeedDialCursor(Cursor[] cursors) {
    super(cursors);
    for (Cursor cursor : cursors) {
      cursor.moveToFirst();
      if (cursor.getColumnCount() != 1
          && cursor.getInt(StrequentContactsCursorLoader.PHONE_STARRED) == 1) {
        hasFavorites = true;
        break;
      }
    }
  }
}
