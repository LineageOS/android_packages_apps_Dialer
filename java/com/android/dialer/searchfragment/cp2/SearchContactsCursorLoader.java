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
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.support.annotation.Nullable;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.dialer.searchfragment.common.Projections;

/** Cursor Loader for CP2 contacts. */
public final class SearchContactsCursorLoader extends CursorLoader {

  private final String query;

  /** @param query Contacts cursor will be filtered based on this query. */
  public SearchContactsCursorLoader(Context context, @Nullable String query) {
    super(
        context,
        Data.CONTENT_URI,
        Projections.CP2_PROJECTION,
        whereStatement(),
        null,
        Phone.SORT_KEY_PRIMARY + " ASC");
    this.query = query;

    ContactsPreferences preferences = new ContactsPreferences(getContext());
    if (preferences.getSortOrder() == ContactsPreferences.SORT_ORDER_ALTERNATIVE) {
      setSortOrder(Phone.SORT_KEY_ALTERNATIVE + " ASC");
    }
    if (preferences.getDisplayOrder() == ContactsPreferences.DISPLAY_ORDER_ALTERNATIVE) {
      setProjection(Projections.CP2_PROJECTION_ALTERNATIVE);
    }
  }

  /**
   * Note: ContactsProvider can make no guarantee that any given field is non-null, and display name
   * has been observed to be null in the wild, though it is unclear when that might happen (possibly
   * a third-party is inserting such data). See a bug.
   *
   * <p>We skip showing contacts without a display name because there is no UI treatment for showing
   * such results. (Note that even contacts with only a number still have a display name set to the
   * number.)
   */
  private static String whereStatement() {
    return (Phone.NUMBER + " IS NOT NULL")
        + " AND "
        + (Data.DISPLAY_NAME_PRIMARY + " IS NOT NULL")
        + " AND "
        + Data.MIMETYPE
        + " IN (\'"
        + Phone.CONTENT_ITEM_TYPE
        + "\', \'"
        + Nickname.CONTENT_ITEM_TYPE
        + "\', \'"
        + Organization.CONTENT_ITEM_TYPE
        + "\')";
  }

  @Override
  public Cursor loadInBackground() {
    // All contacts
    Cursor cursor = super.loadInBackground();
    // Filtering logic
    ContactFilterCursor contactFilterCursor = new ContactFilterCursor(cursor, query, getContext());
    // Header logic
    return SearchContactsCursor.newInstance(getContext(), contactFilterCursor);
  }
}
