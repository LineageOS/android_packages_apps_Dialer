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
import android.support.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Interface for promotion bottom sheet. */
public interface Promotion {

  /**
   * Type of promotion, which means promotion should be shown as a card in {@link
   * android.support.v7.widget.RecyclerView} or {@link
   * android.support.design.bottomsheet.BottomSheetBehavior}.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({PromotionType.CARD, PromotionType.BOTTOM_SHEET})
  @interface PromotionType {
    /** Shown as card in call log or voicemail tab. */
    int CARD = 1;

    /** Shown as bottom sheet. */
    int BOTTOM_SHEET = 2;
  }

  /** Returns {@link PromotionType} for this promotion. */
  @PromotionType
  int getType();

  /**
   * Returns if this promotion should be shown. This usually means the promotion is enabled and not
   * dismissed yet.
   */
  boolean isEligibleToBeShown();

  /** Called when this promotion is first time viewed by user. */
  default void onViewed() {}

  /** Dismisses this promotion. This is called when user acknowledged the promotion. */
  void dismiss();

  /** Returns title text of the promotion. */
  CharSequence getTitle();

  /** Returns details text of the promotion. */
  CharSequence getDetails();

  /** Returns resource id of the icon for the promotion. */
  @DrawableRes
  int getIconRes();
}
