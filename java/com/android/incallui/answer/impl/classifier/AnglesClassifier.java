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

import android.util.ArrayMap;
import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A classifier which calculates the variance of differences between successive angles in a stroke.
 * For each stroke it keeps its last three points. If some successive points are the same, it
 * ignores the repetitions. If a new point is added, the classifier calculates the angle between the
 * last three points. After that, it calculates the difference between this angle and the previously
 * calculated angle. Then it calculates the variance of the differences from a stroke. To the
 * differences there is artificially added value 0.0 and the difference between the first angle and
 * PI (angles are in radians). It helps with strokes which have few points and punishes more strokes
 * which are not smooth.
 *
 * <p>This classifier also tries to split the stroke into two parts in the place in which the
 * biggest angle is. It calculates the angle variance of the two parts and sums them up. The reason
 * the classifier is doing this, is because some human swipes at the beginning go for a moment in
 * one direction and then they rapidly change direction for the rest of the stroke (like a tick).
 * The final result is the minimum of angle variance of the whole stroke and the sum of angle
 * variances of the two parts split up. The classifier tries the tick option only if the first part
 * is shorter than the second part.
 *
 * <p>Additionally, the classifier classifies the angles as left angles (those angles which value is
 * in [0.0, PI - ANGLE_DEVIATION) interval), straight angles ([PI - ANGLE_DEVIATION, PI +
 * ANGLE_DEVIATION] interval) and right angles ((PI + ANGLE_DEVIATION, 2 * PI) interval) and then
 * calculates the percentage of angles which are in the same direction (straight angles can be left
 * angels or right angles)
 */
class AnglesClassifier extends StrokeClassifier {
  private Map<Stroke, Data> strokeMap = new ArrayMap<>();

  public AnglesClassifier(ClassifierData classifierData) {
    this.classifierData = classifierData;
  }

  @Override
  public String getTag() {
    return "ANG";
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
      strokeMap.get(stroke).addPoint(stroke.getPoints().get(stroke.getPoints().size() - 1));
    }
  }

  @Override
  public float getFalseTouchEvaluation(Stroke stroke) {
    Data data = strokeMap.get(stroke);
    return AnglesVarianceEvaluator.evaluate(data.getAnglesVariance())
        + AnglesPercentageEvaluator.evaluate(data.getAnglesPercentage());
  }

  private static class Data {
    private static final float ANGLE_DEVIATION = (float) Math.PI / 20.0f;
    private static final float MIN_MOVE_DIST_DP = .01f;

    private List<Point> lastThreePoints = new ArrayList<>();
    private float firstAngleVariance;
    private float previousAngle;
    private float biggestAngle;
    private float sumSquares;
    private float secondSumSquares;
    private float sum;
    private float secondSum;
    private float count;
    private float secondCount;
    private float firstLength;
    private float length;
    private float anglesCount;
    private float leftAngles;
    private float rightAngles;
    private float straightAngles;

    public Data() {
      firstAngleVariance = 0.0f;
      previousAngle = (float) Math.PI;
      biggestAngle = 0.0f;
      sumSquares = secondSumSquares = 0.0f;
      sum = secondSum = 0.0f;
      count = secondCount = 1.0f;
      length = firstLength = 0.0f;
      anglesCount = leftAngles = rightAngles = straightAngles = 0.0f;
    }

    public void addPoint(Point point) {
      // Checking if the added point is different than the previously added point
      // Repetitions and short distances are being ignored so that proper angles are calculated.
      if (lastThreePoints.isEmpty()
          || (!lastThreePoints.get(lastThreePoints.size() - 1).equals(point)
              && (lastThreePoints.get(lastThreePoints.size() - 1).dist(point)
                  > MIN_MOVE_DIST_DP))) {
        if (!lastThreePoints.isEmpty()) {
          length += lastThreePoints.get(lastThreePoints.size() - 1).dist(point);
        }
        lastThreePoints.add(point);
        if (lastThreePoints.size() == 4) {
          lastThreePoints.remove(0);

          float angle =
              lastThreePoints.get(1).getAngle(lastThreePoints.get(0), lastThreePoints.get(2));

          anglesCount++;
          if (angle < Math.PI - ANGLE_DEVIATION) {
            leftAngles++;
          } else if (angle <= Math.PI + ANGLE_DEVIATION) {
            straightAngles++;
          } else {
            rightAngles++;
          }

          float difference = angle - previousAngle;

          // If this is the biggest angle of the stroke so then we save the value of
          // the angle variance so far and start to count the values for the angle
          // variance of the second part.
          if (biggestAngle < angle) {
            biggestAngle = angle;
            firstLength = length;
            firstAngleVariance = getAnglesVariance(sumSquares, sum, count);
            secondSumSquares = 0.0f;
            secondSum = 0.0f;
            secondCount = 1.0f;
          } else {
            secondSum += difference;
            secondSumSquares += difference * difference;
            secondCount += 1.0f;
          }

          sum += difference;
          sumSquares += difference * difference;
          count += 1.0f;
          previousAngle = angle;
        }
      }
    }

    public float getAnglesVariance(float sumSquares, float sum, float count) {
      return sumSquares / count - (sum / count) * (sum / count);
    }

    public float getAnglesVariance() {
      float anglesVariance = getAnglesVariance(sumSquares, sum, count);
      if (firstLength < length / 2f) {
        anglesVariance =
            Math.min(
                anglesVariance,
                firstAngleVariance + getAnglesVariance(secondSumSquares, secondSum, secondCount));
      }
      return anglesVariance;
    }

    public float getAnglesPercentage() {
      if (anglesCount == 0.0f) {
        return 1.0f;
      }
      return (Math.max(leftAngles, rightAngles) + straightAngles) / anglesCount;
    }
  }
}
