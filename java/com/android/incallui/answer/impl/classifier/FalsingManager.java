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

package com.android.incallui.answer.impl.classifier;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;
import android.os.Trace;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityManager;

/**
 * When the phone is locked, listens to touch, sensor and phone events and sends them to
 * HumanInteractionClassifier to determine if touches are coming from a human.
 */
public class FalsingManager implements SensorEventListener {
  private static final int[] CLASSIFIER_SENSORS =
      new int[] {
        Sensor.TYPE_PROXIMITY,
      };

  private final SensorManager sensorManager;
  private final HumanInteractionClassifier humanInteractionClassifier;
  private final AccessibilityManager accessibilityManager;

  private boolean sessionActive = false;
  private boolean screenOn;

  public FalsingManager(Context context) {
    sensorManager = context.getSystemService(SensorManager.class);
    accessibilityManager = context.getSystemService(AccessibilityManager.class);
    humanInteractionClassifier = new HumanInteractionClassifier(context);
    screenOn = context.getSystemService(PowerManager.class).isInteractive();
  }

  /** Returns {@code true} iff the FalsingManager is enabled and able to classify touches */
  public boolean isEnabled() {
    return humanInteractionClassifier.isEnabled();
  }

  /**
   * Returns {@code true} iff the classifier determined that this is not a human interacting with
   * the phone.
   */
  public boolean isFalseTouch() {
    // Touch exploration triggers false positives in the classifier and
    // already sufficiently prevents false unlocks.
    return !accessibilityManager.isTouchExplorationEnabled()
        && humanInteractionClassifier.isFalseTouch();
  }

  /**
   * Should be called when the screen turns on and the related Views become visible. This will start
   * tracking changes if the manager is enabled.
   */
  public void onScreenOn() {
    screenOn = true;
    sessionEntrypoint();
  }

  /**
   * Should be called when the screen turns off or the related Views are no longer visible. This
   * will cause the manager to stop tracking changes.
   */
  public void onScreenOff() {
    screenOn = false;
    sessionExitpoint();
  }

  /**
   * Should be called when a new touch event has been received and should be classified.
   *
   * @param event MotionEvent to be classified as human or false.
   */
  public void onTouchEvent(MotionEvent event) {
    if (sessionActive) {
      humanInteractionClassifier.onTouchEvent(event);
    }
  }

  @Override
  public synchronized void onSensorChanged(SensorEvent event) {
    humanInteractionClassifier.onSensorChanged(event);
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {}

  private boolean shouldSessionBeActive() {
    return isEnabled() && screenOn;
  }

  private boolean sessionEntrypoint() {
    if (!sessionActive && shouldSessionBeActive()) {
      onSessionStart();
      return true;
    }
    return false;
  }

  private void sessionExitpoint() {
    if (sessionActive && !shouldSessionBeActive()) {
      sessionActive = false;
      sensorManager.unregisterListener(this);
    }
  }

  private void onSessionStart() {
    sessionActive = true;

    if (humanInteractionClassifier.isEnabled()) {
      registerSensors(CLASSIFIER_SENSORS);
    }
  }

  private void registerSensors(int[] sensors) {
    Trace.beginSection("FalsingManager.registerSensors");
    for (int sensorType : sensors) {
      Trace.beginSection("get sensor " + sensorType);
      Sensor s = sensorManager.getDefaultSensor(sensorType);
      Trace.endSection();
      if (s != null) {
        Trace.beginSection("register");
        sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME);
        Trace.endSection();
      }
    }
    Trace.endSection();
  }
}
