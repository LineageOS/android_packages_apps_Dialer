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

import java.util.ArrayList;

/**
 * Contains data about a stroke (a single trace, all the events from a given id from the
 * DOWN/POINTER_DOWN event till the UP/POINTER_UP/CANCEL event.)
 */
class Stroke {

  private static final float NANOS_TO_SECONDS = 1e9f;

  private ArrayList<Point> points = new ArrayList<>();
  private long startTimeNano;
  private long endTimeNano;
  private float length;
  private final float dpi;

  public Stroke(long eventTimeNano, float dpi) {
    this.dpi = dpi;
    startTimeNano = endTimeNano = eventTimeNano;
  }

  public void addPoint(float x, float y, long eventTimeNano) {
    endTimeNano = eventTimeNano;
    Point point = new Point(x / dpi, y / dpi, eventTimeNano - startTimeNano);
    if (!points.isEmpty()) {
      length += points.get(points.size() - 1).dist(point);
    }
    points.add(point);
  }

  public int getCount() {
    return points.size();
  }

  public float getTotalLength() {
    return length;
  }

  public float getEndPointLength() {
    return points.get(0).dist(points.get(points.size() - 1));
  }

  public long getDurationNanos() {
    return endTimeNano - startTimeNano;
  }

  public float getDurationSeconds() {
    return (float) getDurationNanos() / NANOS_TO_SECONDS;
  }

  public ArrayList<Point> getPoints() {
    return points;
  }
}
