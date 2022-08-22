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
 * limitations under the License
 */

package com.android.incallui.answerproximitysensor;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.PowerManager;
import android.os.Trace;
import android.view.Display;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProviderComponent;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.DialerCallListener;
import com.android.incallui.call.state.DialerCallState;

import com.android.incallui.R;

/**
 * This class prevents users from accidentally answering calls by keeping the screen off until the
 * proximity sensor is unblocked. If the screen is already on or if this is a call waiting call then
 * nothing is done.
 */
public class AnswerProximitySensor
    implements DialerCallListener, AnswerProximityWakeLock.ScreenOnListener {

  private static final String CONFIG_ANSWER_PROXIMITY_SENSOR_ENABLED =
      "answer_proximity_sensor_enabled";
  private static final String CONFIG_ANSWER_PSEUDO_PROXIMITY_WAKE_LOCK_ENABLED =
      "answer_pseudo_proximity_wake_lock_enabled";

  private final DialerCall call;
  private final AnswerProximityWakeLock answerProximityWakeLock;

  public static boolean shouldUse(Context context, DialerCall call) {
    Trace.beginSection("AnswerProximitySensor.shouldUse");
    // Don't use the AnswerProximitySensor for call waiting and other states. Those states are
    // handled by the general ProximitySensor code.
    if (call.getState() != DialerCallState.INCOMING) {
      LogUtil.i("AnswerProximitySensor.shouldUse", "call state is not incoming");
      Trace.endSection();
      return false;
    }

    if (!ConfigProviderComponent.get(context)
        .getConfigProvider()
        .getBoolean(CONFIG_ANSWER_PROXIMITY_SENSOR_ENABLED, true)) {
      LogUtil.i("AnswerProximitySensor.shouldUse", "disabled by config");
      Trace.endSection();
      return false;
    }

    if (!context.getResources().getBoolean(R.bool.config_answer_proximity_sensor_enabled)) {
      LogUtil.i("AnswerProximitySensor.shouldUse", "disabled by overlay");
      return false;
    }

    if (!context
        .getSystemService(PowerManager.class)
        .isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
      LogUtil.i("AnswerProximitySensor.shouldUse", "wake lock level not supported");
      Trace.endSection();
      return false;
    }

    if (isDefaultDisplayOn(context)) {
      LogUtil.i("AnswerProximitySensor.shouldUse", "display is already on");
      Trace.endSection();
      return false;
    }

    Trace.endSection();
    return true;
  }

  public AnswerProximitySensor(
      Context context, DialerCall call, PseudoScreenState pseudoScreenState) {
    Trace.beginSection("AnswerProximitySensor Constructor");
    this.call = call;

    LogUtil.i("AnswerProximitySensor.constructor", "acquiring lock");
    if (ConfigProviderComponent.get(context)
        .getConfigProvider()
        .getBoolean(CONFIG_ANSWER_PSEUDO_PROXIMITY_WAKE_LOCK_ENABLED, true)) {
      answerProximityWakeLock = new PseudoProximityWakeLock(context, pseudoScreenState);
    } else {
      // TODO(twyen): choose a wake lock implementation base on framework/device.
      // These bugs requires the PseudoProximityWakeLock workaround:
      // a bug Proximity sensor not working on M
      // a bug fautly touch input when screen is off on marlin/sailfish
      answerProximityWakeLock = new SystemProximityWakeLock(context);
    }
    answerProximityWakeLock.setScreenOnListener(this);
    answerProximityWakeLock.acquire();

    call.addListener(this);
    Trace.endSection();
  }

  private void cleanup() {
    Trace.beginSection("AnswerProximitySensor.Cleanup");
    call.removeListener(this);
    releaseProximityWakeLock();
    Trace.endSection();
  }

  private void releaseProximityWakeLock() {
    if (answerProximityWakeLock.isHeld()) {
      LogUtil.i("AnswerProximitySensor.releaseProximityWakeLock", "releasing lock");
      answerProximityWakeLock.release();
    }
  }

  private static boolean isDefaultDisplayOn(Context context) {
    Display display =
        context.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
    return display.getState() == Display.STATE_ON;
  }

  @Override
  public void onDialerCallDisconnect() {
    LogUtil.i("AnswerProximitySensor.onDialerCallDisconnect", null);
    cleanup();
  }

  @Override
  public void onDialerCallUpdate() {
    if (call.getState() != DialerCallState.INCOMING) {
      LogUtil.i("AnswerProximitySensor.onDialerCallUpdate", "no longer incoming, cleaning up");
      cleanup();
    }
  }

  @Override
  public void onDialerCallChildNumberChange() {}

  @Override
  public void onDialerCallLastForwardedNumberChange() {}

  @Override
  public void onDialerCallUpgradeToVideo() {}

  @Override
  public void onWiFiToLteHandover() {}

  @Override
  public void onHandoverToWifiFailure() {}

  @Override
  public void onInternationalCallOnWifi() {}

  @Override
  public void onEnrichedCallSessionUpdate() {}

  @Override
  public void onDialerCallSessionModificationStateChange() {}

  @Override
  public void onScreenOn() {
    cleanup();
  }
}
