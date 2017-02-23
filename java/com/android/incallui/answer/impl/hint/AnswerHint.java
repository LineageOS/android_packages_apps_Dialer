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

package com.android.incallui.answer.impl.hint;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Interface to overlay a hint of how to answer the call. */
public interface AnswerHint {

  /**
   * Inflates the hint's layout into the container.
   *
   * <p>TODO: if the hint becomes more dependent on other UI elements of the AnswerFragment,
   * should put put and hintText into another data structure.
   */
  void onCreateView(LayoutInflater inflater, ViewGroup container, View puck, TextView hintText);

  /** Called when the puck bounce animation begins. */
  void onBounceStart();

  /**
   * Called when the bounce animation has ended (transitioned into other animations). The hint
   * should reset itself.
   */
  void onBounceEnd();

  /** Called when the call is accepted or rejected through user interaction. */
  void onAnswered();
}
