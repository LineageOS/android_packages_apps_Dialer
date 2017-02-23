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
 * limitations under the License
 */

package com.android.dialer.enrichedcall;

import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
 final class AutoValue_EnrichedCallCapabilities extends EnrichedCallCapabilities {

  private final boolean supportsCallComposer;
  private final boolean supportsPostCall;

  AutoValue_EnrichedCallCapabilities(
      boolean supportsCallComposer,
      boolean supportsPostCall) {
    this.supportsCallComposer = supportsCallComposer;
    this.supportsPostCall = supportsPostCall;
  }

  @Override
  public boolean supportsCallComposer() {
    return supportsCallComposer;
  }

  @Override
  public boolean supportsPostCall() {
    return supportsPostCall;
  }

  @Override
  public String toString() {
    return "EnrichedCallCapabilities{"
        + "supportsCallComposer=" + supportsCallComposer + ", "
        + "supportsPostCall=" + supportsPostCall + ", "
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof EnrichedCallCapabilities) {
      EnrichedCallCapabilities that = (EnrichedCallCapabilities) o;
      return (this.supportsCallComposer == that.supportsCallComposer())
           && (this.supportsPostCall == that.supportsPostCall());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= this.supportsCallComposer ? 1231 : 1237;
    h *= 1000003;
    h ^= this.supportsPostCall ? 1231 : 1237;
    return h;
  }

}

