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

package com.android.incallui.incall.impl;

import android.support.annotation.NonNull;
import com.android.dialer.common.Assert;
import com.android.incallui.incall.impl.MappedButtonConfig.MappingInfo;
import com.android.incallui.incall.protocol.InCallButtonIds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.concurrent.Immutable;

/**
 * Determines where logical buttons should be placed in the {@link InCallFragment} based on the
 * provided mapping.
 *
 * <p>The button placement returned by a call to {@link #getButtonPlacement(int, Set)} is created as
 * follows: one button is placed at each UI slot, using the provided mapping to resolve conflicts.
 * Any allowed buttons that were not chosen for their desired slot are filled in at the end of the
 * list until it becomes the proper size.
 */
@Immutable
final class ButtonChooser {

  private final MappedButtonConfig config;

  public ButtonChooser(@NonNull MappedButtonConfig config) {
    this.config = Assert.isNotNull(config);
  }

  /**
   * Returns the buttons that should be shown in the {@link InCallFragment}, ordered appropriately.
   *
   * @param numUiButtons the number of ui buttons available.
   * @param allowedButtons the {@link InCallButtonIds} that can be shown.
   * @param disabledButtons the {@link InCallButtonIds} that can be shown but in disabled stats.
   * @return an immutable list whose size is at most {@code numUiButtons}, containing the buttons to
   *     show.
   */
  @NonNull
  public List<Integer> getButtonPlacement(
      int numUiButtons,
      @NonNull Set<Integer> allowedButtons,
      @NonNull Set<Integer> disabledButtons) {
    Assert.isNotNull(allowedButtons);
    Assert.checkArgument(numUiButtons >= 0);

    if (numUiButtons == 0 || allowedButtons.isEmpty()) {
      return Collections.emptyList();
    }

    List<Integer> placedButtons = new ArrayList<>();
    List<Integer> conflicts = new ArrayList<>();
    placeButtonsInSlots(numUiButtons, allowedButtons, placedButtons, conflicts);
    placeConflictsInOpenSlots(
        numUiButtons, allowedButtons, disabledButtons, placedButtons, conflicts);
    return Collections.unmodifiableList(placedButtons);
  }

  private void placeButtonsInSlots(
      int numUiButtons,
      @NonNull Set<Integer> allowedButtons,
      @NonNull List<Integer> placedButtons,
      @NonNull List<Integer> conflicts) {
    List<Integer> configuredSlots = config.getOrderedMappedSlots();
    for (int i = 0; i < configuredSlots.size() && placedButtons.size() < numUiButtons; ++i) {
      int slotNumber = configuredSlots.get(i);
      List<Integer> potentialButtons = config.getButtonsForSlot(slotNumber);
      Collections.sort(potentialButtons, config.getSlotComparator());
      for (int j = 0; j < potentialButtons.size(); ++j) {
        if (allowedButtons.contains(potentialButtons.get(j))) {
          placedButtons.add(potentialButtons.get(j));
          conflicts.addAll(potentialButtons.subList(j + 1, potentialButtons.size()));
          break;
        }
      }
    }
  }

  private void placeConflictsInOpenSlots(
      int numUiButtons,
      @NonNull Set<Integer> allowedButtons,
      @NonNull Set<Integer> disabledButtons,
      @NonNull List<Integer> placedButtons,
      @NonNull List<Integer> conflicts) {
    Collections.sort(conflicts, config.getConflictComparator());
    for (Integer conflict : conflicts) {
      if (placedButtons.size() >= numUiButtons) {
        return;
      }

      // If the conflict button is allowed but disabled, don't place it since it probably will
      // move when it's enabled.
      if (!allowedButtons.contains(conflict) || disabledButtons.contains(conflict)) {
        continue;
      }

      if (isMutuallyExclusiveButtonAvailable(
          config.lookupMappingInfo(conflict).getMutuallyExclusiveButton(), allowedButtons)) {
        continue;
      }
      placedButtons.add(conflict);
    }
  }

  private boolean isMutuallyExclusiveButtonAvailable(
      int mutuallyExclusiveButton, @NonNull Set<Integer> allowedButtons) {
    if (mutuallyExclusiveButton == MappingInfo.NO_MUTUALLY_EXCLUSIVE_BUTTON_SET) {
      return false;
    }
    if (allowedButtons.contains(mutuallyExclusiveButton)) {
      return true;
    }
    return false;
  }
}
