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
 * limitations under the License.
 */

package com.android.dialer.searchfragment.cp2;

import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Directory;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.dialer.searchfragment.common.Projections;
import com.android.dialer.searchfragment.common.SearchCursor;
import com.android.dialer.smartdial.SmartDialCursorLoader;

/** Cursor Loader for CP2 contacts. */
public final class SearchContactsCursorLoader extends CursorLoader {

  private final String query;
  private final boolean isRegularSearch;

  /** @param query Contacts cursor will be filtered based on this query. */
  public SearchContactsCursorLoader(
      Context context, @Nullable String query, boolean isRegularSearch) {
    super(
        context,
        buildUri(query),
        getProjection(context),
        getWhere(context),
        null,
        getSortKey(context) + " ASC");
    this.query = TextUtils.isEmpty(query) ? "" : query;
    this.isRegularSearch = isRegularSearch;
  }

  private static String[] getProjection(Context context) {
    ContactsPreferences contactsPrefs = new ContactsPreferences(context);
    boolean displayOrderPrimary =
        (contactsPrefs.getDisplayOrder() == ContactsPreferences.DISPLAY_ORDER_PRIMARY);
    return displayOrderPrimary
        ? Projections.CP2_PROJECTION
        : Projections.CP2_PROJECTION_ALTERNATIVE;
  }

  private static String getWhere(Context context) {
    String where = getProjection(context)[Projections.DISPLAY_NAME] + " IS NOT NULL";
    where += " AND " + Phone.NUMBER + " IS NOT NULL";
    return where;
  }

  private static String getSortKey(Context context) {
    ContactsPreferences contactsPrefs = new ContactsPreferences(context);
    boolean sortOrderPrimary =
        (contactsPrefs.getSortOrder() == ContactsPreferences.SORT_ORDER_PRIMARY);
    return sortOrderPrimary ? Phone.SORT_KEY_PRIMARY : Phone.SORT_KEY_ALTERNATIVE;
  }

  private static Uri buildUri(String query) {
    return Phone.CONTENT_FILTER_URI.buildUpon().appendPath(query).build();
  }

  @Override
  public Cursor loadInBackground() {
    return isRegularSearch ? regularSearchLoadInBackground() : dialpadSearchLoadInBackground();
  }

  private Cursor regularSearchLoadInBackground() {
    return RegularSearchCursor.newInstance(getContext(), super.loadInBackground());
  }

  private Cursor dialpadSearchLoadInBackground() {
    SmartDialCursorLoader loader = new SmartDialCursorLoader(getContext());
    loader.configureQuery(query);
    Cursor cursor = loader.loadInBackground();
    return SmartDialCursor.newInstance(getContext(), cursor);
  }

  static class SmartDialCursor extends MergeCursor implements SearchCursor {

    static SmartDialCursor newInstance(Context context, Cursor smartDialCursor) {
      if (smartDialCursor.getCount() == 0) {
        return new SmartDialCursor(new Cursor[] {new MatrixCursor(Projections.CP2_PROJECTION)});
      }

      MatrixCursor headerCursor = new MatrixCursor(HEADER_PROJECTION);
      headerCursor.addRow(new String[] {context.getString(R.string.all_contacts)});
      return new SmartDialCursor(
          new Cursor[] {headerCursor, convertSmartDialCursorToSearchCursor(smartDialCursor)});
    }

    private SmartDialCursor(Cursor[] cursors) {
      super(cursors);
    }

    @Override
    public boolean isHeader() {
      return isFirst();
    }

    @Override
    public boolean updateQuery(@Nullable String query) {
      return false;
    }

    @Override
    public long getDirectoryId() {
      return Directory.DEFAULT;
    }

    private static MatrixCursor convertSmartDialCursorToSearchCursor(Cursor smartDialCursor) {
      MatrixCursor cursor = new MatrixCursor(Projections.CP2_PROJECTION);
      if (!smartDialCursor.moveToFirst()) {
        return cursor;
      }

      do {
        Object[] newRow = new Object[Projections.CP2_PROJECTION.length];
        for (int i = 0; i < Projections.CP2_PROJECTION.length; i++) {
          String column = Projections.CP2_PROJECTION[i];
          int index = smartDialCursor.getColumnIndex(column);
          if (index != -1) {
            switch (smartDialCursor.getType(index)) {
              case FIELD_TYPE_INTEGER:
                newRow[i] = smartDialCursor.getInt(index);
                break;
              case FIELD_TYPE_STRING:
                newRow[i] = smartDialCursor.getString(index);
                break;
              case FIELD_TYPE_FLOAT:
                newRow[i] = smartDialCursor.getFloat(index);
                break;
              case FIELD_TYPE_BLOB:
                newRow[i] = smartDialCursor.getBlob(index);
                break;
              case FIELD_TYPE_NULL:
              default:
                // No-op
                break;
            }
          }
        }
        cursor.addRow(newRow);
      } while (smartDialCursor.moveToNext());
      return cursor;
    }
  }

  static class RegularSearchCursor extends MergeCursor implements SearchCursor {

    static RegularSearchCursor newInstance(Context context, Cursor regularSearchCursor) {
      if (regularSearchCursor.getCount() == 0) {
        return new RegularSearchCursor(new Cursor[] {new MatrixCursor(Projections.CP2_PROJECTION)});
      }

      MatrixCursor headerCursor = new MatrixCursor(HEADER_PROJECTION);
      headerCursor.addRow(new String[] {context.getString(R.string.all_contacts)});
      return new RegularSearchCursor(new Cursor[] {headerCursor, regularSearchCursor});
    }

    public RegularSearchCursor(Cursor[] cursors) {
      super(cursors);
    }

    @Override
    public boolean isHeader() {
      return isFirst();
    }

    @Override
    public boolean updateQuery(@NonNull String query) {
      return false; // no-op
    }

    @Override
    public long getDirectoryId() {
      return 0; // no-op
    }
  }
}
