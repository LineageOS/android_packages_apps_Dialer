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

import com.google.auto.value.AutoValue;

/** Value type holding enriched call capabilities. */
@AutoValue
public abstract class EnrichedCallCapabilities {

  public static final EnrichedCallCapabilities NO_CAPABILITIES =
      EnrichedCallCapabilities.create(false, false, false);

  public static EnrichedCallCapabilities create(
      boolean supportsCallComposer, boolean supportsPostCall, boolean supportsVideoCall) {
    return new AutoValue_EnrichedCallCapabilities(
        supportsCallComposer, supportsPostCall, supportsVideoCall);
  }

  public abstract boolean supportsCallComposer();

  public abstract boolean supportsPostCall();

  public abstract boolean supportsVideoShare();
}
