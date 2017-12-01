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

package com.android.dialer.speeddial;

import android.content.Context;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.GridLayoutManager.SpanSizeLookup;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.LayoutManager;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import com.android.dialer.common.Assert;
import com.android.dialer.speeddial.FavoritesViewHolder.FavoriteContactsListener;
import com.android.dialer.speeddial.HeaderViewHolder.SpeedDialHeaderListener;
import com.android.dialer.speeddial.SpeedDialCursor.RowType;
import com.android.dialer.speeddial.SuggestionViewHolder.SuggestedContactsListener;

/**
 * RecyclerView adapter for {@link SpeedDialFragment}.
 *
 * <p>Displays a list in the following order:
 *
 * <ol>
 *   <li>Favorite contacts header (with add button)
 *   <li>Favorite contacts
 *   <li>Suggested contacts header
 *   <li>Suggested contacts
 * </ol>
 */
final class SpeedDialAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  private final Context context;
  private final FavoriteContactsListener favoritesListener;
  private final SuggestedContactsListener suggestedListener;
  private final SpeedDialHeaderListener headerListener;

  private SpeedDialCursor cursor;

  public SpeedDialAdapter(
      Context context,
      FavoriteContactsListener favoritesListener,
      SuggestedContactsListener suggestedListener,
      SpeedDialHeaderListener headerListener) {
    this.context = context;
    this.favoritesListener = favoritesListener;
    this.suggestedListener = suggestedListener;
    this.headerListener = headerListener;
  }

  @Override
  public int getItemViewType(int position) {
    return cursor.getRowType(position);
  }

  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(context);
    if (viewType == RowType.STARRED) {
      return new FavoritesViewHolder(
          inflater.inflate(R.layout.favorite_item_layout, parent, false), favoritesListener);
    } else if (viewType == RowType.SUGGESTION) {
      return new SuggestionViewHolder(
          inflater.inflate(R.layout.suggestion_row_layout, parent, false), suggestedListener);
    } else if (viewType == RowType.HEADER) {
      return new HeaderViewHolder(
          inflater.inflate(R.layout.speed_dial_header_layout, parent, false), headerListener);
    } else {
      throw Assert.createIllegalStateFailException("Invalid viewType: " + viewType);
    }
  }

  @Override
  public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
    cursor.moveToPosition(position);
    switch (cursor.getRowType(position)) {
      case RowType.HEADER:
        ((HeaderViewHolder) holder).setHeaderText(cursor.getHeader());
        ((HeaderViewHolder) holder).showAddButton(cursor.hasFavorites() && position == 0);
        break;
      case RowType.STARRED:
        ((FavoritesViewHolder) holder).bind(context, cursor);
        break;
      case RowType.SUGGESTION:
        ((SuggestionViewHolder) holder).bind(context, cursor);
        break;
      default:
        throw Assert.createIllegalStateFailException("Invalid view holder: " + holder);
    }
  }

  @Override
  public int getItemCount() {
    return cursor == null || cursor.isClosed() ? 0 : cursor.getCount();
  }

  public void setCursor(SpeedDialCursor cursor) {
    this.cursor = cursor;
    notifyDataSetChanged();
  }

  LayoutManager getLayoutManager(Context context) {
    GridLayoutManager layoutManager = new GridLayoutManager(context, 3 /* spanCount */);
    layoutManager.setSpanSizeLookup(
        new SpanSizeLookup() {
          @Override
          public int getSpanSize(int position) {
            return SpeedDialAdapter.this.getSpanSize(position);
          }
        });
    return layoutManager;
  }

  @VisibleForTesting
  int getSpanSize(int position) {
    switch (cursor.getRowType(position)) {
      case RowType.SUGGESTION:
      case RowType.HEADER:
        return 3; // span the whole screen
      case RowType.STARRED:
        return 1; // span 1/3 of the screen
      default:
        throw Assert.createIllegalStateFailException(
            "Invalid row type: " + cursor.getRowType(position));
    }
  }
}
