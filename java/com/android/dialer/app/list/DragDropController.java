/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.app.list;

import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/**
 * Class that handles and combines drag events generated from multiple views, and then fires off
 * events to any OnDragDropListeners that have registered for callbacks.
 */
public class DragDropController {

  private final List<OnDragDropListener> mOnDragDropListeners = new ArrayList<OnDragDropListener>();
  private final DragItemContainer mDragItemContainer;
  private final int[] mLocationOnScreen = new int[2];

  public DragDropController(DragItemContainer dragItemContainer) {
    mDragItemContainer = dragItemContainer;
  }

  /** @return True if the drag is started, false if the drag is cancelled for some reason. */
  boolean handleDragStarted(View v, int x, int y) {
    int screenX = x;
    int screenY = y;
    // The coordinates in dragEvent of DragEvent.ACTION_DRAG_STARTED before NYC is window-related.
    // This is fixed in NYC.
    if (VERSION.SDK_INT >= VERSION_CODES.N) {
      v.getLocationOnScreen(mLocationOnScreen);
      screenX = x + mLocationOnScreen[0];
      screenY = y + mLocationOnScreen[1];
    }
    final PhoneFavoriteSquareTileView tileView =
        mDragItemContainer.getViewForLocation(screenX, screenY);
    if (tileView == null) {
      return false;
    }
    for (int i = 0; i < mOnDragDropListeners.size(); i++) {
      mOnDragDropListeners.get(i).onDragStarted(screenX, screenY, tileView);
    }

    return true;
  }

  public void handleDragHovered(View v, int x, int y) {
    v.getLocationOnScreen(mLocationOnScreen);
    final int screenX = x + mLocationOnScreen[0];
    final int screenY = y + mLocationOnScreen[1];
    final PhoneFavoriteSquareTileView view =
        mDragItemContainer.getViewForLocation(screenX, screenY);
    for (int i = 0; i < mOnDragDropListeners.size(); i++) {
      mOnDragDropListeners.get(i).onDragHovered(screenX, screenY, view);
    }
  }

  public void handleDragFinished(int x, int y, boolean isRemoveView) {
    if (isRemoveView) {
      for (int i = 0; i < mOnDragDropListeners.size(); i++) {
        mOnDragDropListeners.get(i).onDroppedOnRemove();
      }
    }

    for (int i = 0; i < mOnDragDropListeners.size(); i++) {
      mOnDragDropListeners.get(i).onDragFinished(x, y);
    }
  }

  public void addOnDragDropListener(OnDragDropListener listener) {
    if (!mOnDragDropListeners.contains(listener)) {
      mOnDragDropListeners.add(listener);
    }
  }

  public void removeOnDragDropListener(OnDragDropListener listener) {
    if (mOnDragDropListeners.contains(listener)) {
      mOnDragDropListeners.remove(listener);
    }
  }

  /**
   * Callback interface used to retrieve views based on the current touch coordinates of the drag
   * event. The {@link DragItemContainer} houses the draggable views that this {@link
   * DragDropController} controls.
   */
  public interface DragItemContainer {

    PhoneFavoriteSquareTileView getViewForLocation(int x, int y);
  }
}
