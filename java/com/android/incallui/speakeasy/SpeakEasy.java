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

package com.android.incallui.speakeasy;

import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;

/** This interface provides a wrapper between callers and the Whisper client. */
public interface SpeakEasy {

  /** Signals to the user interface that the feature is available for use. */
  boolean isEnabled();

  /**
   * Create a new instance of SpeakEasy fragment.
   *
   * @param callId call id of the call.
   * @param nameOrNumber name or number of the caller to be displayed
   * @param sessionStartTimeMillis start time of the session in terms of {@link
   *     android.os.SystemClock#elapsedRealtime}.
   * @return new SpeakEasy fragment. Null if the SpeakEasy feature is not available for use
   */
  @Nullable
  Fragment getSpeakEasyFragment(String callId, String nameOrNumber, long sessionStartTimeMillis);
}
