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

package com.android.dialer.metrics.jank;

import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnScrollListener;
import com.android.dialer.metrics.Metrics;

/** Logs jank for {@link RecyclerView} scrolling events. */
public final class RecyclerViewJankLogger extends OnScrollListener {

  private final Metrics metrics;
  private final String eventName;

  private boolean isScrolling;

  public RecyclerViewJankLogger(Metrics metrics, String eventName) {
    this.metrics = metrics;
    this.eventName = eventName;
  }

  @Override
  public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
    if (!isScrolling && newState == RecyclerView.SCROLL_STATE_DRAGGING) {
      isScrolling = true;
      metrics.startJankRecorder(eventName);
    } else if (isScrolling && newState == RecyclerView.SCROLL_STATE_IDLE) {
      isScrolling = false;
      metrics.stopJankRecorder(eventName);
    }
  }
}
