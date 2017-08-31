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

package com.android.dialer.performancereport;

import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.widget.AbsListView;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.UiAction;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Tracks UI performance for a call. */
public final class PerformanceReport {

  private static final long INVALID_TIME = -1;
  private static final long ACTIVE_DURATION = TimeUnit.MINUTES.toMillis(5);

  private static final List<UiAction.Type> actions = new ArrayList<>();
  private static final List<Long> actionTimestamps = new ArrayList<>();

  private static final RecyclerView.OnScrollListener recordOnScrollListener =
      new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
          if (newState == RecyclerView.SCROLL_STATE_SETTLING) {
            PerformanceReport.recordClick(UiAction.Type.SCROLL);
          }
          super.onScrollStateChanged(recyclerView, newState);
        }
      };

  private static boolean recording = false;
  private static long appLaunchTimeMillis = INVALID_TIME;
  private static long firstClickTimeMillis = INVALID_TIME;
  private static long lastActionTimeMillis = INVALID_TIME;

  @Nullable private static UiAction.Type ignoreActionOnce = null;

  private static int startingTabIndex = -1; // UNKNOWN

  private PerformanceReport() {}

  public static void startRecording() {
    LogUtil.enterBlock("PerformanceReport.startRecording");

    appLaunchTimeMillis = SystemClock.elapsedRealtime();
    lastActionTimeMillis = appLaunchTimeMillis;
    if (!actions.isEmpty()) {
      actions.clear();
      actionTimestamps.clear();
    }
    recording = true;
  }

  public static void stopRecording() {
    LogUtil.enterBlock("PerformanceReport.stopRecording");
    recording = false;
  }

  public static void recordClick(UiAction.Type action) {
    if (!recording) {
      return;
    }

    if (action == ignoreActionOnce) {
      LogUtil.i("PerformanceReport.recordClick", "%s is ignored", action.toString());
      ignoreActionOnce = null;
      return;
    }
    ignoreActionOnce = null;

    LogUtil.v("PerformanceReport.recordClick", action.toString());

    // Timeout
    long currentTime = SystemClock.elapsedRealtime();
    if (currentTime - lastActionTimeMillis > ACTIVE_DURATION) {
      startRecording();
      recordClick(action);
      return;
    }

    lastActionTimeMillis = currentTime;
    if (actions.isEmpty()) {
      firstClickTimeMillis = currentTime;
    }
    actions.add(action);
    actionTimestamps.add(currentTime - appLaunchTimeMillis);
  }

  public static void recordScrollStateChange(int scrollState) {
    if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
      recordClick(UiAction.Type.SCROLL);
    }
  }

  public static void logOnScrollStateChange(RecyclerView recyclerView) {
    // Remove the listener in case it was added before
    recyclerView.removeOnScrollListener(recordOnScrollListener);
    recyclerView.addOnScrollListener(recordOnScrollListener);
  }

  public static boolean isRecording() {
    return recording;
  }

  public static long getTimeSinceAppLaunch() {
    if (appLaunchTimeMillis == INVALID_TIME) {
      return INVALID_TIME;
    }
    return SystemClock.elapsedRealtime() - appLaunchTimeMillis;
  }

  public static long getTimeSinceFirstClick() {
    if (firstClickTimeMillis == INVALID_TIME) {
      return INVALID_TIME;
    }
    return SystemClock.elapsedRealtime() - firstClickTimeMillis;
  }

  public static List<UiAction.Type> getActions() {
    return actions;
  }

  public static List<Long> getActionTimestamps() {
    return actionTimestamps;
  }

  public static int getStartingTabIndex() {
    return startingTabIndex;
  }

  public static void setStartingTabIndex(int startingTabIndex) {
    PerformanceReport.startingTabIndex = startingTabIndex;
  }

  public static void setIgnoreActionOnce(@Nullable UiAction.Type ignoreActionOnce) {
    PerformanceReport.ignoreActionOnce = ignoreActionOnce;
    LogUtil.i(
        "PerformanceReport.setIgnoreActionOnce",
        "next action will be ignored once if it is %s",
        ignoreActionOnce.toString());
  }
}
