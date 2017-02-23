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

package com.android.dialer.common;

/** Utility class for common math operations */
public class MathUtil {

  /**
   * Interpolates between two integer values based on percentage.
   *
   * @param begin Begin value
   * @param end End value
   * @param percent Percentage value, between 0 and 1
   * @return Interpolated result
   */
  public static int lerp(int begin, int end, float percent) {
    return (int) (begin * (1 - percent) + end * percent);
  }

  /**
   * Interpolates between two float values based on percentage.
   *
   * @param begin Begin value
   * @param end End value
   * @param percent Percentage value, between 0 and 1
   * @return Interpolated result
   */
  public static float lerp(float begin, float end, float percent) {
    return begin * (1 - percent) + end * percent;
  }

  /**
   * Clamps a value between two bounds inclusively.
   *
   * @param value Value to be clamped
   * @param min Lower bound
   * @param max Upper bound
   * @return Clamped value
   */
  public static float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(value, max));
  }
}
