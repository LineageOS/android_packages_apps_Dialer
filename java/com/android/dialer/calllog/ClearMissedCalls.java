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

package com.android.dialer.calllog;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.support.v4.os.UserManagerCompat;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.common.concurrent.Annotations.Ui;
import com.android.dialer.common.database.Selection;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.notification.missedcalls.MissedCallNotificationCanceller;
import com.android.dialer.util.PermissionsUtil;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collection;
import javax.inject.Inject;

/**
 * Clears missed calls. This includes cancelling notifications and updating the "IS_READ" status in
 * the system call log.
 */
public final class ClearMissedCalls {

  private final Context appContext;
  private final ListeningExecutorService backgroundExecutor;
  private final ListeningExecutorService uiThreadExecutor;

  @Inject
  ClearMissedCalls(
      @ApplicationContext Context appContext,
      @BackgroundExecutor ListeningExecutorService backgroundExecutor,
      @Ui ListeningExecutorService uiThreadExecutor) {
    this.appContext = appContext;
    this.backgroundExecutor = backgroundExecutor;
    this.uiThreadExecutor = uiThreadExecutor;
  }

  /**
   * Cancels all missed call notifications and marks all "unread" missed calls in the system call
   * log as "read".
   */
  public ListenableFuture<Void> clearAll() {
    ListenableFuture<Void> markReadFuture = markRead(ImmutableSet.of());
    ListenableFuture<Void> cancelNotificationsFuture =
        uiThreadExecutor.submit(
            () -> {
              MissedCallNotificationCanceller.cancelAll(appContext);
              return null;
            });

    // Note on this usage of whenAllComplete:
    //   -The returned future completes when all sub-futures complete (whether they fail or not)
    //   -The returned future fails if any sub-future fails
    return Futures.whenAllComplete(markReadFuture, cancelNotificationsFuture)
        .call(
            () -> {
              // Calling get() is necessary to propagate failures.
              markReadFuture.get();
              cancelNotificationsFuture.get();
              return null;
            },
            MoreExecutors.directExecutor());
  }

  /**
   * For the provided set of IDs from the system call log, cancels their missed call notifications
   * and marks them "read".
   *
   * @param ids IDs from the system call log (see {@link Calls#_ID}}.
   */
  public ListenableFuture<Void> clearBySystemCallLogId(Collection<Long> ids) {
    ListenableFuture<Void> markReadFuture = markRead(ids);
    ListenableFuture<Void> cancelNotificationsFuture =
        uiThreadExecutor.submit(
            () -> {
              for (long id : ids) {
                Uri callUri = Calls.CONTENT_URI.buildUpon().appendPath(Long.toString(id)).build();
                MissedCallNotificationCanceller.cancelSingle(appContext, callUri);
              }
              return null;
            });

    // Note on this usage of whenAllComplete:
    //   -The returned future completes when all sub-futures complete (whether they fail or not)
    //   -The returned future fails if any sub-future fails
    return Futures.whenAllComplete(markReadFuture, cancelNotificationsFuture)
        .call(
            () -> {
              // Calling get() is necessary to propagate failures.
              markReadFuture.get();
              cancelNotificationsFuture.get();
              return null;
            },
            MoreExecutors.directExecutor());
  }

  /**
   * Marks all provided system call log IDs as read, or if the provided collection is empty, marks
   * all calls as read.
   */
  @SuppressLint("MissingPermission")
  private ListenableFuture<Void> markRead(Collection<Long> ids) {
    return backgroundExecutor.submit(
        () -> {
          if (!UserManagerCompat.isUserUnlocked(appContext)) {
            LogUtil.e("ClearMissedCalls.markRead", "locked");
            return null;
          }
          if (!PermissionsUtil.hasCallLogWritePermissions(appContext)) {
            LogUtil.e("ClearMissedCalls.markRead", "no permission");
            return null;
          }

          ContentValues values = new ContentValues();
          values.put(Calls.IS_READ, 1);

          Selection.Builder selectionBuilder =
              Selection.builder()
                  .and(
                      Selection.column(Calls.IS_READ)
                          .is("=", 0)
                          .buildUpon()
                          .or(Selection.column(Calls.IS_READ).is("IS NULL"))
                          .build())
                  .and(Selection.column(Calls.TYPE).is("=", Calls.MISSED_TYPE));
          if (!ids.isEmpty()) {
            selectionBuilder.and(Selection.column(Calls._ID).in(toStrings(ids)));
          }
          Selection selection = selectionBuilder.build();
          appContext
              .getContentResolver()
              .update(
                  Calls.CONTENT_URI,
                  values,
                  selection.getSelection(),
                  selection.getSelectionArgs());
          return null;
        });
  }

  private static String[] toStrings(Collection<Long> longs) {
    String[] strings = new String[longs.size()];
    int i = 0;
    for (long value : longs) {
      strings[i++] = Long.toString(value);
    }
    return strings;
  }
}
