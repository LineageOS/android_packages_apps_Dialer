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

/** Does nothing. Used to avoid null checks on AnswerHint. */
public class EmptyAnswerHint implements AnswerHint {

  @Override
  public void onCreateView(
      LayoutInflater inflater, ViewGroup container, View puck, TextView hintText) {}

  @Override
  public void onBounceStart() {}

  @Override
  public void onBounceEnd() {}

  @Override
  public void onAnswered() {}
}
