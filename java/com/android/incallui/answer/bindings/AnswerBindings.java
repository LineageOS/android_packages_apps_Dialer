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

package com.android.incallui.answer.bindings;

import com.android.incallui.answer.impl.AnswerFragment;
import com.android.incallui.answer.protocol.AnswerScreen;

/** Bindings for answer module. */
public class AnswerBindings {

  public static AnswerScreen createAnswerScreen(
      String callId,
      boolean isVideoCall,
      boolean isVideoUpgradeRequest,
      boolean isSelfManagedCamera,
      boolean allowAnswerAndRelease,
      boolean hasCallOnHold) {
    return AnswerFragment.newInstance(
        callId,
        isVideoCall,
        isVideoUpgradeRequest,
        isSelfManagedCamera,
        allowAnswerAndRelease,
        hasCallOnHold);
  }
}
