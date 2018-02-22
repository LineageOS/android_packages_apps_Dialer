/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.calllog.constants;

/** A class containing names for call log intents. */
public final class IntentNames {

  public static final String ACTION_REFRESH_ANNOTATED_CALL_LOG = "refresh_annotated_call_log";

  public static final String ACTION_CANCEL_REFRESHING_ANNOTATED_CALL_LOG =
      "cancel_refreshing_annotated_call_log";

  public static final String EXTRA_CHECK_DIRTY = "check_dirty";

  private IntentNames() {}
}
