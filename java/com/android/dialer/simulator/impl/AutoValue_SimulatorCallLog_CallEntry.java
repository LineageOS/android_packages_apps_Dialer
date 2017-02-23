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

package com.android.dialer.simulator.impl;

import android.support.annotation.NonNull;
import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
 final class AutoValue_SimulatorCallLog_CallEntry extends SimulatorCallLog.CallEntry {

  private final String number;
  private final int type;
  private final int presentation;
  private final long timeMillis;

  private AutoValue_SimulatorCallLog_CallEntry(
      String number,
      int type,
      int presentation,
      long timeMillis) {
    this.number = number;
    this.type = type;
    this.presentation = presentation;
    this.timeMillis = timeMillis;
  }

  @NonNull
  @Override
  String getNumber() {
    return number;
  }

  @Override
  int getType() {
    return type;
  }

  @Override
  int getPresentation() {
    return presentation;
  }

  @Override
  long getTimeMillis() {
    return timeMillis;
  }

  @Override
  public String toString() {
    return "CallEntry{"
        + "number=" + number + ", "
        + "type=" + type + ", "
        + "presentation=" + presentation + ", "
        + "timeMillis=" + timeMillis
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof SimulatorCallLog.CallEntry) {
      SimulatorCallLog.CallEntry that = (SimulatorCallLog.CallEntry) o;
      return (this.number.equals(that.getNumber()))
           && (this.type == that.getType())
           && (this.presentation == that.getPresentation())
           && (this.timeMillis == that.getTimeMillis());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= this.number.hashCode();
    h *= 1000003;
    h ^= this.type;
    h *= 1000003;
    h ^= this.presentation;
    h *= 1000003;
    h ^= (this.timeMillis >>> 32) ^ this.timeMillis;
    return h;
  }

  static final class Builder extends SimulatorCallLog.CallEntry.Builder {
    private String number;
    private Integer type;
    private Integer presentation;
    private Long timeMillis;
    Builder() {
    }
    private Builder(SimulatorCallLog.CallEntry source) {
      this.number = source.getNumber();
      this.type = source.getType();
      this.presentation = source.getPresentation();
      this.timeMillis = source.getTimeMillis();
    }
    @Override
    SimulatorCallLog.CallEntry.Builder setNumber(String number) {
      this.number = number;
      return this;
    }
    @Override
    SimulatorCallLog.CallEntry.Builder setType(int type) {
      this.type = type;
      return this;
    }
    @Override
    SimulatorCallLog.CallEntry.Builder setPresentation(int presentation) {
      this.presentation = presentation;
      return this;
    }
    @Override
    SimulatorCallLog.CallEntry.Builder setTimeMillis(long timeMillis) {
      this.timeMillis = timeMillis;
      return this;
    }
    @Override
    SimulatorCallLog.CallEntry build() {
      String missing = "";
      if (this.number == null) {
        missing += " number";
      }
      if (this.type == null) {
        missing += " type";
      }
      if (this.presentation == null) {
        missing += " presentation";
      }
      if (this.timeMillis == null) {
        missing += " timeMillis";
      }
      if (!missing.isEmpty()) {
        throw new IllegalStateException("Missing required properties:" + missing);
      }
      return new AutoValue_SimulatorCallLog_CallEntry(
          this.number,
          this.type,
          this.presentation,
          this.timeMillis);
    }
  }

}
