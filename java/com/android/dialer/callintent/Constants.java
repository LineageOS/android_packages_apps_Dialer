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

package com.android.dialer.callintent;

/** Constants used to construct and parse call intents. These should never be made public. */
/* package */ class Constants {
  // This is a Dialer extra that is set for outgoing calls and used by the InCallUI.
  /* package */ static final String EXTRA_CALL_SPECIFIC_APP_DATA =
      "com.android.dialer.callintent.CALL_SPECIFIC_APP_DATA";

  // This is a hidden system extra. For outgoing calls Dialer sets it and parses it but for incoming
  // calls Telecom sets it and Dialer parses it.
  /* package */ static final String EXTRA_CALL_CREATED_TIME_MILLIS =
      "android.telecom.extra.CALL_CREATED_TIME_MILLIS";

  private Constants() {}
}
