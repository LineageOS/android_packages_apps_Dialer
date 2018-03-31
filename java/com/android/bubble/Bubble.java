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

package com.android.bubble;

import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import java.util.List;

/**
 * Creates and manages a bubble window from information in a {@link BubbleInfo}. Before creating, be
 * sure to check whether bubbles may be shown using {@code Settings.canDrawOverlays(context)} and
 * request permission if necessary
 */
public interface Bubble {

  /**
   * Make the bubble visible. Will show a short entrance animation as it enters. If the bubble is
   * already showing this method does nothing.
   */
  void show();

  /** Hide the bubble. */
  void hide();

  /** Returns whether the bubble is currently visible */
  boolean isVisible();

  /** Returns whether the bubble is currently dismissed */
  boolean isDismissed();

  /**
   * Set the info for this Bubble to display
   *
   * @param bubbleInfo the BubbleInfo to display in this Bubble.
   */
  void setBubbleInfo(@NonNull BubbleInfo bubbleInfo);

  /**
   * Update the state and behavior of actions.
   *
   * @param actions the new state of the bubble's actions
   */
  void updateActions(@NonNull List<BubbleInfo.Action> actions);

  /**
   * Update the avatar from photo.
   *
   * @param avatar the new photo avatar in the bubble's primary button
   */
  void updatePhotoAvatar(@NonNull Drawable avatar);

  /**
   * Update the avatar.
   *
   * @param avatar the new avatar in the bubble's primary button
   */
  void updateAvatar(@NonNull Drawable avatar);

  /**
   * Display text. The bubble's drawer is not expandable while text is showing, and the drawer will
   * be closed if already open.
   *
   * @param text the text to display to the user
   */
  void showText(@NonNull CharSequence text);
}
