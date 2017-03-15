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

package com.android.dialer.simulator;

import android.content.Context;
import dagger.Subcomponent;
import com.android.dialer.simulator.impl.SimulatorImpl;

/** Subcomponent that can be used to access the simulator implementation. */
public class SimulatorComponent {
  private static SimulatorComponent instance;
  private Simulator simulator;

  public Simulator getSimulator() {
    if (simulator == null) {
        simulator = new SimulatorImpl();
    }
    return simulator;
  }

  public static SimulatorComponent get(Context context) {
    if (instance == null) {
        instance = new SimulatorComponent();
    }
    return instance;
  }

  /** Used to refer to the root application component. */
  public interface HasComponent {
    SimulatorComponent simulatorComponent();
  }
}
