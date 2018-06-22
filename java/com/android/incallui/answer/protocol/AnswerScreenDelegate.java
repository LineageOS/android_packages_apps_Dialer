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

import com.android.incallui.incalluilock.InCallUiLock;

/** Callbacks implemented by the container app for this module. */
public interface AnswerScreenDelegate {

  void onAnswerScreenUnready();

  void onRejectCallWithMessage(String message);

  void onAnswer(boolean answerVideoAsAudio);

  void onReject();

  void onSpeakEasyCall();

  void onAnswerAndReleaseCall();

  void onAnswerAndReleaseButtonEnabled();

  void onAnswerAndReleaseButtonDisabled();

  /** Returns true if any answer/reject action timed out. */
  boolean isActionTimeout();

  InCallUiLock acquireInCallUiLock(String tag);
}
