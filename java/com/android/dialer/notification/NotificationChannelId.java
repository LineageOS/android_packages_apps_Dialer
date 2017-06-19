/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.dialer.notification;

import android.support.annotation.StringDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Centralized source of all notification channels used by Dialer. */
@Retention(RetentionPolicy.SOURCE)
@StringDef({
  NotificationChannelId.INCOMING_CALL,
  NotificationChannelId.ONGOING_CALL,
  NotificationChannelId.MISSED_CALL,
  NotificationChannelId.DEFAULT,
})
public @interface NotificationChannelId {
  // This value is white listed in the system.
  // See /vendor/google/nexus_overlay/common/frameworks/base/core/res/res/values/config.xml
  String INCOMING_CALL = "phone_incoming_call";

  String ONGOING_CALL = "phone_ongoing_call";

  String MISSED_CALL = "phone_missed_call";

  String DEFAULT = "phone_default";
}
