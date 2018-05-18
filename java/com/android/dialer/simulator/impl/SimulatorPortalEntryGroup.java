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

package com.android.dialer.simulator.impl;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;

/** Represents a portal that receives requests from either UI or IPC. */
@AutoValue
public abstract class SimulatorPortalEntryGroup {
  abstract ImmutableMap<String, Runnable> methods();

  abstract ImmutableMap<String, SimulatorPortalEntryGroup> subPortals();

  static Builder builder() {
    return new AutoValue_SimulatorPortalEntryGroup.Builder()
        .setMethods(Collections.emptyMap())
        .setSubPortals(Collections.emptyMap());
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setMethods(Map<String, Runnable> value);

    abstract Builder setSubPortals(Map<String, SimulatorPortalEntryGroup> value);

    abstract SimulatorPortalEntryGroup build();
  }
}
