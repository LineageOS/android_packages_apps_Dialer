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
import com.android.dialer.configprovider.ConfigProviderBindings;

/** An classifier trying to determine whether it is a human interacting with the phone or not. */
class HumanInteractionClassifier extends Classifier {

  private static final String CONFIG_ANSWER_FALSE_TOUCH_DETECTION_ENABLED =
      "answer_false_touch_detection_enabled";

  private final StrokeClassifier[] mStrokeClassifiers;
  private final GestureClassifier[] mGestureClassifiers;
  private final HistoryEvaluator mHistoryEvaluator;
  private final boolean mEnabled;

  HumanInteractionClassifier(Context context) {
    DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();

    // If the phone is rotated to landscape, the calculations would be wrong if xdpi and ydpi
    // were to be used separately. Due negligible differences in xdpi and ydpi we can just
    // take the average.
    // Note that xdpi and ydpi are the physical pixels per inch and are not affected by scaling.
    float dpi = (displayMetrics.xdpi + displayMetrics.ydpi) / 2.0f;
    mClassifierData = new ClassifierData(dpi, displayMetrics.heightPixels);
    mHistoryEvaluator = new HistoryEvaluator();
    mEnabled =
        ConfigProviderBindings.get(context)
            .getBoolean(CONFIG_ANSWER_FALSE_TOUCH_DETECTION_ENABLED, true);

    mStrokeClassifiers =
        new StrokeClassifier[] {
          new AnglesClassifier(mClassifierData),
          new SpeedClassifier(mClassifierData),
          new DurationCountClassifier(mClassifierData),
          new EndPointRatioClassifier(mClassifierData),
          new EndPointLengthClassifier(mClassifierData),
          new AccelerationClassifier(mClassifierData),
          new SpeedAnglesClassifier(mClassifierData),
          new LengthCountClassifier(mClassifierData),
          new DirectionClassifier(mClassifierData)
        };

    mGestureClassifiers =
        new GestureClassifier[] {
          new PointerCountClassifier(mClassifierData), new ProximityClassifier(mClassifierData)
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
    mClassifierData.update(event);

    for (StrokeClassifier c : mStrokeClassifiers) {
      c.onTouchEvent(event);
    }

    for (GestureClassifier c : mGestureClassifiers) {
      c.onTouchEvent(event);
    }

    int size = mClassifierData.getEndingStrokes().size();
    for (int i = 0; i < size; i++) {
      Stroke stroke = mClassifierData.getEndingStrokes().get(i);
      float evaluation = 0.0f;
      for (StrokeClassifier c : mStrokeClassifiers) {
        float e = c.getFalseTouchEvaluation(stroke);
        evaluation += e;
      }

      mHistoryEvaluator.addStroke(evaluation);
    }

    int action = event.getActionMasked();
    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
      float evaluation = 0.0f;
      for (GestureClassifier c : mGestureClassifiers) {
        float e = c.getFalseTouchEvaluation();
        evaluation += e;
      }
      mHistoryEvaluator.addGesture(evaluation);
    }

    mClassifierData.cleanUp(event);
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    for (Classifier c : mStrokeClassifiers) {
      c.onSensorChanged(event);
    }

    for (Classifier c : mGestureClassifiers) {
      c.onSensorChanged(event);
    }
  }

  boolean isFalseTouch() {
    float evaluation = mHistoryEvaluator.getEvaluation();
    return evaluation >= 5.0f;
  }

  public boolean isEnabled() {
    return mEnabled;
  }

  @Override
  public String getTag() {
    return "HIC";
  }
}
