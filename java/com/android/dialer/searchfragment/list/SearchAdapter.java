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

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import com.android.dialer.common.Assert;
import com.android.dialer.searchfragment.common.SearchCursor;
import com.android.dialer.searchfragment.cp2.SearchContactViewHolder;
import com.android.dialer.searchfragment.list.SearchCursorManager.RowType;
import com.android.dialer.searchfragment.nearbyplaces.NearbyPlaceViewHolder;
import com.android.dialer.searchfragment.remote.RemoteContactViewHolder;

/** RecyclerView adapter for {@link NewSearchFragment}. */
class SearchAdapter extends RecyclerView.Adapter<ViewHolder> {

  private final SearchCursorManager searchCursorManager;
  private final Context context;

  private String query;

  SearchAdapter(Context context, SearchCursorManager searchCursorManager) {
    this.context = context;
    this.searchCursorManager = searchCursorManager;
  }

  @Override
  public ViewHolder onCreateViewHolder(ViewGroup root, @RowType int rowType) {
    switch (rowType) {
      case RowType.CONTACT_ROW:
        return new SearchContactViewHolder(
            LayoutInflater.from(context).inflate(R.layout.search_contact_row, root, false));
      case RowType.NEARBY_PLACES_ROW:
        return new NearbyPlaceViewHolder(
            LayoutInflater.from(context).inflate(R.layout.search_contact_row, root, false));
      case RowType.CONTACT_HEADER:
      case RowType.DIRECTORY_HEADER:
      case RowType.NEARBY_PLACES_HEADER:
        return new HeaderViewHolder(
            LayoutInflater.from(context).inflate(R.layout.header_layout, root, false));
      case RowType.DIRECTORY_ROW:
        return new RemoteContactViewHolder(
            LayoutInflater.from(context).inflate(R.layout.search_contact_row, root, false));
      case RowType.INVALID:
      default:
        throw Assert.createIllegalStateFailException("Invalid RowType: " + rowType);
    }
  }

  @Override
  public @RowType int getItemViewType(int position) {
    return searchCursorManager.getRowType(position);
  }

  @Override
  public void onBindViewHolder(ViewHolder holder, int position) {
    if (holder instanceof SearchContactViewHolder) {
      ((SearchContactViewHolder) holder).bind(searchCursorManager.getCursor(position), query);
    } else if (holder instanceof NearbyPlaceViewHolder) {
      ((NearbyPlaceViewHolder) holder).bind(searchCursorManager.getCursor(position), query);
    } else if (holder instanceof RemoteContactViewHolder) {
      ((RemoteContactViewHolder) holder).bind(searchCursorManager.getCursor(position), query);
    } else if (holder instanceof HeaderViewHolder) {
      String header =
          searchCursorManager.getCursor(position).getString(SearchCursor.HEADER_TEXT_POSITION);
      ((HeaderViewHolder) holder).setHeader(header);
    } else {
      throw Assert.createIllegalStateFailException("Invalid ViewHolder: " + holder);
    }
  }

  void setContactsCursor(SearchCursor cursor) {
    searchCursorManager.setContactsCursor(cursor);
    notifyDataSetChanged();
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
    if (searchCursorManager.setQuery(query)) {
      notifyDataSetChanged();
    }
  }

  public void setNearbyPlacesCursor(SearchCursor nearbyPlacesCursor) {
    if (searchCursorManager.setNearbyPlacesCursor(nearbyPlacesCursor)) {
      notifyDataSetChanged();
    }
  }

  public void setRemoteContactsCursor(SearchCursor remoteContactsCursor) {
    if (searchCursorManager.setCorpDirectoryCursor(remoteContactsCursor)) {
      notifyDataSetChanged();
    }
  }
}
