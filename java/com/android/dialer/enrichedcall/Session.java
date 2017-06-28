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

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.multimedia.MultimediaData;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Holds state information and data about enriched calling sessions. */
public interface Session {

  /** Possible states for call composer sessions. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    STATE_NONE,
    STATE_STARTING,
    STATE_STARTED,
    STATE_START_FAILED,
    STATE_MESSAGE_SENT,
    STATE_MESSAGE_FAILED,
    STATE_CLOSED,
  })
  @interface State {}

  int STATE_NONE = 0;
  int STATE_STARTING = STATE_NONE + 1;
  int STATE_STARTED = STATE_STARTING + 1;
  int STATE_START_FAILED = STATE_STARTED + 1;
  int STATE_MESSAGE_SENT = STATE_START_FAILED + 1;
  int STATE_MESSAGE_FAILED = STATE_MESSAGE_SENT + 1;
  int STATE_CLOSED = STATE_MESSAGE_FAILED + 1;

  /** Id used for sessions that fail to start. */
  long NO_SESSION_ID = -1;

  /**
   * An id for the specific case when sending a message fails so early that a message id isn't
   * created.
   */
  String MESSAGE_ID_COULD_NOT_CREATE_ID = "messageIdCouldNotCreateId";

  /**
   * Returns the id associated with this session, or {@link #NO_SESSION_ID} if this represents a
   * session that failed to start.
   */
  long getSessionId();

  /** Returns the id of the dialer call associated with this session, or null if there isn't one. */
  @Nullable
  String getUniqueDialerCallId();

  void setUniqueDialerCallId(@NonNull String id);

  /** Returns the number associated with the remote end of this session. */
  @NonNull
  String getRemoteNumber();

  /** Returns the {@link State} for this session. */
  @State
  int getState();

  /** Returns the {@link MultimediaData} associated with this session. */
  @NonNull
  MultimediaData getMultimediaData();

  /** Returns type of this session, based on some arbitrarily defined type. */
  int getType();

  /**
   * Sets the {@link MultimediaData} for this session.
   *
   *
   * @throws IllegalArgumentException if the type is invalid
   */
  void setSessionData(@NonNull MultimediaData multimediaData, int type);
}
