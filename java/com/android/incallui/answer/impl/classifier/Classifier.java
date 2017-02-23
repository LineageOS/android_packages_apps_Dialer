/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.hardware.SensorEvent;
import android.view.MotionEvent;

/** An abstract class for classifiers for touch and sensor events. */
abstract class Classifier {

  /** Contains all the information about touch events from which the classifier can query */
  protected ClassifierData mClassifierData;

  /** Informs the classifier that a new touch event has occurred */
  public void onTouchEvent(MotionEvent event) {}

  /** Informs the classifier that a sensor change occurred */
  public void onSensorChanged(SensorEvent event) {}

  public abstract String getTag();
}
