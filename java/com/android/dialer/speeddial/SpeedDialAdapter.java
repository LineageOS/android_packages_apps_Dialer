/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.GridLayoutManager.SpanSizeLookup;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.LayoutManager;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.util.ArrayMap;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import com.android.dialer.common.Assert;
import com.android.dialer.speeddial.FavoritesViewHolder.FavoriteContactsListener;
import com.android.dialer.speeddial.HeaderViewHolder.SpeedDialHeaderListener;
import com.android.dialer.speeddial.SuggestionViewHolder.SuggestedContactsListener;
import com.android.dialer.speeddial.loader.SpeedDialUiItem;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
@SuppressWarnings("AndroidApiChecker")
@TargetApi(VERSION_CODES.N)
public final class SpeedDialAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({RowType.STARRED_HEADER, RowType.SUGGESTION_HEADER, RowType.STARRED, RowType.SUGGESTION})
  @interface RowType {
    int STARRED_HEADER = 0;
    int SUGGESTION_HEADER = 1;
    int STARRED = 2;
    int SUGGESTION = 3;
  }

  private final Context context;
  private final FavoriteContactsListener favoritesListener;
  private final SuggestedContactsListener suggestedListener;
  private final SpeedDialHeaderListener headerListener;

  private final Map<Integer, Integer> positionToRowTypeMap = new ArrayMap<>();
  private List<SpeedDialUiItem> speedDialUiItems;

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
    return positionToRowTypeMap.get(position);
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(context);
    switch (viewType) {
      case RowType.STARRED:
        return new FavoritesViewHolder(
            inflater.inflate(R.layout.favorite_item_layout, parent, false), favoritesListener);
      case RowType.SUGGESTION:
        return new SuggestionViewHolder(
            inflater.inflate(R.layout.suggestion_row_layout, parent, false), suggestedListener);
      case RowType.STARRED_HEADER:
      case RowType.SUGGESTION_HEADER:
        return new HeaderViewHolder(
            inflater.inflate(R.layout.speed_dial_header_layout, parent, false), headerListener);
      default:
        throw Assert.createIllegalStateFailException("Invalid viewType: " + viewType);
    }
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    switch (getItemViewType(position)) {
      case RowType.STARRED_HEADER:
        ((HeaderViewHolder) holder).setHeaderText(R.string.favorites_header);
        ((HeaderViewHolder) holder).showAddButton(true);
        return;
      case RowType.SUGGESTION_HEADER:
        ((HeaderViewHolder) holder).setHeaderText(R.string.suggestions_header);
        ((HeaderViewHolder) holder).showAddButton(false);
        return;
      case RowType.STARRED:
        ((FavoritesViewHolder) holder).bind(context, speedDialUiItems.get(position - 1));
        break;
      case RowType.SUGGESTION:
        ((SuggestionViewHolder) holder).bind(context, speedDialUiItems.get(position - 2));
        break;
      default:
        throw Assert.createIllegalStateFailException("Invalid view holder: " + holder);
    }
  }

  @Override
  public int getItemCount() {
    return positionToRowTypeMap.size();
  }

  public void setSpeedDialUiItems(List<SpeedDialUiItem> immutableSpeedDialUiItems) {
    speedDialUiItems = new ArrayList<>();
    speedDialUiItems.addAll(immutableSpeedDialUiItems);
    speedDialUiItems.sort((o1, o2) -> Boolean.compare(o2.isStarred(), o1.isStarred()));
    positionToRowTypeMap.clear();
    if (speedDialUiItems.isEmpty()) {
      return;
    }

    // Show the add favorites even if there are no favorite contacts
    positionToRowTypeMap.put(0, RowType.STARRED_HEADER);
    int positionOfSuggestionHeader = 1;
    for (int i = 0; i < speedDialUiItems.size(); i++) {
      if (speedDialUiItems.get(i).isStarred()) {
        positionToRowTypeMap.put(i + 1, RowType.STARRED); // +1 for the header
        positionOfSuggestionHeader++;
      } else {
        positionToRowTypeMap.put(i + 2, RowType.SUGGESTION); // +2 for both headers
      }
    }
    if (!speedDialUiItems.get(speedDialUiItems.size() - 1).isStarred()) {
      positionToRowTypeMap.put(positionOfSuggestionHeader, RowType.SUGGESTION_HEADER);
    }
  }

  /* package-private */ LayoutManager getLayoutManager(Context context) {
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
    switch (getItemViewType(position)) {
      case RowType.SUGGESTION:
      case RowType.STARRED_HEADER:
      case RowType.SUGGESTION_HEADER:
        return 3; // span the whole screen
      case RowType.STARRED:
        return 1; // span 1/3 of the screen
      default:
        throw Assert.createIllegalStateFailException(
            "Invalid row type: " + positionToRowTypeMap.get(position));
    }
  }
}
