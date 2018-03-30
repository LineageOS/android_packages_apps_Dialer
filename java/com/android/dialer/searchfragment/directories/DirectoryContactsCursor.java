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

package com.android.dialer.searchfragment.directories;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import com.android.dialer.common.Assert;
import com.android.dialer.common.cp2.DirectoryUtils;
import com.android.dialer.searchfragment.common.SearchCursor;
import com.android.dialer.searchfragment.directories.DirectoriesCursorLoader.Directory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@link MergeCursor} used for combining directory cursors into one cursor.
 *
 * <p>Usually a device with multiple Google accounts will have multiple directories returned by
 * {@link DirectoriesCursorLoader}, each represented as a {@link Directory}.
 *
 * <p>This cursor merges them together with a header at the start of each cursor/list using {@link
 * Directory#getDisplayName()} as the header text.
 */
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
public final class DirectoryContactsCursor extends MergeCursor implements SearchCursor {

  /**
   * {@link SearchCursor#HEADER_PROJECTION} with {@link #COLUMN_DIRECTORY_ID} appended on the end.
   *
   * <p>This is needed to get the directoryId associated with each contact. directoryIds are needed
   * to load the correct quick contact card.
   */
  private static final String[] PROJECTION = buildProjection();

  private static final String COLUMN_DIRECTORY_ID = "directory_id";

  /**
   * Returns a single cursor with headers inserted between each non-empty cursor. If all cursors are
   * empty, null or closed, this method returns null.
   */
  @Nullable
  @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
  public static DirectoryContactsCursor newInstance(
      Context context, Cursor[] cursors, List<Directory> directories) {
    Assert.checkArgument(
        cursors.length == directories.size(),
        "Directories (%d) and cursors (%d) must be the same size.",
        directories.size(),
        cursors.length);
    Cursor[] cursorsWithHeaders = insertHeaders(context, cursors, directories);
    if (cursorsWithHeaders.length > 0) {
      return new DirectoryContactsCursor(cursorsWithHeaders);
    }
    return null;
  }

  private DirectoryContactsCursor(Cursor[] cursors) {
    super(cursors);
  }

  private static Cursor[] insertHeaders(
      Context context, Cursor[] cursors, List<Directory> directories) {
    List<Cursor> cursorList = new ArrayList<>();
    for (int i = 0; i < cursors.length; i++) {
      Cursor cursor = cursors[i];

      if (cursor == null || cursor.isClosed()) {
        continue;
      }

      Directory directory = directories.get(i);
      if (cursor.getCount() == 0) {
        // Since the cursor isn't being merged in, we need to close it here.
        cursor.close();
        continue;
      }

      cursorList.add(createHeaderCursor(context, directory.getDisplayName(), directory.getId()));
      cursorList.add(cursor);
    }
    return cursorList.toArray(new Cursor[cursorList.size()]);
  }

  private static MatrixCursor createHeaderCursor(Context context, String name, long id) {
    MatrixCursor headerCursor = new MatrixCursor(PROJECTION, 1);
    if (DirectoryUtils.isLocalEnterpriseDirectoryId(id)) {
      headerCursor.addRow(
          new Object[] {context.getString(R.string.directory_search_label_work), id});
    } else {
      headerCursor.addRow(new Object[] {context.getString(R.string.directory, name), id});
    }
    return headerCursor;
  }

  private static String[] buildProjection() {
    String[] projection = Arrays.copyOf(HEADER_PROJECTION, HEADER_PROJECTION.length + 1);
    projection[projection.length - 1] = COLUMN_DIRECTORY_ID;
    return projection;
  }

  /** Returns true if the current position is a header row. */
  @Override
  public boolean isHeader() {
    return !isClosed() && getColumnIndex(HEADER_PROJECTION[HEADER_TEXT_POSITION]) != -1;
  }

  @Override
  public long getDirectoryId() {
    int position = getPosition();
    // proceed backwards until we reach the header row, which contains the directory ID.
    while (moveToPrevious()) {
      int columnIndex = getColumnIndex(COLUMN_DIRECTORY_ID);
      if (columnIndex == -1) {
        continue;
      }

      int id = getInt(columnIndex);
      if (id == -1) {
        continue;
      }

      // return the cursor to it's original position/state
      moveToPosition(position);
      return id;
    }
    throw Assert.createIllegalStateFailException("No directory id for contact at: " + position);
  }

  @Override
  public boolean updateQuery(@Nullable String query) {
    // When the query changes, a new network request is made for nearby places. Meaning this cursor
    // will be closed and another created, so return false.
    return false;
  }
}
