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

package com.android.dialer.compat.android.support.design.bottomsheet;

import android.support.design.widget.BottomSheetBehavior;

/** Provides access to bottom sheet states. */
public final class BottomSheetStateCompat {

  /** The bottom sheet is dragging. */
  public static final int STATE_DRAGGING = BottomSheetBehavior.STATE_DRAGGING;

  /** The bottom sheet is settling. */
  public static final int STATE_SETTLING = BottomSheetBehavior.STATE_SETTLING;

  /** The bottom sheet is expanded. */
  public static final int STATE_EXPANDED = BottomSheetBehavior.STATE_EXPANDED;

  /** The bottom sheet is collapsed. */
  public static final int STATE_COLLAPSED = BottomSheetBehavior.STATE_COLLAPSED;

  /** The bottom sheet is hidden. */
  public static final int STATE_HIDDEN = BottomSheetBehavior.STATE_HIDDEN;

  /** The bottom sheet is half-expanded (not public yet). */
  public static final int STATE_HALF_EXPANDED = 6;

  private BottomSheetStateCompat() {}
}
