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

package com.android.incallui;

import android.support.annotation.FloatRange;
import com.android.incallui.answer.protocol.AnswerScreenDelegate;

/**
 * Stub implementation of the answer screen delegate. Used to keep the answer fragment visible when
 * no call exists.
 */
public class AnswerScreenPresenterStub implements AnswerScreenDelegate {
  @Override
  public void onAnswerScreenUnready() {}

  @Override
  public void onDismissDialog() {}

  @Override
  public void onRejectCallWithMessage(String message) {}

  @Override
  public void onAnswer(boolean answerVideoAsAudio) {}

  @Override
  public void onReject() {}

  @Override
  public void onAnswerAndReleaseCall() {}

  @Override
  public void onAnswerAndReleaseButtonEnabled() {}

  @Override
  public void onAnswerAndReleaseButtonDisabled() {}

  @Override
  public void updateWindowBackgroundColor(@FloatRange(from = -1f, to = 1.0f) float progress) {}

  @Override
  public boolean isActionTimeout() {
    return false;
  }
}
