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
 * limitations under the License.
 */

package com.android.dialer.spam;

import android.content.Context;
import java.util.Objects;

/** Accessor for the spam bindings. */
public class Spam {

  private static SpamBindings spamBindings;

  private Spam() {}

  public static SpamBindings get(Context context) {
    Objects.requireNonNull(context);
    if (spamBindings != null) {
      return spamBindings;
    }

    Context application = context.getApplicationContext();
    if (application instanceof SpamBindingsFactory) {
      spamBindings = ((SpamBindingsFactory) application).newSpamBindings();
    }

    if (spamBindings == null) {
      spamBindings = new SpamBindingsStub();
    }
    return spamBindings;
  }

  public static void setForTesting(SpamBindings spamBindings) {
    Spam.spamBindings = spamBindings;
  }
}
