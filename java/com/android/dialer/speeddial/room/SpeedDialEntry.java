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

package com.android.dialer.speeddial.room;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** SpeedDialEntry Entity. Represents a single element in favorites. */
@Entity
public class SpeedDialEntry {

  public static final int UNKNOWN = 0;
  public static final int VOICE = 1;
  public static final int VIDEO = 2;

  /** An Enum for the different row view types shown by this adapter. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({UNKNOWN, VOICE, VIDEO})
  public @interface Type {}

  @PrimaryKey(autoGenerate = true)
  public Integer id;

  @Nullable public final String number;

  public final long contactId;

  @Type public final int type;

  /** Build an unknown speed dial entry. */
  public static SpeedDialEntry newSpeedDialEntry(long contactId) {
    return new SpeedDialEntry(null, contactId, UNKNOWN);
  }

  public SpeedDialEntry(@Nullable String number, long contactId, @Type int type) {
    this.number = number;
    this.contactId = contactId;
    this.type = type;
  }
}
