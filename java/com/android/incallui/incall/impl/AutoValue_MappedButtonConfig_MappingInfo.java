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
 * limitations under the License
 */

package com.android.incallui.incall.impl;

import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
 final class AutoValue_MappedButtonConfig_MappingInfo extends MappedButtonConfig.MappingInfo {

  private final int slot;
  private final int slotOrder;
  private final int conflictOrder;

  private AutoValue_MappedButtonConfig_MappingInfo(
      int slot,
      int slotOrder,
      int conflictOrder) {
    this.slot = slot;
    this.slotOrder = slotOrder;
    this.conflictOrder = conflictOrder;
  }

  @Override
  public int getSlot() {
    return slot;
  }

  @Override
  public int getSlotOrder() {
    return slotOrder;
  }

  @Override
  public int getConflictOrder() {
    return conflictOrder;
  }

  @Override
  public String toString() {
    return "MappingInfo{"
        + "slot=" + slot + ", "
        + "slotOrder=" + slotOrder + ", "
        + "conflictOrder=" + conflictOrder
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof MappedButtonConfig.MappingInfo) {
      MappedButtonConfig.MappingInfo that = (MappedButtonConfig.MappingInfo) o;
      return (this.slot == that.getSlot())
           && (this.slotOrder == that.getSlotOrder())
           && (this.conflictOrder == that.getConflictOrder());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= this.slot;
    h *= 1000003;
    h ^= this.slotOrder;
    h *= 1000003;
    h ^= this.conflictOrder;
    return h;
  }

  static final class Builder extends MappedButtonConfig.MappingInfo.Builder {
    private Integer slot;
    private Integer slotOrder;
    private Integer conflictOrder;
    Builder() {
    }
    private Builder(MappedButtonConfig.MappingInfo source) {
      this.slot = source.getSlot();
      this.slotOrder = source.getSlotOrder();
      this.conflictOrder = source.getConflictOrder();
    }
    @Override
    public MappedButtonConfig.MappingInfo.Builder setSlot(int slot) {
      this.slot = slot;
      return this;
    }
    @Override
    public MappedButtonConfig.MappingInfo.Builder setSlotOrder(int slotOrder) {
      this.slotOrder = slotOrder;
      return this;
    }
    @Override
    public MappedButtonConfig.MappingInfo.Builder setConflictOrder(int conflictOrder) {
      this.conflictOrder = conflictOrder;
      return this;
    }
    @Override
    public MappedButtonConfig.MappingInfo build() {
      String missing = "";
      if (this.slot == null) {
        missing += " slot";
      }
      if (this.slotOrder == null) {
        missing += " slotOrder";
      }
      if (this.conflictOrder == null) {
        missing += " conflictOrder";
      }
      if (!missing.isEmpty()) {
        throw new IllegalStateException("Missing required properties:" + missing);
      }
      return new AutoValue_MappedButtonConfig_MappingInfo(
          this.slot,
          this.slotOrder,
          this.conflictOrder);
    }
  }

}
