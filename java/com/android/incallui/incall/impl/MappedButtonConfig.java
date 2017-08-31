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
import android.support.v4.util.ArrayMap;
import android.util.ArraySet;
import com.android.dialer.common.Assert;
import com.android.incallui.incall.protocol.InCallButtonIds;
import com.android.incallui.incall.protocol.InCallButtonIdsExtension;
import com.google.auto.value.AutoValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.concurrent.Immutable;

/**
 * Determines logical button slot and ordering based on a provided mapping.
 *
 * <p>The provided mapping is declared with the following pieces of information: key, the {@link
 * InCallButtonIds} for which the mapping applies; {@link MappingInfo#getSlot()}, the arbitrarily
 * indexed slot into which the InCallButtonId desires to be placed; {@link
 * MappingInfo#getSlotOrder()}, the slotOrder, used to choose the correct InCallButtonId when
 * multiple desire to be placed in the same slot; and {@link MappingInfo#getConflictOrder()}, the
 * conflictOrder, used to determine the overall order for InCallButtonIds that weren't chosen for
 * their desired slot.
 */
@Immutable
final class MappedButtonConfig {

  @NonNull private final Map<Integer, MappingInfo> mapping;
  @NonNull private final List<Integer> orderedMappedSlots;

  /**
   * Creates this MappedButtonConfig with the given mapping of {@link InCallButtonIds} to their
   * corresponding slots and order.
   *
   * @param mapping the mapping.
   */
  public MappedButtonConfig(@NonNull Map<Integer, MappingInfo> mapping) {
    this.mapping = new ArrayMap<>();
    this.mapping.putAll(Assert.isNotNull(mapping));
    this.orderedMappedSlots = findOrderedMappedSlots();
  }

  private List<Integer> findOrderedMappedSlots() {
    Set<Integer> slots = new ArraySet<>();
    for (Entry<Integer, MappingInfo> entry : mapping.entrySet()) {
      slots.add(entry.getValue().getSlot());
    }
    List<Integer> orderedSlots = new ArrayList<>(slots);
    Collections.sort(orderedSlots);
    return orderedSlots;
  }

  /** Returns an immutable list of the slots for which this class has button mapping. */
  @NonNull
  public List<Integer> getOrderedMappedSlots() {
    if (mapping.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(orderedMappedSlots);
  }

  /**
   * Returns a list of {@link InCallButtonIds} that are configured to be placed in the given ui
   * slot. The slot can be based from any index, as long as it matches the provided mapping.
   */
  @NonNull
  public List<Integer> getButtonsForSlot(int slot) {
    List<Integer> buttons = new ArrayList<>();
    for (Entry<Integer, MappingInfo> entry : mapping.entrySet()) {
      if (entry.getValue().getSlot() == slot) {
        buttons.add(entry.getKey());
      }
    }
    return buttons;
  }

  /**
   * Returns a {@link Comparator} capable of ordering {@link InCallButtonIds} that are configured to
   * be placed in the same slot. InCallButtonIds are sorted based on the natural ordering of {@link
   * MappingInfo#getSlotOrder()}.
   *
   * <p>Note: the returned Comparator's compare method will throw an {@link
   * IllegalArgumentException} if called with InCallButtonIds that have no configuration or are not
   * to be placed in the same slot.
   */
  @NonNull
  public Comparator<Integer> getSlotComparator() {
    return new Comparator<Integer>() {
      @Override
      public int compare(Integer lhs, Integer rhs) {
        MappingInfo lhsInfo = lookupMappingInfo(lhs);
        MappingInfo rhsInfo = lookupMappingInfo(rhs);
        if (lhsInfo.getSlot() != rhsInfo.getSlot()) {
          throw new IllegalArgumentException("lhs and rhs don't go in the same slot");
        }
        return lhsInfo.getSlotOrder() - rhsInfo.getSlotOrder();
      }
    };
  }

  /**
   * Returns a {@link Comparator} capable of ordering {@link InCallButtonIds} by their conflict
   * score. This comparator should be used when multiple InCallButtonIds could have been shown in
   * the same slot. InCallButtonIds are sorted based on the natural ordering of {@link
   * MappingInfo#getConflictOrder()}.
   *
   * <p>Note: the returned Comparator's compare method will throw an {@link
   * IllegalArgumentException} if called with InCallButtonIds that have no configuration.
   */
  @NonNull
  public Comparator<Integer> getConflictComparator() {
    return new Comparator<Integer>() {
      @Override
      public int compare(Integer lhs, Integer rhs) {
        MappingInfo lhsInfo = lookupMappingInfo(lhs);
        MappingInfo rhsInfo = lookupMappingInfo(rhs);
        return lhsInfo.getConflictOrder() - rhsInfo.getConflictOrder();
      }
    };
  }

  @NonNull
  public MappingInfo lookupMappingInfo(@InCallButtonIds int button) {
    MappingInfo info = mapping.get(button);
    if (info == null) {
      throw new IllegalArgumentException(
          "Unknown InCallButtonId: " + InCallButtonIdsExtension.toString(button));
    }
    return info;
  }

  /** Holds information about button mapping. */
  @AutoValue
  abstract static class MappingInfo {

    public static final int NO_MUTUALLY_EXCLUSIVE_BUTTON_SET = -1;

    /** The Ui slot into which a given button desires to be placed. */
    public abstract int getSlot();

    /**
     * Returns an integer used to determine which button is chosen for a slot when multiple buttons
     * desire to be placed in the same slot. Follows from the natural ordering of integers, i.e. a
     * lower slotOrder results in the button being chosen.
     */
    public abstract int getSlotOrder();

    /**
     * Returns an integer used to determine the order in which buttons that weren't chosen for their
     * desired slot are placed into the Ui. Follows from the natural ordering of integers, i.e. a
     * lower conflictOrder results in the button being chosen.
     */
    public abstract int getConflictOrder();

    /**
     * Returns an integer representing a button for which the given button conflicts. Defaults to
     * {@link NO_MUTUALLY_EXCLUSIVE_BUTTON_SET}.
     *
     * <p>If the mutually exclusive button is chosen, the associated button should never be chosen.
     */
    public abstract @InCallButtonIds int getMutuallyExclusiveButton();

    static Builder builder(int slot) {
      return new AutoValue_MappedButtonConfig_MappingInfo.Builder()
          .setSlot(slot)
          .setSlotOrder(Integer.MAX_VALUE)
          .setConflictOrder(Integer.MAX_VALUE)
          .setMutuallyExclusiveButton(NO_MUTUALLY_EXCLUSIVE_BUTTON_SET);
    }

    /** Class used to build instances of {@link MappingInfo}. */
    @AutoValue.Builder
    abstract static class Builder {
      public abstract Builder setSlot(int slot);

      public abstract Builder setSlotOrder(int slotOrder);

      public abstract Builder setConflictOrder(int conflictOrder);

      public abstract Builder setMutuallyExclusiveButton(@InCallButtonIds int button);

      public abstract MappingInfo build();
    }
  }
}
