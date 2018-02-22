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

package com.android.incallui;

import android.content.Context;
import com.android.incallui.rtt.protocol.RttCallScreen;
import com.android.incallui.rtt.protocol.RttCallScreenDelegate;

/**
 * Logic related to the {@link RttCallScreen} and for managing changes to the RTT calling surfaces
 * based on other user interface events and incoming events.
 */
public class RttCallPresenter implements RttCallScreenDelegate {

  private Context appContext;
  private RttCallScreen rttCallScreen;

  @Override
  public void initRttCallScreenDelegate(Context context, RttCallScreen rttCallScreen) {
    this.appContext = context.getApplicationContext();
    this.rttCallScreen = rttCallScreen;
  }

  @Override
  public void onRttCallScreenUiReady() {}

  @Override
  public void onRttCallScreenUiUnready() {}
}
