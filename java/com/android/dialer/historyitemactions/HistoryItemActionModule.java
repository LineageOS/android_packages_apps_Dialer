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
 * limitations under the License.
 */

package com.android.dialer.historyitemactions;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

/**
 * Modules used to build {@link HistoryItemActionBottomSheet}.
 *
 * <p>A history item is one that is displayed in the call log or the voicemail fragment.
 */
public interface HistoryItemActionModule {

  @StringRes
  int getStringId();

  @DrawableRes
  int getDrawableId();

  /** Returns true if tint can be applied to the drawable. */
  default boolean tintDrawable() {
    return true;
  }

  /** @return true if the bottom sheet should close, false otherwise */
  boolean onClick();
}
