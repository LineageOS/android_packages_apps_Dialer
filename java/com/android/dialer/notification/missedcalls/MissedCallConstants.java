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
 * limitations under the License.
 */

package com.android.dialer.notification.missedcalls;

/** Constants related to missed call notifications. */
public final class MissedCallConstants {

  /** Prefix used to generate a unique tag for each missed call notification. */
  public static final String NOTIFICATION_TAG_PREFIX = "MissedCall_";

  /** Common ID for all missed call notifications. */
  public static final int NOTIFICATION_ID = 1;

  /** Tag for the group summary notification. */
  public static final String GROUP_SUMMARY_NOTIFICATION_TAG = "GroupSummary_MissedCall";

  /**
   * Key used to associate all missed call notifications and the summary as belonging to a single
   * group.
   */
  public static final String GROUP_KEY = "MissedCallGroup";
}
