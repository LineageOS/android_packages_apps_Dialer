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

import android.support.v7.widget.RecyclerView.ViewHolder;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import com.android.dialer.common.Assert;

/** OnTouchListener for the {@link com.android.dialer.speeddial.FavoritesViewHolder}. */
public class SpeedDialFavoritesViewHolderOnTouchListener implements OnTouchListener {

  private final ViewConfiguration configuration;
  private final ItemTouchHelper itemTouchHelper;
  private final ViewHolder viewHolder;
  private final OnTouchFinishCallback onTouchFinishCallback;

  private boolean hasPerformedLongClick;
  private float startX;
  private float startY;

  public SpeedDialFavoritesViewHolderOnTouchListener(
      ViewConfiguration configuration,
      ItemTouchHelper itemTouchHelper,
      ViewHolder viewHolder,
      OnTouchFinishCallback onTouchFinishCallback) {
    this.configuration = configuration;
    this.itemTouchHelper = itemTouchHelper;
    this.viewHolder = viewHolder;
    this.onTouchFinishCallback = onTouchFinishCallback;
  }

  @Override
  public boolean onTouch(View v, MotionEvent event) {
    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        startX = event.getX();
        startY = event.getY();
        return true;
      case MotionEvent.ACTION_MOVE:
        // If the user has long clicked the view
        if (event.getEventTime() - event.getDownTime() > ViewConfiguration.getLongPressTimeout()) {
          // Perform long click if we haven't already
          if (!hasPerformedLongClick) {
            v.performLongClick();
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            hasPerformedLongClick = true;
          } else if (moveEventExceedsTouchSlop(event)) {
            itemTouchHelper.startDrag(viewHolder);
            onTouchFinishCallback.onTouchFinished(true);
          }
        }
        return true;
      case MotionEvent.ACTION_UP:
        if (event.getEventTime() - event.getDownTime() < ViewConfiguration.getLongPressTimeout()) {
          v.performClick();
        }
        // fallthrough
      case MotionEvent.ACTION_CANCEL:
        hasPerformedLongClick = false;
        onTouchFinishCallback.onTouchFinished(false);
        return true;
      default:
        return false;
    }
  }

  private boolean moveEventExceedsTouchSlop(MotionEvent event) {
    Assert.checkArgument(event.getAction() == MotionEvent.ACTION_MOVE);
    if (event.getHistorySize() <= 0) {
      return false;
    }

    return Math.abs(startX - event.getX()) > configuration.getScaledTouchSlop()
        || Math.abs(startY - event.getY()) > configuration.getScaledTouchSlop();
  }

  /** Callback to listen for on touch events ending. */
  public interface OnTouchFinishCallback {

    /**
     * Called when the user stops touching the view.
     *
     * @see MotionEvent#ACTION_UP
     * @see MotionEvent#ACTION_CANCEL
     */
    void onTouchFinished(boolean closeContextMenu);
  }
}
