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

package com.android.dialer.util;

import android.graphics.Point;

/**
 * Singleton class to keep track of where the user last touched the screen.
 *
 * <p>Used to pass on to the InCallUI for animation.
 */
public class TouchPointManager {

  public static final String TOUCH_POINT = "touchPoint";

  private static TouchPointManager sInstance = new TouchPointManager();

  private Point mPoint = new Point();

  /** Private constructor. Instance should only be acquired through getRunningInstance(). */
  private TouchPointManager() {}

  public static TouchPointManager getInstance() {
    return sInstance;
  }

  public Point getPoint() {
    return mPoint;
  }

  public void setPoint(int x, int y) {
    mPoint.set(x, y);
  }

  /**
   * When a point is initialized, its value is (0,0). Since it is highly unlikely a user will touch
   * at that exact point, if the point in TouchPointManager is (0,0), it is safe to assume that the
   * TouchPointManager has not yet collected a touch.
   *
   * @return True if there is a valid point saved. Define a valid point as any point that is not
   *     (0,0).
   */
  public boolean hasValidPoint() {
    return mPoint.x != 0 || mPoint.y != 0;
  }
}
