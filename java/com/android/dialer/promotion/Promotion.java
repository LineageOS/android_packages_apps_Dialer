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

package com.android.dialer.promotion;

import android.support.annotation.DrawableRes;

/** Interface for promotion bottom sheet. */
public interface Promotion {

  /** Returns if this promotion should be shown. */
  boolean shouldShow();

  /** Sets to show this promotion. */
  void setShouldShow(boolean shouldShow);

  /** Dismisses this promotion. This is called when user acknowledged the promotion. */
  void dismiss();

  CharSequence getTitle();

  CharSequence getDetails();

  @DrawableRes
  int getIconRes();
}
