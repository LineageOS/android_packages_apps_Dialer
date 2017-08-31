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

  public static final EnrichedCallCapabilities NO_CAPABILITIES = builder().build();

  public static final EnrichedCallCapabilities ALL_CAPABILITIES =
      builder()
          .setCallComposerCapable(true)
          .setPostCallCapable(true)
          .setVideoShareCapable(true)
          .build();

  public abstract boolean isCallComposerCapable();

  public abstract boolean isPostCallCapable();

  public abstract boolean isVideoShareCapable();

  public abstract Builder toBuilder();

  /**
   * Returns {@code true} if these capabilities represent those of a user that is temporarily
   * unavailable. This is an indication that capabilities should be refreshed.
   */
  public abstract boolean isTemporarilyUnavailable();

  /**
   * Creates an instance of {@link Builder}.
   *
   * <p>Unless otherwise set, all fields will default to false.
   */
  public static Builder builder() {
    return new AutoValue_EnrichedCallCapabilities.Builder()
        .setCallComposerCapable(false)
        .setPostCallCapable(false)
        .setVideoShareCapable(false)
        .setTemporarilyUnavailable(false);
  }

  /** Creates instances of {@link EnrichedCallCapabilities}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setCallComposerCapable(boolean isCapable);

    public abstract Builder setPostCallCapable(boolean isCapable);

    public abstract Builder setVideoShareCapable(boolean isCapable);

    public abstract Builder setTemporarilyUnavailable(boolean temporarilyUnavailable);

    public abstract EnrichedCallCapabilities build();
  }
}
