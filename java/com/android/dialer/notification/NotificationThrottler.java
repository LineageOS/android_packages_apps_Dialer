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
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility to ensure that only a certain number of notifications are shown for a particular
 * notification type. Once the limit is reached, older notifications are cancelled.
 */
class NotificationThrottler {
  /**
   * For gropued bundled notifications, the system UI will only display the last 8. For grouped
   * unbundled notifications, the system displays all notifications until a global maximum of 50 is
   * reached.
   */
  private static final int MAX_NOTIFICATIONS_PER_TAG = 8;

  private static final int HIGH_GLOBAL_NOTIFICATION_COUNT = 45;

  private static boolean didLogHighGlobalNotificationCountReached;

  /**
   * For all the active notifications in the same group as the provided notification, cancel the
   * earliest ones until the left ones is under limit.
   *
   * @param notification the provided notification to determine group
   * @return a set of cancelled notification
   */
  static Set<StatusBarNotification> throttle(
      @NonNull Context context, @NonNull Notification notification) {
    Assert.isNotNull(context);
    Assert.isNotNull(notification);
    Set<StatusBarNotification> throttledNotificationSet = new HashSet<>();

    // No limiting for non-grouped notifications.
    String groupKey = notification.getGroup();
    if (TextUtils.isEmpty(groupKey)) {
      return throttledNotificationSet;
    }

    NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
    StatusBarNotification[] activeNotifications = notificationManager.getActiveNotifications();
    if (activeNotifications.length > HIGH_GLOBAL_NOTIFICATION_COUNT
        && !didLogHighGlobalNotificationCountReached) {
      LogUtil.i(
          "NotificationThrottler.throttle",
          "app has %d notifications, system may suppress future notifications",
          activeNotifications.length);
      didLogHighGlobalNotificationCountReached = true;
      Logger.get(context)
          .logImpression(DialerImpression.Type.HIGH_GLOBAL_NOTIFICATION_COUNT_REACHED);
    }

    // Count the number of notificatons for this group (excluding the summary).
    int count = 0;
    for (StatusBarNotification currentNotification : activeNotifications) {
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
        throttledNotificationSet.add(notifications.get(i));
      }
    }
    return throttledNotificationSet;
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
