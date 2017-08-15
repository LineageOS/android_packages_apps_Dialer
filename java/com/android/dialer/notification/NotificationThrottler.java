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

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.StatusBarNotification;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Utility to ensure that only a certain number of notifications are shown for a particular
 * notification type. Once the limit is reached, older notifications are cancelled.
 */
/* package */ class NotificationThrottler {
  private static final int MAX_NOTIFICATIONS_PER_TAG = 10;

  /* package */ static void throttle(@NonNull Context context, @NonNull Notification notification) {
    Assert.isNotNull(context);
    Assert.isNotNull(notification);

    // No limiting for non-grouped notifications.
    String groupKey = notification.getGroup();
    if (TextUtils.isEmpty(groupKey)) {
      return;
    }

    int count = 0;
    NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
    for (StatusBarNotification currentNotification : notificationManager.getActiveNotifications()) {
      if (isNotificationInGroup(currentNotification, groupKey)) {
        count++;
      }
    }

    if (count > MAX_NOTIFICATIONS_PER_TAG) {
      LogUtil.i(
          "NotificationThrottler.throttle",
          "groupKey: %s is over limit, count: %d, limit: %d",
          groupKey,
          count,
          MAX_NOTIFICATIONS_PER_TAG);
      List<StatusBarNotification> notifications = getSortedMatchingNotifications(context, groupKey);
      for (int i = 0; i < (count - MAX_NOTIFICATIONS_PER_TAG); i++) {
        notificationManager.cancel(notifications.get(i).getTag(), notifications.get(i).getId());
      }
    }
  }

  private static List<StatusBarNotification> getSortedMatchingNotifications(
      @NonNull Context context, @NonNull String groupKey) {
    List<StatusBarNotification> notifications = new ArrayList<>();
    NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
    for (StatusBarNotification notification : notificationManager.getActiveNotifications()) {
      if (isNotificationInGroup(notification, groupKey)) {
        notifications.add(notification);
      }
    }
    Collections.sort(
        notifications,
        new Comparator<StatusBarNotification>() {
          @Override
          public int compare(StatusBarNotification left, StatusBarNotification right) {
            return Long.compare(left.getPostTime(), right.getPostTime());
          }
        });
    return notifications;
  }

  private static boolean isNotificationInGroup(
      @NonNull StatusBarNotification notification, @NonNull String groupKey) {
    // Don't include group summaries.
    if ((notification.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
      return false;
    }

    return TextUtils.equals(groupKey, notification.getNotification().getGroup());
  }

  private NotificationThrottler() {}
}
