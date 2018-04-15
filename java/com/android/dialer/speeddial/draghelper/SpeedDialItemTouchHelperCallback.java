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
 * limitations under the License.
 */

package com.android.dialer.speeddial.draghelper;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.support.v7.widget.helper.ItemTouchHelper;

/** {@link ItemTouchHelper} for Speed Dial favorite contacts. */
public class SpeedDialItemTouchHelperCallback extends ItemTouchHelper.Callback {

  private final ItemTouchHelperAdapter adapter;

  public SpeedDialItemTouchHelperCallback(ItemTouchHelperAdapter adapter) {
    this.adapter = adapter;
  }

  @Override
  public boolean isLongPressDragEnabled() {
    // We'll manually call ItemTouchHelper#startDrag
    return false;
  }

  @Override
  public boolean isItemViewSwipeEnabled() {
    // We don't want to enable swiping
    return false;
  }

  @Override
  public boolean canDropOver(
      @NonNull RecyclerView recyclerView, @NonNull ViewHolder current, @NonNull ViewHolder target) {
    return adapter.canDropOver(target);
  }

  @Override
  public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull ViewHolder viewHolder) {
    if (!adapter.canDropOver(viewHolder)) {
      return makeMovementFlags(0, 0);
    }

    int dragFlags =
        ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.START | ItemTouchHelper.END;
    return makeMovementFlags(dragFlags, /* swipeFlags */ 0);
  }

  @Override
  public boolean onMove(
      @NonNull RecyclerView recyclerView,
      @NonNull ViewHolder viewHolder,
      @NonNull ViewHolder target) {
    adapter.onItemMove(viewHolder.getAdapterPosition(), target.getAdapterPosition());
    return true;
  }

  @Override
  public void onSwiped(@NonNull ViewHolder viewHolder, int direction) {
    // No-op since we don't support swiping
  }

  /** RecyclerView adapters interested in drag and drop should implement this interface. */
  public interface ItemTouchHelperAdapter {

    void onItemMove(int fromPosition, int toPosition);

    boolean canDropOver(ViewHolder target);
  }
}
