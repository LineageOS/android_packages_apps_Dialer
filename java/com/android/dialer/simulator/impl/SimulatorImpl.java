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

package com.android.dialer.simulator.impl;

import android.content.Context;
import android.view.ActionProvider;
import com.android.dialer.buildtype.BuildType;
import com.android.dialer.common.LogUtil;
import com.android.dialer.simulator.Simulator;
import javax.inject.Inject;

/** The entry point for the simulator feature. */
final class SimulatorImpl implements Simulator {
  @Inject
  public SimulatorImpl() {}

  @Override
  public boolean shouldShow() {
    return BuildType.get() == BuildType.BUGFOOD || LogUtil.isDebugEnabled();
  }

  @Override
  public ActionProvider getActionProvider(Context context) {
    return new SimulatorActionProvider(context);
  }
}
