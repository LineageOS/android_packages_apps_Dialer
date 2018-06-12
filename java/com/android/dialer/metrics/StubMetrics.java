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

package com.android.dialer.metrics;

import android.os.SystemClock;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Stub {@link Metrics} which simply logs debug messages to logcat. */
@ThreadSafe
@Singleton
public final class StubMetrics implements Metrics {

  private final ConcurrentMap<String, StubTimerEvent> namedEvents = new ConcurrentHashMap<>();
  private final ConcurrentMap<Integer, StubTimerEvent> unnamedEvents = new ConcurrentHashMap<>();

  @Inject
  StubMetrics() {}

  @Override
  public void startTimer(String timerEventName) {
    namedEvents.put(timerEventName, new StubTimerEvent());
  }

  @Override
  public Integer startUnnamedTimer() {
    StubTimerEvent stubTimerEvent = new StubTimerEvent();
    int id = stubTimerEvent.hashCode();
    LogUtil.d("StubMetrics.startUnnamedTimer", "started timer for id: %d", id);
    unnamedEvents.put(id, stubTimerEvent);
    return id;
  }

  @Override
  public void stopTimer(String timerEventName) {
    StubTimerEvent stubTimerEvent = namedEvents.remove(timerEventName);
    if (stubTimerEvent == null) {
      return;
    }

    LogUtil.d(
        "StubMetrics.stopTimer",
        "%s took %dms",
        timerEventName,
        SystemClock.elapsedRealtime() - stubTimerEvent.startTime);
  }

  @Override
  public void stopUnnamedTimer(int timerId, String timerEventName) {
    long startTime =
        Assert.isNotNull(
                unnamedEvents.remove(timerId),
                "no timer found for id: %d (%s)",
                timerId,
                timerEventName)
            .startTime;

    LogUtil.d(
        "StubMetrics.stopUnnamedTimer",
        "%s took %dms",
        timerEventName,
        SystemClock.elapsedRealtime() - startTime);
  }

  @Override
  public void startJankRecorder(String eventName) {
    LogUtil.d("StubMetrics.startJankRecorder", "started jank recorder for %s", eventName);
  }

  @Override
  public void stopJankRecorder(String eventName) {
    LogUtil.d("StubMetrics.startJankRecorder", "stopped jank recorder for %s", eventName);
  }

  @Override
  public void recordMemory(String memoryEventName) {
    LogUtil.d("StubMetrics.startJankRecorder", "recorded memory for %s", memoryEventName);
  }

  @Override
  public void recordBattery(String batteryEventName) {
    LogUtil.d("StubMetrics.recordBattery", "recorded battery for %s", batteryEventName);
  }

  private static class StubTimerEvent {
    final long startTime;

    StubTimerEvent() {
      this.startTime = SystemClock.elapsedRealtime();
    }
  }
}
