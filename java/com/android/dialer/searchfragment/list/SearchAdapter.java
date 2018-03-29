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
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import com.android.dialer.common.Assert;
import com.android.dialer.searchfragment.common.RowClickListener;
import com.android.dialer.searchfragment.common.SearchCursor;
import com.android.dialer.searchfragment.cp2.SearchContactViewHolder;
import com.android.dialer.searchfragment.directories.DirectoryContactViewHolder;
import com.android.dialer.searchfragment.list.SearchCursorManager.RowType;
import com.android.dialer.searchfragment.nearbyplaces.NearbyPlaceViewHolder;
import java.util.List;

/** RecyclerView adapter for {@link NewSearchFragment}. */
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
public final class SearchAdapter extends RecyclerView.Adapter<ViewHolder> {

  private final SearchCursorManager searchCursorManager;
  private final Context context;

  private String query;
  // Raw query number from dialpad, which may contain special character such as "+". This is used
  // for actions to add contact or send sms.
  private String rawNumber;
  private OnClickListener allowClickListener;
  private OnClickListener dismissClickListener;
  private RowClickListener rowClickListener;

  @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
  public SearchAdapter(
      Context context, SearchCursorManager searchCursorManager, RowClickListener rowClickListener) {
    this.context = context;
    this.searchCursorManager = searchCursorManager;
    this.rowClickListener = rowClickListener;
  }

  @Override
  public ViewHolder onCreateViewHolder(ViewGroup root, @RowType int rowType) {
    switch (rowType) {
      case RowType.CONTACT_ROW:
        return new SearchContactViewHolder(
            LayoutInflater.from(context).inflate(R.layout.search_contact_row, root, false),
            rowClickListener);
      case RowType.NEARBY_PLACES_ROW:
        return new NearbyPlaceViewHolder(
            LayoutInflater.from(context).inflate(R.layout.search_contact_row, root, false),
            rowClickListener);
      case RowType.CONTACT_HEADER:
      case RowType.DIRECTORY_HEADER:
      case RowType.NEARBY_PLACES_HEADER:
        return new HeaderViewHolder(
            LayoutInflater.from(context).inflate(R.layout.header_layout, root, false));
      case RowType.DIRECTORY_ROW:
        return new DirectoryContactViewHolder(
            LayoutInflater.from(context).inflate(R.layout.search_contact_row, root, false),
            rowClickListener);
      case RowType.SEARCH_ACTION:
        return new SearchActionViewHolder(
            LayoutInflater.from(context).inflate(R.layout.search_action_layout, root, false),
            rowClickListener);
      case RowType.LOCATION_REQUEST:
        return new LocationPermissionViewHolder(
            LayoutInflater.from(context).inflate(R.layout.location_permission_row, root, false),
            allowClickListener,
            dismissClickListener);
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
    } else if (holder instanceof DirectoryContactViewHolder) {
      ((DirectoryContactViewHolder) holder).bind(searchCursorManager.getCursor(position), query);
    } else if (holder instanceof HeaderViewHolder) {
      String header =
          searchCursorManager.getCursor(position).getString(SearchCursor.HEADER_TEXT_POSITION);
      ((HeaderViewHolder) holder).setHeader(header);
    } else if (holder instanceof SearchActionViewHolder) {
      ((SearchActionViewHolder) holder)
          .setAction(
              searchCursorManager.getSearchAction(position),
              position,
              TextUtils.isEmpty(rawNumber) ? query : rawNumber);
    } else if (holder instanceof LocationPermissionViewHolder) {
      // No-op
    } else {
      throw Assert.createIllegalStateFailException("Invalid ViewHolder: " + holder);
    }
  }

  public void setContactsCursor(SearchCursor cursor) {
    if (searchCursorManager.setContactsCursor(cursor)) {
      // Since this is a new contacts cursor, we need to reapply the filter.
      searchCursorManager.setQuery(query);
      notifyDataSetChanged();
    }
  }

  void clear() {
    searchCursorManager.clear();
  }

  @Override
  public int getItemCount() {
    return searchCursorManager.getCount();
  }

  public void setQuery(String query, @Nullable String rawNumber) {
    this.query = query;
    this.rawNumber = rawNumber;
    if (searchCursorManager.setQuery(query)) {
      notifyDataSetChanged();
    }
  }

  /** Sets the actions to be shown at the bottom of the search results. */
  void setSearchActions(List<Integer> actions) {
    if (searchCursorManager.setSearchActions(actions)) {
      notifyDataSetChanged();
    }
  }

  public void setNearbyPlacesCursor(SearchCursor nearbyPlacesCursor) {
    if (searchCursorManager.setNearbyPlacesCursor(nearbyPlacesCursor)) {
      notifyDataSetChanged();
    }
  }

  /**
   * Updates the adapter to show the location request row element. If the element was previously
   * hidden, the adapter will call {@link #notifyDataSetChanged()}.
   */
  public void showLocationPermissionRequest(
      OnClickListener allowClickListener, OnClickListener dismissClickListener) {
    this.allowClickListener = Assert.isNotNull(allowClickListener);
    this.dismissClickListener = Assert.isNotNull(dismissClickListener);
    if (searchCursorManager.showLocationPermissionRequest(true)) {
      notifyItemInserted(0);
    }
  }

  /**
   * Updates the adapter to hide the location request row element. If the element was previously
   * visible, the adapter will call {@link #notifyDataSetChanged()}.
   */
  void hideLocationPermissionRequest() {
    allowClickListener = null;
    dismissClickListener = null;
    if (searchCursorManager.showLocationPermissionRequest(false)) {
      notifyItemRemoved(0);
    }
  }

  void setDirectoryContactsCursor(SearchCursor directoryContactsCursor) {
    if (searchCursorManager.setCorpDirectoryCursor(directoryContactsCursor)) {
      notifyDataSetChanged();
    }
  }

  /** Viewholder for R.layout.location_permission_row that requests the location permission. */
  private static class LocationPermissionViewHolder extends RecyclerView.ViewHolder {

    LocationPermissionViewHolder(
        View itemView, OnClickListener allowClickListener, OnClickListener dismissClickListener) {
      super(itemView);
      Assert.isNotNull(allowClickListener);
      Assert.isNotNull(dismissClickListener);
      itemView
          .findViewById(
              com.android.dialer.searchfragment.nearbyplaces.R.id.location_permission_allow)
          .setOnClickListener(allowClickListener);
      itemView
          .findViewById(
              com.android.dialer.searchfragment.nearbyplaces.R.id.location_permission_dismiss)
          .setOnClickListener(dismissClickListener);
    }
  }
}
