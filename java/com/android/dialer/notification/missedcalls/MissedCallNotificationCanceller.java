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

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.common.LogUtil;
import com.android.dialer.notification.DialerNotificationManager;
import com.android.dialer.notification.NotificationManagerUtils;

/** Cancels missed calls notifications. */
public final class MissedCallNotificationCanceller {

  /** Cancels all missed call notifications. */
  public static void cancelAll(@NonNull Context context) {
    NotificationManagerUtils.cancelAllInGroup(context, MissedCallConstants.GROUP_KEY);
  }

  /** Cancels a missed call notification for a single call. */
  public static void cancelSingle(@NonNull Context context, @Nullable Uri callUri) {
    if (callUri == null) {
      LogUtil.e(
          "MissedCallNotificationCanceller.cancelSingle",
          "unable to cancel notification, uri is null");
      return;
    }
    // This will also dismiss the group summary if there are no more missed call notifications.
    DialerNotificationManager.cancel(
        context,
        MissedCallNotificationTags.getNotificationTagForCallUri(callUri),
        MissedCallConstants.NOTIFICATION_ID);
  }
}
