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

import com.google.common.base.Optional;

/** An interface representing a number's spam status. */
@SuppressWarnings("Guava")
public interface SpamStatus {

  /** Returns true if the number is spam. */
  boolean isSpam();

  /**
   * Returns the timestamp (in milliseconds) indicating when the number's spam status entered the
   * underlying data source.
   *
   * <p>{@code Optional.absent()} is returned if
   *
   * <ul>
   *   <li>the number's spam status doesn't exist in the underlying data source, or
   *   <li>the underlying data source can't provide a timestamp.
   * </ul>
   */
  Optional<Long> getTimestampMillis();

  /** Returns the {@link SpamMetadata} associated with this status. */
  SpamMetadata getSpamMetadata();
}
