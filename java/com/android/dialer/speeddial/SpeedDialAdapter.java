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

import android.content.Context;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager.SpanSizeLookup;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.ArrayMap;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.animation.AnticipateInterpolator;
import android.widget.FrameLayout;
import com.android.dialer.common.Assert;
import com.android.dialer.speeddial.FavoritesViewHolder.FavoriteContactsListener;
import com.android.dialer.speeddial.HeaderViewHolder.SpeedDialHeaderListener;
import com.android.dialer.speeddial.SpeedDialFragment.HostInterface;
import com.android.dialer.speeddial.SuggestionViewHolder.SuggestedContactsListener;
import com.android.dialer.speeddial.draghelper.SpeedDialItemTouchHelperCallback.ItemTouchHelperAdapter;
import com.android.dialer.speeddial.loader.SpeedDialUiItem;
import com.google.common.collect.ImmutableList;
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
public final class SpeedDialAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
    implements ItemTouchHelperAdapter {

  private static final int NON_CONTACT_ITEM_NUMBER_BEFORE_FAVORITES = 2;
  private static final int NON_CONTACT_ITEM_NUMBER_BEFORE_SUGGESTION = 3;

  private static final float IN_REMOVE_VIEW_SCALE = 0.5f;
  private static final float IN_REMOVE_VIEW_ALPHA = 0.5f;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({RowType.STARRED_HEADER, RowType.SUGGESTION_HEADER, RowType.STARRED, RowType.SUGGESTION})
  @interface RowType {
    int REMOVE_VIEW = 0;
    int STARRED_HEADER = 1;
    int SUGGESTION_HEADER = 2;
    int STARRED = 3;
    int SUGGESTION = 4;
  }

  private final Context context;
  private final FavoriteContactsListener favoritesListener;
  private final SuggestedContactsListener suggestedListener;
  private final SpeedDialHeaderListener headerListener;
  private final HostInterface hostInterface;

  private final Map<Integer, Integer> positionToRowTypeMap = new ArrayMap<>();
  private List<SpeedDialUiItem> speedDialUiItems;

  // Needed for FavoriteViewHolder
  private ItemTouchHelper itemTouchHelper;

  private RemoveViewHolder removeViewHolder;
  private FavoritesViewHolder draggingFavoritesViewHolder;

  public SpeedDialAdapter(
      Context context,
      FavoriteContactsListener favoritesListener,
      SuggestedContactsListener suggestedListener,
      SpeedDialHeaderListener headerListener,
      HostInterface hostInterface) {
    this.context = context;
    this.favoritesListener = favoritesListener;
    this.suggestedListener = suggestedListener;
    this.headerListener = headerListener;
    this.hostInterface = hostInterface;
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
            inflater.inflate(R.layout.favorite_item_layout, parent, false),
            itemTouchHelper,
            favoritesListener);
      case RowType.SUGGESTION:
        return new SuggestionViewHolder(
            inflater.inflate(R.layout.suggestion_row_layout, parent, false), suggestedListener);
      case RowType.STARRED_HEADER:
      case RowType.SUGGESTION_HEADER:
        return new HeaderViewHolder(
            inflater.inflate(R.layout.speed_dial_header_layout, parent, false), headerListener);
      case RowType.REMOVE_VIEW:
        removeViewHolder =
            new RemoveViewHolder(
                inflater.inflate(R.layout.favorite_remove_view_layout, parent, false));
        return removeViewHolder;
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
        ((FavoritesViewHolder) holder).bind(context, speedDialUiItems.get(position - 2));
        // Removed item might come back
        FrameLayout avatarContainer = ((FavoritesViewHolder) holder).getAvatarContainer();
        avatarContainer.setScaleX(1);
        avatarContainer.setScaleY(1);
        avatarContainer.setAlpha(1);
        break;
      case RowType.SUGGESTION:
        ((SuggestionViewHolder) holder).bind(context, speedDialUiItems.get(position - 3));
        break;
      case RowType.REMOVE_VIEW:
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
    speedDialUiItems.sort(
        (o1, o2) -> {
          if (o1.isStarred() && o2.isStarred()) {
            return Integer.compare(o1.pinnedPosition().or(-1), o2.pinnedPosition().or(-1));
          }
          return Boolean.compare(o2.isStarred(), o1.isStarred());
        });
    updatePositionToRowTypeMap();
  }

  private void updatePositionToRowTypeMap() {
    positionToRowTypeMap.clear();
    if (speedDialUiItems.isEmpty()) {
      return;
    }

    positionToRowTypeMap.put(0, RowType.REMOVE_VIEW);
    // Show the add favorites even if there are no favorite contacts
    positionToRowTypeMap.put(1, RowType.STARRED_HEADER);
    int positionOfSuggestionHeader = NON_CONTACT_ITEM_NUMBER_BEFORE_FAVORITES;
    for (int i = 0; i < speedDialUiItems.size(); i++) {
      if (speedDialUiItems.get(i).isStarred()) {
        positionToRowTypeMap.put(i + NON_CONTACT_ITEM_NUMBER_BEFORE_FAVORITES, RowType.STARRED);
        positionOfSuggestionHeader++;
      } else {
        positionToRowTypeMap.put(i + NON_CONTACT_ITEM_NUMBER_BEFORE_SUGGESTION, RowType.SUGGESTION);
      }
    }
    if (!speedDialUiItems.get(speedDialUiItems.size() - 1).isStarred()) {
      positionToRowTypeMap.put(positionOfSuggestionHeader, RowType.SUGGESTION_HEADER);
    }
  }

  public ImmutableList<SpeedDialUiItem> getSpeedDialUiItems() {
    if (speedDialUiItems == null || speedDialUiItems.isEmpty()) {
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(speedDialUiItems);
  }

  public SpanSizeLookup getSpanSizeLookup() {
    return new SpanSizeLookup() {
      @Override
      public int getSpanSize(int position) {
        switch (getItemViewType(position)) {
          case RowType.SUGGESTION:
          case RowType.STARRED_HEADER:
          case RowType.SUGGESTION_HEADER:
          case RowType.REMOVE_VIEW:
            return 3; // span the whole screen
          case RowType.STARRED:
            return 1; // span 1/3 of the screen
          default:
            throw Assert.createIllegalStateFailException(
                "Invalid row type: " + positionToRowTypeMap.get(position));
        }
      }
    };
  }

  @Override
  public void onItemMove(int fromPosition, int toPosition) {
    if (toPosition == 0) {
      // drop to removeView
      return;
    }
    // fromPosition/toPosition correspond to adapter position, which is off by 1 from the list
    // position b/c of the favorites header. So subtract 1 here.
    speedDialUiItems.add(toPosition - 2, speedDialUiItems.remove(fromPosition - 2));
    notifyItemMoved(fromPosition, toPosition);
  }

  @Override
  public boolean canDropOver(ViewHolder target) {
    return target instanceof FavoritesViewHolder || target instanceof RemoveViewHolder;
  }

  @Override
  public void onSelectedChanged(@Nullable ViewHolder viewHolder, int actionState) {
    switch (actionState) {
      case ItemTouchHelper.ACTION_STATE_DRAG:
        if (viewHolder != null) {
          draggingFavoritesViewHolder = (FavoritesViewHolder) viewHolder;
          draggingFavoritesViewHolder.onSelectedChanged(true);
          hostInterface.dragFavorite(true);
          removeViewHolder.show();
        }
        break;
      case ItemTouchHelper.ACTION_STATE_IDLE:
        // viewHolder is null in this case
        if (draggingFavoritesViewHolder != null) {
          draggingFavoritesViewHolder.onSelectedChanged(false);
          draggingFavoritesViewHolder = null;
          hostInterface.dragFavorite(false);
          removeViewHolder.hide();
        }
        break;
      default:
        break;
    }
  }

  @Override
  public void enterRemoveView() {
    if (draggingFavoritesViewHolder != null) {
      draggingFavoritesViewHolder
          .getAvatarContainer()
          .animate()
          .scaleX(IN_REMOVE_VIEW_SCALE)
          .scaleY(IN_REMOVE_VIEW_SCALE)
          .alpha(IN_REMOVE_VIEW_ALPHA)
          .start();
    }
  }

  @Override
  public void leaveRemoveView() {
    if (draggingFavoritesViewHolder != null) {
      draggingFavoritesViewHolder
          .getAvatarContainer()
          .animate()
          .scaleX(1)
          .scaleY(1)
          .alpha(1)
          .start();
    }
  }

  @Override
  public void dropOnRemoveView(ViewHolder fromViewHolder) {
    if (!(fromViewHolder instanceof FavoritesViewHolder)) {
      return;
    }
    int fromPosition = fromViewHolder.getAdapterPosition();

    SpeedDialUiItem removedItem = speedDialUiItems.remove(fromPosition - 2);
    favoritesListener.onRequestRemove(removedItem);
    ((FavoritesViewHolder) fromViewHolder)
        .getAvatarContainer()
        .animate()
        .scaleX(0)
        .scaleY(0)
        .alpha(0)
        .setInterpolator(new AnticipateInterpolator())
        .start();
    updatePositionToRowTypeMap();
  }

  public void setItemTouchHelper(ItemTouchHelper itemTouchHelper) {
    this.itemTouchHelper = itemTouchHelper;
  }

  /** Returns true if there are suggested contacts. */
  public boolean hasFrequents() {
    return !speedDialUiItems.isEmpty() && getItemViewType(getItemCount() - 1) == RowType.SUGGESTION;
  }
}
