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
import android.hardware.SensorEvent;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import com.android.dialer.configprovider.ConfigProviderComponent;

/** An classifier trying to determine whether it is a human interacting with the phone or not. */
class HumanInteractionClassifier extends Classifier {

  private static final String CONFIG_ANSWER_FALSE_TOUCH_DETECTION_ENABLED =
      "answer_false_touch_detection_enabled";

  private final StrokeClassifier[] strokeClassifiers;
  private final GestureClassifier[] gestureClassifiers;
  private final HistoryEvaluator historyEvaluator;
  private final boolean enabled;

  HumanInteractionClassifier(Context context) {
    DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();

    // If the phone is rotated to landscape, the calculations would be wrong if xdpi and ydpi
    // were to be used separately. Due negligible differences in xdpi and ydpi we can just
    // take the average.
    // Note that xdpi and ydpi are the physical pixels per inch and are not affected by scaling.
    float dpi = (displayMetrics.xdpi + displayMetrics.ydpi) / 2.0f;
    classifierData = new ClassifierData(dpi, displayMetrics.heightPixels);
    historyEvaluator = new HistoryEvaluator();
    enabled =
        ConfigProviderComponent.get(context)
            .getConfigProvider()
            .getBoolean(CONFIG_ANSWER_FALSE_TOUCH_DETECTION_ENABLED, true);

    strokeClassifiers =
        new StrokeClassifier[] {
          new AnglesClassifier(classifierData),
          new SpeedClassifier(classifierData),
          new DurationCountClassifier(classifierData),
          new EndPointRatioClassifier(classifierData),
          new EndPointLengthClassifier(classifierData),
          new AccelerationClassifier(classifierData),
          new SpeedAnglesClassifier(classifierData),
          new LengthCountClassifier(classifierData),
          new DirectionClassifier(classifierData)
        };

    gestureClassifiers =
        new GestureClassifier[] {
          new PointerCountClassifier(classifierData), new ProximityClassifier(classifierData)
        };
  }

  @Override
  public void onTouchEvent(MotionEvent event) {

    // If the user is dragging down the notification, they might want to drag it down
    // enough to see the content, read it for a while and then lift the finger to open
    // the notification. This kind of motion scores very bad in the Classifier so the
    // MotionEvents which are close to the current position of the finger are not
    // sent to the classifiers until the finger moves far enough. When the finger if lifted
    // up, the last MotionEvent which was far enough from the finger is set as the final
    // MotionEvent and sent to the Classifiers.
    addTouchEvent(event);
  }

  private void addTouchEvent(MotionEvent event) {
    classifierData.update(event);

    for (StrokeClassifier c : strokeClassifiers) {
      c.onTouchEvent(event);
    }

    for (GestureClassifier c : gestureClassifiers) {
      c.onTouchEvent(event);
    }

    int size = classifierData.getEndingStrokes().size();
    for (int i = 0; i < size; i++) {
      Stroke stroke = classifierData.getEndingStrokes().get(i);
      float evaluation = 0.0f;
      for (StrokeClassifier c : strokeClassifiers) {
        float e = c.getFalseTouchEvaluation(stroke);
        evaluation += e;
      }

      historyEvaluator.addStroke(evaluation);
    }

    int action = event.getActionMasked();
    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
      float evaluation = 0.0f;
      for (GestureClassifier c : gestureClassifiers) {
        float e = c.getFalseTouchEvaluation();
        evaluation += e;
      }
      historyEvaluator.addGesture(evaluation);
    }

    classifierData.cleanUp(event);
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    for (Classifier c : strokeClassifiers) {
      c.onSensorChanged(event);
    }

    for (Classifier c : gestureClassifiers) {
      c.onSensorChanged(event);
    }
  }

  boolean isFalseTouch() {
    float evaluation = historyEvaluator.getEvaluation();
    return evaluation >= 5.0f;
  }

  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public String getTag() {
    return "HIC";
  }
}
