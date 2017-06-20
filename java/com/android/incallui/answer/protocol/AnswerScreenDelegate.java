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

package com.android.incallui.answer.protocol;

import android.support.annotation.FloatRange;

/** Callbacks implemented by the container app for this module. */
public interface AnswerScreenDelegate {

  void onAnswerScreenUnready();

  void onDismissDialog();

  void onRejectCallWithMessage(String message);

  void onAnswer(boolean answerVideoAsAudio);

  void onReject();

  void onAnswerAndReleaseCall();

  void onAnswerAndReleaseButtonEnabled();

  void onAnswerAndReleaseButtonDisabled();
  /**
   * Sets the window background color based on foreground call's theme and the given progress. This
   * is called from the answer UI to animate the accept and reject action.
   *
   * <p>When the user is rejecting we animate the background color to a mostly transparent gray. The
   * end effect is that the home screen shows through.
   *
   * @param progress float from -1 to 1. -1 is fully rejected, 1 is fully accepted, and 0 is neutral
   */
  void updateWindowBackgroundColor(@FloatRange(from = -1f, to = 1.0f) float progress);

  /** Returns true if any answer/reject action timed out. */
  boolean isActionTimeout();
}
