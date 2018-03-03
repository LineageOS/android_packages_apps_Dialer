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
import com.android.dialer.calllog.RefreshAnnotatedCallLogWorker.RefreshResult;
import com.android.dialer.calllog.constants.IntentNames;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.LoggingBindings;
import com.android.dialer.metrics.FutureTimer;
import com.android.dialer.metrics.Metrics;
import com.android.dialer.metrics.MetricsComponent;
import com.google.common.base.Function;
import com.google.common.util.concurrent.FutureCallback;
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
  private final FutureTimer futureTimer;
  private final LoggingBindings logger;

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
    futureTimer = MetricsComponent.get(context).futureTimer();
    logger = Logger.get(context);
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
          ListenableFuture<RefreshResult> future =
              checkDirty
                  ? refreshAnnotatedCallLogWorker.refreshWithDirtyCheck()
                  : refreshAnnotatedCallLogWorker.refreshWithoutDirtyCheck();
          Futures.addCallback(
              future,
              new FutureCallback<RefreshResult>() {
                @Override
                public void onSuccess(RefreshResult refreshResult) {
                  logger.logImpression(getImpressionType(checkDirty, refreshResult));
                }

                @Override
                public void onFailure(Throwable throwable) {
                  ThreadUtil.getUiThreadHandler()
                      .post(
                          () -> {
                            throw new RuntimeException(throwable);
                          });
                }
              },
              MoreExecutors.directExecutor());
          futureTimer.applyTiming(future, new EventNameFromResultFunction(checkDirty));
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

  private static class EventNameFromResultFunction implements Function<RefreshResult, String> {

    private final boolean checkDirty;

    private EventNameFromResultFunction(boolean checkDirty) {
      this.checkDirty = checkDirty;
    }

    @Override
    public String apply(RefreshResult refreshResult) {
      switch (refreshResult) {
        case NOT_DIRTY:
          return Metrics.ANNOTATED_CALL_LOG_NOT_DIRTY; // NOT_DIRTY implies forceRefresh is false
        case REBUILT_BUT_NO_CHANGES_NEEDED:
          return checkDirty
              ? Metrics.ANNOTATED_LOG_NO_CHANGES_NEEDED
              : Metrics.NEW_CALL_LOG_FORCE_REFRESH_NO_CHANGES_NEEDED;
        case REBUILT_AND_CHANGES_NEEDED:
          return checkDirty
              ? Metrics.ANNOTATED_CALL_LOG_CHANGES_NEEDED
              : Metrics.ANNOTATED_CALL_LOG_FORCE_REFRESH_CHANGES_NEEDED;
        default:
          throw new IllegalStateException("Unsupported result: " + refreshResult);
      }
    }
  }

  private static DialerImpression.Type getImpressionType(
      boolean checkDirty, RefreshResult refreshResult) {
    switch (refreshResult) {
      case NOT_DIRTY:
        return DialerImpression.Type.ANNOTATED_CALL_LOG_NOT_DIRTY;
      case REBUILT_BUT_NO_CHANGES_NEEDED:
        return checkDirty
            ? DialerImpression.Type.ANNOTATED_CALL_LOG_NO_CHANGES_NEEDED
            : DialerImpression.Type.ANNOTATED_CALL_LOG_FORCE_REFRESH_NO_CHANGES_NEEDED;
      case REBUILT_AND_CHANGES_NEEDED:
        return checkDirty
            ? DialerImpression.Type.ANNOTATED_CALL_LOG_CHANGES_NEEDED
            : DialerImpression.Type.ANNOTATED_CALL_LOG_FORCE_REFRESH_CHANGES_NEEDED;
      default:
        throw new IllegalStateException("Unsupported result: " + refreshResult);
    }
  }
}
