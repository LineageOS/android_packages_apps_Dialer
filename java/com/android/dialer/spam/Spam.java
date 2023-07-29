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

import android.provider.CallLog.Calls;

/** Allows the container application to mark calls as spam. */
public class Spam {
  /**
   * Given a number's spam status and a call type, determine if the call should be shown as spam.
   *
   * <p>We show a call as spam if
   *
   * <ul>
   *   <li>the number is marked as spam, and
   *   <li>the call is not an outgoing call.
   * </ul>
   *
   * <p>This is because spammers can hide behind a legit number (e.g., a customer service number).
   * We don't want to show a spam icon when users call it.
   *
   * @param isNumberSpam Whether the number is spam.
   * @param callType One of the types in {@link android.provider.CallLog.Calls#TYPE}.
   * @return true if the number is spam *and* the call is not an outgoing call.
   */
  public static boolean shouldShowAsSpam(boolean isNumberSpam, int callType) {
    return isNumberSpam && (callType != Calls.OUTGOING_TYPE);
  }
}
