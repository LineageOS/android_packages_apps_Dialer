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

package com.android.dialer.calllog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.Nullable;
import com.android.dialer.calllog.constants.IntentNames;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DefaultFutureCallback;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * A {@link BroadcastReceiver} that starts/cancels refreshing the annotated call log when notified.
 */
public final class RefreshAnnotatedCallLogReceiver extends BroadcastReceiver {

  /**
   * This is a reasonable time that it might take between related call log writes, that also
   * shouldn't slow down single-writes too much. For example, when populating the database using the
   * simulator, using this value results in ~6 refresh cycles (on a release build) to write 120 call
   * log entries.
   */
  private static final long REFRESH_ANNOTATED_CALL_LOG_WAIT_MILLIS = 100L;

  private final RefreshAnnotatedCallLogWorker refreshAnnotatedCallLogWorker;

  @Nullable private Runnable refreshAnnotatedCallLogRunnable;

  /** Returns an {@link IntentFilter} containing all actions accepted by this broadcast receiver. */
  public static IntentFilter getIntentFilter() {
    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(IntentNames.ACTION_REFRESH_ANNOTATED_CALL_LOG);
    intentFilter.addAction(IntentNames.ACTION_CANCEL_REFRESHING_ANNOTATED_CALL_LOG);
    return intentFilter;
  }

  public RefreshAnnotatedCallLogReceiver(Context context) {
    refreshAnnotatedCallLogWorker =
        CallLogComponent.get(context).getRefreshAnnotatedCallLogWorker();
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    LogUtil.enterBlock("RefreshAnnotatedCallLogReceiver.onReceive");

    String action = intent.getAction();

    if (IntentNames.ACTION_REFRESH_ANNOTATED_CALL_LOG.equals(action)) {
      boolean checkDirty = intent.getBooleanExtra(IntentNames.EXTRA_CHECK_DIRTY, false);
      refreshAnnotatedCallLog(checkDirty);
    } else if (IntentNames.ACTION_CANCEL_REFRESHING_ANNOTATED_CALL_LOG.equals(action)) {
      cancelRefreshingAnnotatedCallLog();
    }
  }

  /**
   * Request a refresh of the annotated call log.
   *
   * <p>Note that the execution will be delayed by {@link #REFRESH_ANNOTATED_CALL_LOG_WAIT_MILLIS}.
   * Once the work begins, it can't be cancelled.
   *
   * @see #cancelRefreshingAnnotatedCallLog()
   */
  private void refreshAnnotatedCallLog(boolean checkDirty) {
    LogUtil.enterBlock("RefreshAnnotatedCallLogReceiver.refreshAnnotatedCallLog");

    // If we already scheduled a refresh, cancel it and schedule a new one so that repeated requests
    // in quick succession don't result in too much work. For example, if we get 10 requests in
    // 10ms, and a complete refresh takes a constant 200ms, the refresh will take 300ms (100ms wait
    // and 1 iteration @200ms) instead of 2 seconds (10 iterations @ 200ms) since the work requests
    // are serialized in RefreshAnnotatedCallLogWorker.
    //
    // We might get many requests in quick succession, for example, when the simulator inserts
    // hundreds of rows into the system call log, or when the data for a new call is incrementally
    // written to different columns as it becomes available.
    ThreadUtil.getUiThreadHandler().removeCallbacks(refreshAnnotatedCallLogRunnable);

    refreshAnnotatedCallLogRunnable =
        () -> {
          ListenableFuture<Void> future =
              checkDirty
                  ? refreshAnnotatedCallLogWorker.refreshWithDirtyCheck()
                  : refreshAnnotatedCallLogWorker.refreshWithoutDirtyCheck();
          Futures.addCallback(
              future, new DefaultFutureCallback<>(), MoreExecutors.directExecutor());
        };

    ThreadUtil.getUiThreadHandler()
        .postDelayed(refreshAnnotatedCallLogRunnable, REFRESH_ANNOTATED_CALL_LOG_WAIT_MILLIS);
  }

  /**
   * When a refresh is requested, its execution is delayed (see {@link
   * #refreshAnnotatedCallLog(boolean)}). This method only cancels the refresh if it hasn't started.
   */
  private void cancelRefreshingAnnotatedCallLog() {
    LogUtil.enterBlock("RefreshAnnotatedCallLogReceiver.cancelRefreshingAnnotatedCallLog");

    ThreadUtil.getUiThreadHandler().removeCallbacks(refreshAnnotatedCallLogRunnable);
  }
}
