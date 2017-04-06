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
import android.service.notification.StatusBarNotification;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import java.util.Objects;

/** Utilities for dealing with grouped notifications */
public final class GroupedNotificationUtil {

  /**
   * Remove notification(s) that were added as part of a group. Will ensure that if this is the last
   * notification in the group the summary will be removed.
   *
   * @param tag String tag as included in {@link NotificationManager#notify(String, int,
   *     android.app.Notification)}. If null will remove all notifications under id
   * @param id notification id as included with {@link NotificationManager#notify(String, int,
   *     android.app.Notification)}.
   * @param summaryTag String tag of the summary notification
   */
  public static void removeNotification(
      @NonNull NotificationManager notificationManager,
      @Nullable String tag,
      int id,
      @NonNull String summaryTag) {
    if (tag == null) {
      // Clear all grouped notifications
      for (StatusBarNotification notification : notificationManager.getActiveNotifications()) {
        if (notification.getId() == id) {
          notificationManager.cancel(notification.getTag(), id);
        }
      }
    } else {
      notificationManager.cancel(tag, id);

      // See if other non-summary grouped notifications exist, and if not then clear the summary
      boolean clearSummary = true;
      for (StatusBarNotification notification : notificationManager.getActiveNotifications()) {
        if (notification.getId() == id && !Objects.equals(summaryTag, notification.getTag())) {
          clearSummary = false;
          break;
        }
      }
      if (clearSummary) {
        notificationManager.cancel(summaryTag, id);
      }
    }
  }
}
