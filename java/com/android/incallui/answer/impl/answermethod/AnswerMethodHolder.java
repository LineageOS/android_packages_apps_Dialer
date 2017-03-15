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

package com.android.incallui.answer.impl.answermethod;

import android.support.annotation.FloatRange;

/** Defines callbacks {@link AnswerMethod AnswerMethods} may use to update their parent. */
public interface AnswerMethodHolder {

  /**
   * Update animation based on method progress.
   *
   * @param answerProgress float representing progress. -1 is fully declined, 1 is fully answered,
   *     and 0 is neutral.
   */
  void onAnswerProgressUpdate(@FloatRange(from = -1f, to = 1f) float answerProgress);

  /** Answer the current call. */
  void answerFromMethod();

  /** Reject the current call. */
  void rejectFromMethod();

  /** Set AnswerProgress to zero (not due to normal updates). */
  void resetAnswerProgress();

  /**
   * Check whether the current call is a video call.
   *
   * @return true iff the current call is a video call.
   */
  boolean isVideoCall();

  boolean isVideoUpgradeRequest();
}
