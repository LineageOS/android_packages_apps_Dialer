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

package com.android.dialer.spam.status;

import android.support.annotation.Nullable;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;

/** Holds a boolean and long to represent spam status. */
@AutoValue
public abstract class SimpleSpamStatus implements SpamStatus {

  /** Returns a SimpleSpamStatus with the given boolean and timestamp. */
  public static SimpleSpamStatus create(boolean isSpam, @Nullable Long timestampMillis) {
    return builder()
        .setSpam(isSpam)
        .setTimestampMillis(timestampMillis)
        .setSpamMetadata(SpamMetadata.empty())
        .build();
  }

  /** Returns a SimpleSpamStatus that's not marked as spam and has no timestamp. */
  public static SimpleSpamStatus notSpam() {
    return create(false, null);
  }

  public static Builder builder() {
    return new AutoValue_SimpleSpamStatus.Builder();
  }

  /** Creates instances of SimpleSpamStatus. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setSpam(boolean isSpam);

    abstract Builder setTimestampMillis(Optional<Long> timestamp);

    public Builder setTimestampMillis(@Nullable Long timestampMillis) {
      return setTimestampMillis(Optional.fromNullable(timestampMillis));
    }

    public abstract Builder setSpamMetadata(SpamMetadata spamMetadata);

    public abstract SimpleSpamStatus build();
  }
}
