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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.annotation.Nullable;
import com.android.dialer.common.LogUtil;

/**
 * A fake PROXIMITY_SCREEN_OFF_WAKE_LOCK implemented by the app. It will use {@link
 * PseudoScreenState} to fake a black screen when the proximity sensor is near.
 */
public class PseudoProximityWakeLock implements AnswerProximityWakeLock, SensorEventListener {

  private final Context context;
  private final PseudoScreenState pseudoScreenState;
  private final Sensor proximitySensor;

  @Nullable private ScreenOnListener listener;
  private boolean isHeld;

  public PseudoProximityWakeLock(Context context, PseudoScreenState pseudoScreenState) {
    this.context = context;
    this.pseudoScreenState = pseudoScreenState;
    pseudoScreenState.setOn(true);
    proximitySensor =
        context.getSystemService(SensorManager.class).getDefaultSensor(Sensor.TYPE_PROXIMITY);
  }

  @Override
  public void acquire() {
    isHeld = true;
    context
        .getSystemService(SensorManager.class)
        .registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
  }

  @Override
  public void release() {
    isHeld = false;
    context.getSystemService(SensorManager.class).unregisterListener(this);
    pseudoScreenState.setOn(true);
  }

  @Override
  public boolean isHeld() {
    return isHeld;
  }

  @Override
  public void setScreenOnListener(ScreenOnListener listener) {
    this.listener = listener;
  }

  @Override
  public void onSensorChanged(SensorEvent sensorEvent) {
    boolean near = sensorEvent.values[0] < sensorEvent.sensor.getMaximumRange();
    LogUtil.i("AnswerProximitySensor.PseudoProximityWakeLock.onSensorChanged", "near: " + near);
    pseudoScreenState.setOn(!near);
    if (!near && listener != null) {
      listener.onScreenOn();
    }
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int i) {}
}
