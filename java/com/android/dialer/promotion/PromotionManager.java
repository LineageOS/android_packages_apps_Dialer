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

import com.android.dialer.promotion.Promotion.PromotionType;
import com.google.common.collect.ImmutableList;
import java.util.Optional;
import javax.inject.Inject;

/**
 * A class to manage all promotion cards/bottom sheet.
 *
 * <p>Only one promotion with highest priority will be shown at a time no matter type. So if there
 * are one card and one bottom sheet promotion, either one will be shown instead of both.
 */
public final class PromotionManager {

  /** Promotion priority order list. Promotions with higher priority must be added first. */
  private ImmutableList<Promotion> priorityPromotionList;

  @Inject
  public PromotionManager(ImmutableList<Promotion> priorityPromotionList) {
    this.priorityPromotionList = priorityPromotionList;
  }

  /**
   * Returns promotion should show with highest priority. {@link Optional#empty()} if no promotion
   * should be shown with given {@link PromotionType}.
   *
   * <p>e.g. if FooPromotion(card, high priority) and BarPromotion(bottom sheet, low priority) are
   * both enabled, getHighestPriorityPromotion(CARD) returns Optional.of(FooPromotion) but
   * getHighestPriorityPromotion(BOTTOM_SHEET) returns {@link Optional#empty()}.
   *
   * <p>Currently it only supports promotion in call log tab.
   *
   * <p>TODO(wangqi): add support for other tabs.
   */
  public Optional<Promotion> getHighestPriorityPromotion(@PromotionType int type) {
    for (Promotion promotion : priorityPromotionList) {
      if (promotion.isEligibleToBeShown()) {
        if (promotion.getType() == type) {
          return Optional.of(promotion);
        } else {
          // Returns empty promotion since it's not the type looking for and only one promotion
          // should be shown at a time.
          return Optional.empty();
        }
      }
    }
    return Optional.empty();
  }
}
