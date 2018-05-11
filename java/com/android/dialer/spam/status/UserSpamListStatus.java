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
 * limitations under the License.
 */

package com.android.dialer.spam.status;

import android.support.annotation.IntDef;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** A value class representing a number's spam status in the user spam list. */
@AutoValue
@SuppressWarnings("Guava")
public abstract class UserSpamListStatus {

  /** Integers representing the spam status in the user spam list. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({Status.NOT_ON_LIST, Status.WHITELISTED, Status.BLACKLISTED})
  public @interface Status {
    int NOT_ON_LIST = 1;
    int WHITELISTED = 2;
    int BLACKLISTED = 3;
  }

  public abstract @Status int getStatus();

  /**
   * Returns the timestamp (in milliseconds) representing when a number's spam status was put on the
   * list, or {@code Optional.absent()} if the number is not on the list.
   */
  public abstract Optional<Long> getTimestampMillis();

  public static UserSpamListStatus notOnList() {
    return new AutoValue_UserSpamListStatus(Status.NOT_ON_LIST, Optional.absent());
  }

  public static UserSpamListStatus whitelisted(long timestampMillis) {
    return new AutoValue_UserSpamListStatus(Status.WHITELISTED, Optional.of(timestampMillis));
  }

  public static UserSpamListStatus blacklisted(long timestampMillis) {
    return new AutoValue_UserSpamListStatus(Status.BLACKLISTED, Optional.of(timestampMillis));
  }
}
