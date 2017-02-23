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

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.view.Display;
import com.android.dialer.common.LogUtil;

/** The normal PROXIMITY_SCREEN_OFF_WAKE_LOCK provided by the OS. */
public class SystemProximityWakeLock implements AnswerProximityWakeLock, DisplayListener {

  private static final String TAG = "SystemProximityWakeLock";

  private final Context context;
  private final PowerManager.WakeLock wakeLock;

  @Nullable private ScreenOnListener listener;

  public SystemProximityWakeLock(Context context) {
    this.context = context;
    wakeLock =
        context
            .getSystemService(PowerManager.class)
            .newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, TAG);
  }

  @Override
  public void acquire() {
    wakeLock.acquire();
    context.getSystemService(DisplayManager.class).registerDisplayListener(this, null);
  }

  @Override
  public void release() {
    wakeLock.release();
    context.getSystemService(DisplayManager.class).unregisterDisplayListener(this);
  }

  @Override
  public boolean isHeld() {
    return wakeLock.isHeld();
  }

  @Override
  public void setScreenOnListener(ScreenOnListener listener) {
    this.listener = listener;
  }

  @Override
  public void onDisplayAdded(int displayId) {}

  @Override
  public void onDisplayRemoved(int displayId) {}

  @Override
  public void onDisplayChanged(int displayId) {
    if (displayId == Display.DEFAULT_DISPLAY) {
      if (isDefaultDisplayOn(context)) {
        LogUtil.i("SystemProximityWakeLock.onDisplayChanged", "display turned on");
        if (listener != null) {
          listener.onScreenOn();
        }
      }
    }
  }

  private static boolean isDefaultDisplayOn(Context context) {
    Display display =
        context.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
    return display.getState() != Display.STATE_OFF;
  }
}
