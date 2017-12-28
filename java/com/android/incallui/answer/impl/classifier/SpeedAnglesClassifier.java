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

import android.util.ArrayMap;
import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A classifier which for each point from a stroke, it creates a point on plane with coordinates
 * (timeOffsetNano, distanceCoveredUpToThisPoint) (scaled by DURATION_SCALE and LENGTH_SCALE) and
 * then it calculates the angle variance of these points like the class {@link AnglesClassifier}
 * (without splitting it into two parts). The classifier ignores the last point of a stroke because
 * the UP event comes in with some delay and this ruins the smoothness of this curve. Additionally,
 * the classifier classifies calculates the percentage of angles which value is in [PI -
 * ANGLE_DEVIATION, 2* PI) interval. The reason why the classifier does that is because the speed of
 * a good stroke is most often increases, so most of these angels should be in this interval.
 */
class SpeedAnglesClassifier extends StrokeClassifier {
  private Map<Stroke, Data> strokeMap = new ArrayMap<>();

  public SpeedAnglesClassifier(ClassifierData classifierData) {
    this.classifierData = classifierData;
  }

  @Override
  public String getTag() {
    return "SPD_ANG";
  }

  @Override
  public void onTouchEvent(MotionEvent event) {
    int action = event.getActionMasked();

    if (action == MotionEvent.ACTION_DOWN) {
      strokeMap.clear();
    }

    for (int i = 0; i < event.getPointerCount(); i++) {
      Stroke stroke = classifierData.getStroke(event.getPointerId(i));

      if (strokeMap.get(stroke) == null) {
        strokeMap.put(stroke, new Data());
      }

      if (action != MotionEvent.ACTION_UP
          && action != MotionEvent.ACTION_CANCEL
          && !(action == MotionEvent.ACTION_POINTER_UP && i == event.getActionIndex())) {
        strokeMap.get(stroke).addPoint(stroke.getPoints().get(stroke.getPoints().size() - 1));
      }
    }
  }

  @Override
  public float getFalseTouchEvaluation(Stroke stroke) {
    Data data = strokeMap.get(stroke);
    return SpeedVarianceEvaluator.evaluate(data.getAnglesVariance())
        + SpeedAnglesPercentageEvaluator.evaluate(data.getAnglesPercentage());
  }

  private static class Data {
    private static final float DURATION_SCALE = 1e8f;
    private static final float LENGTH_SCALE = 1.0f;
    private static final float ANGLE_DEVIATION = (float) Math.PI / 10.0f;

    private List<Point> lastThreePoints = new ArrayList<>();
    private Point previousPoint;
    private float previousAngle;
    private float sumSquares;
    private float sum;
    private float count;
    private float dist;
    private float anglesCount;
    private float acceleratingAngles;

    public Data() {
      previousPoint = null;
      previousAngle = (float) Math.PI;
      sumSquares = 0.0f;
      sum = 0.0f;
      count = 1.0f;
      dist = 0.0f;
      anglesCount = acceleratingAngles = 0.0f;
    }

    public void addPoint(Point point) {
      if (previousPoint != null) {
        dist += previousPoint.dist(point);
      }

      previousPoint = point;
      Point speedPoint =
          new Point((float) point.timeOffsetNano / DURATION_SCALE, dist / LENGTH_SCALE);

      // Checking if the added point is different than the previously added point
      // Repetitions are being ignored so that proper angles are calculated.
      if (lastThreePoints.isEmpty()
          || !lastThreePoints.get(lastThreePoints.size() - 1).equals(speedPoint)) {
        lastThreePoints.add(speedPoint);
        if (lastThreePoints.size() == 4) {
          lastThreePoints.remove(0);

          float angle =
              lastThreePoints.get(1).getAngle(lastThreePoints.get(0), lastThreePoints.get(2));

          anglesCount++;
          if (angle >= (float) Math.PI - ANGLE_DEVIATION) {
            acceleratingAngles++;
          }

          float difference = angle - previousAngle;
          sum += difference;
          sumSquares += difference * difference;
          count += 1.0f;
          previousAngle = angle;
        }
      }
    }

    public float getAnglesVariance() {
      return sumSquares / count - (sum / count) * (sum / count);
    }

    public float getAnglesPercentage() {
      if (anglesCount == 0.0f) {
        return 1.0f;
      }
      return (acceleratingAngles) / anglesCount;
    }
  }
}
