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

package com.android.dialer.searchfragment.testing;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.support.annotation.Nullable;
import com.android.dialer.searchfragment.common.SearchCursor;

/** {@link SearchCursor} implementation useful for testing with a header inserted at position 0. */
public final class TestSearchCursor extends MergeCursor implements SearchCursor {

  public static TestSearchCursor newInstance(Cursor cursor, String header) {
    MatrixCursor headerRow = new MatrixCursor(HEADER_PROJECTION);
    headerRow.addRow(new String[] {header});
    return new TestSearchCursor(new Cursor[] {headerRow, cursor});
  }

  private TestSearchCursor(Cursor[] cursors) {
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
    return 0;
  }
}
