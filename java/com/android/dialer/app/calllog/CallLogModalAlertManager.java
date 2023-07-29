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

package com.android.dialer.app.calllog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.dialer.R;
import com.android.dialer.app.alert.AlertManager;

/**
 * Alert manager controls modal view to show message in call log. When modal view is shown, regular
 * call log will be hidden.
 */
public class CallLogModalAlertManager implements AlertManager {

  interface Listener {
    void onShowModalAlert(boolean show);
  }

  private final Listener listener;
  private final ViewGroup parent;
  private final ViewGroup container;
  private final LayoutInflater inflater;

  public CallLogModalAlertManager(LayoutInflater inflater, ViewGroup parent, Listener listener) {
    this.inflater = inflater;
    this.parent = parent;
    this.listener = listener;
    container = (ViewGroup) parent.findViewById(R.id.modal_message_container);
  }

  @Override
  public View inflate(int layoutId) {
    return inflater.inflate(layoutId, parent, false);
  }

  @Override
  public void add(View view) {
    if (contains(view)) {
      return;
    }
    container.addView(view);
    listener.onShowModalAlert(true);
  }

  @Override
  public void clear() {
    container.removeAllViews();
    listener.onShowModalAlert(false);
  }

  public boolean isEmpty() {
    return container.getChildCount() == 0;
  }

  public boolean contains(View view) {
    return container.indexOfChild(view) != -1;
  }
}
