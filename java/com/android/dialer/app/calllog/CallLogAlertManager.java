/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.app.calllog;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.app.R;
import com.android.dialer.app.alert.AlertManager;
import com.android.dialer.common.Assert;

/** Manages "alerts" to be shown at the top of an call log to gain the user's attention. */
public class CallLogAlertManager implements AlertManager {

  private final CallLogAdapter adapter;
  private final View view;
  private final LayoutInflater inflater;
  private final ViewGroup parent;
  private final ViewGroup container;

  public CallLogAlertManager(CallLogAdapter adapter, LayoutInflater inflater, ViewGroup parent) {
    this.adapter = adapter;
    this.inflater = inflater;
    this.parent = parent;
    view = inflater.inflate(R.layout.call_log_alert_item, parent, false);
    container = (ViewGroup) view.findViewById(R.id.container);
  }

  @Override
  public View inflate(int layoutId) {
    return inflater.inflate(layoutId, container, false);
  }

  public RecyclerView.ViewHolder createViewHolder(ViewGroup parent) {
    Assert.checkArgument(
        parent == this.parent,
        "createViewHolder should be called with the same parent in constructor");
    return new AlertViewHolder(view);
  }

  public boolean isEmpty() {
    return container.getChildCount() == 0;
  }

  public boolean contains(View view) {
    return container.indexOfChild(view) != -1;
  }

  @Override
  public void clear() {
    container.removeAllViews();
    adapter.notifyItemRemoved(CallLogAdapter.ALERT_POSITION);
  }

  @Override
  public void add(View view) {
    if (contains(view)) {
      return;
    }
    container.addView(view);
    if (container.getChildCount() == 1) {
      // Was empty before
      adapter.notifyItemInserted(CallLogAdapter.ALERT_POSITION);
    }
  }

  /**
   * Does nothing. The view this ViewHolder show is directly managed by {@link CallLogAlertManager}
   */
  private static class AlertViewHolder extends RecyclerView.ViewHolder {
    private AlertViewHolder(View view) {
      super(view);
    }
  }
}
