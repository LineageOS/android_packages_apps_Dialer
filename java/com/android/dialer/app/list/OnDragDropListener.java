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

/**
 * Classes that want to receive callbacks in response to drag events should implement this
 * interface.
 */
public interface OnDragDropListener {

  /**
   * Called when a drag is started.
   *
   * @param x X-coordinate of the drag event
   * @param y Y-coordinate of the drag event
   * @param view The contact tile which the drag was started on
   */
  void onDragStarted(int x, int y, PhoneFavoriteSquareTileView view);

  /**
   * Called when a drag is in progress and the user moves the dragged contact to a location.
   *
   * @param x X-coordinate of the drag event
   * @param y Y-coordinate of the drag event
   * @param view Contact tile in the ListView which is currently being displaced by the dragged
   *     contact
   */
  void onDragHovered(int x, int y, PhoneFavoriteSquareTileView view);

  /**
   * Called when a drag is completed (whether by dropping it somewhere or simply by dragging the
   * contact off the screen)
   *
   * @param x X-coordinate of the drag event
   * @param y Y-coordinate of the drag event
   */
  void onDragFinished(int x, int y);

  /**
   * Called when a contact has been dropped on the remove view, indicating that the user wants to
   * remove this contact.
   */
  void onDroppedOnRemove();
}
