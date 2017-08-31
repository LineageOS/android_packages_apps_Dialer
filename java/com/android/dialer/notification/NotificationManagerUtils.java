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

import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.StatusBarNotification;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import com.android.dialer.common.Assert;

/** Utilities to manage notifications. */
public final class NotificationManagerUtils {
  public static void cancelAllInGroup(@NonNull Context context, @NonNull String groupKey) {
    Assert.isNotNull(context);
    Assert.checkArgument(!TextUtils.isEmpty(groupKey));

    NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
    for (StatusBarNotification notification : notificationManager.getActiveNotifications()) {
      if (TextUtils.equals(groupKey, notification.getNotification().getGroup())) {
        notificationManager.cancel(notification.getTag(), notification.getId());
      }
    }
  }

  private NotificationManagerUtils() {}
}
