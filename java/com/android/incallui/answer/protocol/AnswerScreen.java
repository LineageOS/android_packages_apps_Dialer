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

import android.support.v4.app.Fragment;
import java.util.List;

/** Interface for the answer module. */
public interface AnswerScreen {

  String getCallId();

  boolean isVideoCall();

  boolean isVideoUpgradeRequest();

  boolean allowAnswerAndRelease();

  boolean isActionTimeout();

  void setTextResponses(List<String> textResponses);

  boolean hasPendingDialogs();

  void dismissPendingDialogs();

  Fragment getAnswerScreenFragment();
}
