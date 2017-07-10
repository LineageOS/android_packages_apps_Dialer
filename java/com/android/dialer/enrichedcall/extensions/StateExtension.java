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

package com.android.dialer.enrichedcall.extensions;

import android.support.annotation.NonNull;
import com.android.dialer.common.Assert;
import com.android.dialer.enrichedcall.Session;
import com.android.dialer.enrichedcall.Session.State;

/** Extends the {@link State} to include a toString method. */
public class StateExtension {

  /** Returns the string representation for the given {@link State}. */
  @NonNull
  public static String toString(@State int callComposerState) {
    if (callComposerState == Session.STATE_NONE) {
      return "STATE_NONE";
    }
    if (callComposerState == Session.STATE_STARTING) {
      return "STATE_STARTING";
    }
    if (callComposerState == Session.STATE_STARTED) {
      return "STATE_STARTED";
    }
    if (callComposerState == Session.STATE_START_FAILED) {
      return "STATE_START_FAILED";
    }
    if (callComposerState == Session.STATE_MESSAGE_SENT) {
      return "STATE_MESSAGE_SENT";
    }
    if (callComposerState == Session.STATE_MESSAGE_FAILED) {
      return "STATE_MESSAGE_FAILED";
    }
    if (callComposerState == Session.STATE_CLOSED) {
      return "STATE_CLOSED";
    }
    Assert.checkArgument(false, "Unexpected callComposerState: %d", callComposerState);
    return null;
  }
}
