/*
 * Copyright (C) 2018 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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

import android.content.Context;
import android.graphics.Canvas;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

/** {@link ItemTouchHelper} for Speed Dial favorite contacts. */
public class SpeedDialItemTouchHelperCallback extends ItemTouchHelper.Callback {

  private final ItemTouchHelperAdapter adapter;
  private final Context context;

  // When dragged item is in removeView, onMove() and onChildDraw() are called in turn. This
  // behavior changes when dragged item entering/leaving removeView. The boolean field
  // movedOverRemoveView is for onMove() and onChildDraw() to flip.
  private boolean movedOverRemoveView;
  private boolean inRemoveView;

  public SpeedDialItemTouchHelperCallback(Context context, ItemTouchHelperAdapter adapter) {
    this.context = context;
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
    if (target.getItemViewType() == 0) { // 0 for RowType.REMOVE_VIEW
      movedOverRemoveView = true;
      if (!inRemoveView) {
        // onMove() first called
        adapter.enterRemoveView();
        inRemoveView = true;
      }
      return false;
    } else if (inRemoveView) {
      // Move out of removeView fast
      inRemoveView = false;
      movedOverRemoveView = false;
      adapter.leaveRemoveView();
    }
    adapter.onItemMove(viewHolder.getAdapterPosition(), target.getAdapterPosition());
    return true;
  }

  @Override
  public void onMoved(
      @NonNull RecyclerView recyclerView,
      @NonNull ViewHolder viewHolder,
      int fromPos,
      @NonNull ViewHolder viewHolder1,
      int toPos,
      int x,
      int y) {
    super.onMoved(recyclerView, viewHolder, fromPos, viewHolder1, toPos, x, y);
  }

  @Override
  public void onChildDraw(
      @NonNull Canvas canvas,
      @NonNull RecyclerView recyclerView,
      @NonNull ViewHolder viewHolder,
      float dx,
      float dy,
      int i,
      boolean isCurrentlyActive) {
    if (inRemoveView) {
      if (!isCurrentlyActive) {
        // View animating back to its original state, which means drop in this case
        inRemoveView = false;
        adapter.dropOnRemoveView(viewHolder);
      }
      if (!movedOverRemoveView) {
        // when the view is over a droppable target, onMove() will be called before onChildDraw()
        // thus if onMove() is not called, it is not over a droppable target.
        inRemoveView = false;
        adapter.leaveRemoveView();
      }
    }
    movedOverRemoveView = false;
    super.onChildDraw(canvas, recyclerView, viewHolder, dx, dy, i, isCurrentlyActive);
  }

  @Override
  public void onSelectedChanged(@Nullable ViewHolder viewHolder, int actionState) {
    super.onSelectedChanged(viewHolder, actionState);
    adapter.onSelectedChanged(viewHolder, actionState);
  }

  @Override
  public void onSwiped(@NonNull ViewHolder viewHolder, int direction) {
    // No-op since we don't support swiping
  }

  /** RecyclerView adapters interested in drag and drop should implement this interface. */
  public interface ItemTouchHelperAdapter {

    void onItemMove(int fromPosition, int toPosition);

    boolean canDropOver(ViewHolder target);

    void onSelectedChanged(@Nullable ViewHolder viewHolder, int actionState);

    void enterRemoveView();

    void leaveRemoveView();

    void dropOnRemoveView(ViewHolder fromViewHolder);
  }
}
