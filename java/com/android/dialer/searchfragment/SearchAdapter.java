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

package com.android.dialer.searchfragment;

import android.content.Context;
import android.database.Cursor;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import com.android.dialer.common.Assert;
import com.android.dialer.searchfragment.SearchCursorManager.RowType;

/** RecyclerView adapter for {@link NewSearchFragment}. */
class SearchAdapter extends RecyclerView.Adapter<ViewHolder> {

  private final SearchCursorManager searchCursorManager;
  private final Context context;

  private String query;

  SearchAdapter(Context context) {
    searchCursorManager = new SearchCursorManager();
    this.context = context;
  }

  // TODO: fill in the rest of the view holders.
  @Override
  public ViewHolder onCreateViewHolder(ViewGroup root, int position) {
    @RowType int rowType = searchCursorManager.getRowType(position);
    switch (rowType) {
      case RowType.CONTACT_ROW:
        return new SearchContactViewHolder(
            LayoutInflater.from(context).inflate(R.layout.search_contact_row, root, false));
      case RowType.DIRECTORY_HEADER:
      case RowType.DIRECTORY_ROW:
      case RowType.INVALID:
      case RowType.NEARBY_PLACES_HEADER:
      case RowType.NEARBY_PLACES_ROW:
        return null;
      default:
        throw Assert.createIllegalStateFailException("Invalid RowType: " + rowType);
    }
  }

  @Override
  public void onBindViewHolder(ViewHolder holder, int position) {
    Cursor cursor = searchCursorManager.getCursor(position);
    ((SearchContactViewHolder) holder).bind(cursor, query);
  }

  void setContactsCursor(Cursor cursor) {
    searchCursorManager.setContactsCursor(cursor);
  }

  void clear() {
    searchCursorManager.clear();
  }

  @Override
  public int getItemCount() {
    return searchCursorManager.getCount();
  }

  public void setQuery(String query) {
    this.query = query;
    searchCursorManager.setQuery(query);
    notifyDataSetChanged();
  }
}
