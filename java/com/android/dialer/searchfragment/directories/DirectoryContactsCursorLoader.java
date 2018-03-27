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
import android.content.CursorLoader;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import com.android.dialer.common.cp2.DirectoryUtils;
import com.android.dialer.searchfragment.common.Projections;
import com.android.dialer.searchfragment.directories.DirectoriesCursorLoader.Directory;
import java.util.ArrayList;
import java.util.List;

/**
 * Cursor loader to load extended contacts on device.
 *
 * <p>This loader performs several database queries in serial and merges the resulting cursors
 * together into {@link DirectoryContactsCursor}. If there are no results, the loader will return a
 * null cursor.
 */
public final class DirectoryContactsCursorLoader extends CursorLoader {

  private static final Uri ENTERPRISE_CONTENT_FILTER_URI =
      Uri.withAppendedPath(Phone.CONTENT_URI, "filter_enterprise");

  private static final String IGNORE_NUMBER_TOO_LONG_CLAUSE = "length(" + Phone.NUMBER + ") < 1000";
  private static final String PHONE_NUMBER_NOT_NULL = Phone.NUMBER + " IS NOT NULL";
  private static final String MAX_RESULTS = "10";

  private final String query;
  private final List<Directory> directories;
  private final Cursor[] cursors;

  public DirectoryContactsCursorLoader(Context context, String query, List<Directory> directories) {
    super(
        context,
        null,
        Projections.DATA_PROJECTION,
        IGNORE_NUMBER_TOO_LONG_CLAUSE + " AND " + PHONE_NUMBER_NOT_NULL,
        null,
        Phone.SORT_KEY_PRIMARY);
    this.query = query;
    this.directories = new ArrayList<>(directories);
    cursors = new Cursor[directories.size()];
  }

  @Override
  public Cursor loadInBackground() {
    for (int i = 0; i < directories.size(); i++) {
      Directory directory = directories.get(i);

      if (!ContactsContract.Directory.isRemoteDirectoryId(directory.getId())
          && !ContactsContract.Directory.isEnterpriseDirectoryId(directory.getId())) {
        cursors[i] = null;
        continue;
      }

      // Filter out invisible directories.
      if (DirectoryUtils.isInvisibleDirectoryId(directory.getId())) {
        cursors[i] = null;
        continue;
      }

      Cursor cursor =
          getContext()
              .getContentResolver()
              .query(
                  getContentFilterUri(query, directory.getId()),
                  getProjection(),
                  getSelection(),
                  getSelectionArgs(),
                  getSortOrder());
      // Even though the cursor specifies "WHERE PHONE_NUMBER IS NOT NULL" the Blackberry Hub app's
      // directory extension doesn't appear to respect it, and sometimes returns a null phone
      // number. In this case just hide the row entirely. See a bug.
      cursors[i] = createMatrixCursorFilteringNullNumbers(cursor);
    }
    return DirectoryContactsCursor.newInstance(getContext(), cursors, directories);
  }

  private MatrixCursor createMatrixCursorFilteringNullNumbers(Cursor cursor) {
    if (cursor == null) {
      return null;
    }
    MatrixCursor matrixCursor = new MatrixCursor(cursor.getColumnNames());
    try {
      if (cursor.moveToFirst()) {
        do {
          String number = cursor.getString(Projections.PHONE_NUMBER);
          if (number == null) {
            continue;
          }
          matrixCursor.addRow(objectArrayFromCursor(cursor));
        } while (cursor.moveToNext());
      }
    } finally {
      cursor.close();
    }
    return matrixCursor;
  }

  @NonNull
  private static Object[] objectArrayFromCursor(@NonNull Cursor cursor) {
    Object[] values = new Object[cursor.getColumnCount()];
    for (int i = 0; i < cursor.getColumnCount(); i++) {
      int fieldType = cursor.getType(i);
      if (fieldType == Cursor.FIELD_TYPE_BLOB) {
        values[i] = cursor.getBlob(i);
      } else if (fieldType == Cursor.FIELD_TYPE_FLOAT) {
        values[i] = cursor.getDouble(i);
      } else if (fieldType == Cursor.FIELD_TYPE_INTEGER) {
        values[i] = cursor.getLong(i);
      } else if (fieldType == Cursor.FIELD_TYPE_STRING) {
        values[i] = cursor.getString(i);
      } else if (fieldType == Cursor.FIELD_TYPE_NULL) {
        values[i] = null;
      } else {
        throw new IllegalStateException("Unknown fieldType (" + fieldType + ") for column: " + i);
      }
    }
    return values;
  }

  @VisibleForTesting
  static Uri getContentFilterUri(String query, long directoryId) {
    return ENTERPRISE_CONTENT_FILTER_URI
        .buildUpon()
        .appendPath(query)
        .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(directoryId))
        .appendQueryParameter(ContactsContract.REMOVE_DUPLICATE_ENTRIES, "true")
        .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, MAX_RESULTS)
        .build();
  }
}
