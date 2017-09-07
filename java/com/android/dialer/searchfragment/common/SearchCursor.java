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

package com.android.dialer.searchfragment.common;

import android.database.Cursor;
import android.support.annotation.NonNull;

/** Base cursor interface needed for all cursors used in search. */
public interface SearchCursor extends Cursor {

  String[] HEADER_PROJECTION = {"header_text"};

  int HEADER_TEXT_POSITION = 0;

  /** Returns true if the current cursor position is a header */
  boolean isHeader();

  /**
   * Notifies the cursor that the query has updated.
   *
   * @return true if the data set has changed.
   */
  boolean updateQuery(@NonNull String query);

  /**
   * Returns an ID unique to the directory this cursor reads from. Generally this value will be
   * related to {@link android.provider.ContactsContract.Directory} but could differ depending on
   * the implementation.
   */
  long getDirectoryId();
}
