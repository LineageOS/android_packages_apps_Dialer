/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.dialer.callcomposer.util;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.support.annotation.VisibleForTesting;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;

/** Utility class for resizing images before sending them as enriched call attachments. */
public final class BitmapResizer {
  @VisibleForTesting static final int MAX_OUTPUT_RESOLUTION = 640;

  /**
   * Returns a bitmap that is a resized version of the parameter image. The image will only be
   * resized down and sized to be appropriate for an enriched call.
   *
   * @param image to be resized
   * @param rotation degrees to rotate the image clockwise
   * @return resized image
   */
  public static Bitmap resizeForEnrichedCalling(Bitmap image, int rotation) {
    Assert.isWorkerThread();

    int width = image.getWidth();
    int height = image.getHeight();

    Matrix matrix = new Matrix();
    matrix.postRotate(rotation);

    LogUtil.i(
        "BitmapResizer.resizeForEnrichedCalling", "starting height: %d, width: %d", height, width);

    if (width <= MAX_OUTPUT_RESOLUTION && height <= MAX_OUTPUT_RESOLUTION) {
      LogUtil.i("BitmapResizer.resizeForEnrichedCalling", "no resizing needed");
      return Bitmap.createBitmap(image, 0, 0, width, height, matrix, true);
    }

    float ratio = 1;
    if (width > height) {
      // landscape
      ratio = MAX_OUTPUT_RESOLUTION / (float) width;
    } else {
      // portrait & square
      ratio = MAX_OUTPUT_RESOLUTION / (float) height;
    }

    LogUtil.i(
        "BitmapResizer.resizeForEnrichedCalling",
        "ending height: %f, width: %f",
        height * ratio,
        width * ratio);

    matrix.postScale(ratio, ratio);
    return Bitmap.createBitmap(image, 0, 0, width, height, matrix, true);
  }
}
