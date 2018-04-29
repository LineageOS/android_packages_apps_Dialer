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

package com.android.dialer.main.impl.bottomnav;

import android.Manifest;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.provider.CallLog.Calls;
import android.support.annotation.RequiresPermission;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.concurrent.UiListener;
import com.android.dialer.main.impl.bottomnav.BottomNavBar.TabIndex;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Observes the call log and updates the badge count to show the number of unread missed calls.
 *
 * <p>Used only when the new call log fragment is enabled.
 */
public final class MissedCallCountObserver extends ContentObserver {
  private final Context appContext;
  private final BottomNavBar bottomNavBar;
  private final UiListener<Integer> uiListener;

  public MissedCallCountObserver(
      Context appContext, BottomNavBar bottomNavBar, UiListener<Integer> uiListener) {
    super(null);
    this.appContext = appContext;
    this.bottomNavBar = bottomNavBar;
    this.uiListener = uiListener;
  }

  @RequiresPermission(Manifest.permission.READ_CALL_LOG)
  @Override
  public void onChange(boolean selfChange) {
    ListenableFuture<Integer> countFuture =
        DialerExecutorComponent.get(appContext)
            .backgroundExecutor()
            .submit(
                () -> {
                  try (Cursor cursor =
                      appContext
                          .getContentResolver()
                          .query(
                              Calls.CONTENT_URI,
                              new String[] {Calls._ID},
                              "("
                                  + Calls.IS_READ
                                  + " = ? OR "
                                  + Calls.IS_READ
                                  + " IS NULL) AND "
                                  + Calls.TYPE
                                  + " = ?",
                              new String[] {"0", Integer.toString(Calls.MISSED_TYPE)},
                              /* sortOrder= */ null)) {
                    return cursor == null ? 0 : cursor.getCount();
                  }
                });
    uiListener.listen(
        appContext,
        countFuture,
        count -> bottomNavBar.setNotificationCount(TabIndex.CALL_LOG, count == null ? 0 : count),
        throwable -> {
          throw new RuntimeException(throwable);
        });
  }
}
