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

package com.android.incallui.answerproximitysensor;

import android.util.ArraySet;
import java.util.Set;

/**
 * Stores a fake screen on/off state for the {@link InCallActivity}. If InCallActivity see the state
 * is off, it will draw a black view over the activity pretending the screen is off.
 *
 * <p>If the screen is already touched when the screen is turned on, the OS behavior is sending a
 * new DOWN event once the point started moving and then behave as a normal gesture. To prevent
 * accidental answer/rejects, touches that started when the screen is off should be ignored.
 *
 * <p>b/31499931 on certain devices with N-DR1, if the screen is already touched when the screen is
 * turned on, a "DOWN MOVE UP" will be sent for each movement before the touch is actually released.
 * These events is hard to discern from other normal events, and keeping the screen on reduces its'
 * probability.
 */
public class PseudoScreenState {

  /** Notifies when the on state has changed. */
  public interface StateChangedListener {
    void onPseudoScreenStateChanged(boolean isOn);
  }

  private final Set<StateChangedListener> listeners = new ArraySet<>();

  private boolean on = true;

  public boolean isOn() {
    return on;
  }

  public void setOn(boolean value) {
    if (on != value) {
      on = value;
      for (StateChangedListener listener : listeners) {
        listener.onPseudoScreenStateChanged(on);
      }
    }
  }

  public void addListener(StateChangedListener listener) {
    listeners.add(listener);
  }

  public void removeListener(StateChangedListener listener) {
    listeners.remove(listener);
  }
}
