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

package com.android.dialer.searchfragment.cp2;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.provider.ContactsContract.Directory;
import android.support.annotation.Nullable;
import com.android.dialer.searchfragment.common.SearchCursor;

/**
 * {@link SearchCursor} implementation for displaying on device contacts.
 *
 * <p>Inserts header "All Contacts" at position 0.
 */
final class SearchContactsCursor extends MergeCursor implements SearchCursor {

  private final ContactFilterCursor contactFilterCursor;
  private final Context context;

  static SearchContactsCursor newInstance(
      Context context, ContactFilterCursor contactFilterCursor) {
    MatrixCursor headerCursor = new MatrixCursor(HEADER_PROJECTION);
    headerCursor.addRow(new String[] {context.getString(R.string.all_contacts)});
    return new SearchContactsCursor(new Cursor[] {headerCursor, contactFilterCursor}, context);
  }

  private SearchContactsCursor(Cursor[] cursors, Context context) {
    super(cursors);
    this.contactFilterCursor = (ContactFilterCursor) cursors[1];
    this.context = context;
  }

  @Override
  public boolean isHeader() {
    return isFirst();
  }

  @Override
  public boolean updateQuery(@Nullable String query) {
    contactFilterCursor.filter(query, context);
    return true;
  }

  @Override
  public long getDirectoryId() {
    return Directory.DEFAULT;
  }

  @Override
  public int getCount() {
    // If we don't have any contents, we don't want to show the header
    int count = contactFilterCursor.getCount();
    return count == 0 ? 0 : count + 1;
  }
}
