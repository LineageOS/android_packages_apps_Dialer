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

import android.util.SparseArray;
import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Contains data which is used to classify interaction sequences on the lockscreen. It does, for
 * example, provide information on the current touch state.
 */
class ClassifierData {
  private SparseArray<Stroke> currentStrokes = new SparseArray<>();
  private ArrayList<Stroke> endingStrokes = new ArrayList<>();
  private final float dpi;
  private final float screenHeight;

  public ClassifierData(float dpi, float screenHeight) {
    this.dpi = dpi;
    this.screenHeight = screenHeight / dpi;
  }

  public void update(MotionEvent event) {
    endingStrokes.clear();
    int action = event.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      currentStrokes.clear();
    }

    for (int i = 0; i < event.getPointerCount(); i++) {
      int id = event.getPointerId(i);
      if (currentStrokes.get(id) == null) {
        // TODO (keyboardr): See if there's a way to use event.getEventTimeNanos() instead
        currentStrokes.put(
            id, new Stroke(TimeUnit.MILLISECONDS.toNanos(event.getEventTime()), dpi));
      }
      currentStrokes
          .get(id)
          .addPoint(
              event.getX(i), event.getY(i), TimeUnit.MILLISECONDS.toNanos(event.getEventTime()));

      if (action == MotionEvent.ACTION_UP
          || action == MotionEvent.ACTION_CANCEL
          || (action == MotionEvent.ACTION_POINTER_UP && i == event.getActionIndex())) {
        endingStrokes.add(getStroke(id));
      }
    }
  }

  void cleanUp(MotionEvent event) {
    endingStrokes.clear();
    int action = event.getActionMasked();
    for (int i = 0; i < event.getPointerCount(); i++) {
      int id = event.getPointerId(i);
      if (action == MotionEvent.ACTION_UP
          || action == MotionEvent.ACTION_CANCEL
          || (action == MotionEvent.ACTION_POINTER_UP && i == event.getActionIndex())) {
        currentStrokes.remove(id);
      }
    }
  }

  /** @return the list of Strokes which are ending in the recently added MotionEvent */
  public ArrayList<Stroke> getEndingStrokes() {
    return endingStrokes;
  }

  /**
   * @param id the id from MotionEvent
   * @return the Stroke assigned to the id
   */
  public Stroke getStroke(int id) {
    return currentStrokes.get(id);
  }

  /** @return the height of the screen in inches */
  public float getScreenHeight() {
    return screenHeight;
  }
}
