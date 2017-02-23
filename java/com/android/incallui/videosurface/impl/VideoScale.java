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

package com.android.incallui.videosurface.impl;

import android.graphics.Matrix;
import android.view.TextureView;
import com.android.dialer.common.LogUtil;

/** Utilities to scale the preview and remote video. */
public class VideoScale {
  /**
   * Scales the video in the given view such that the video takes up the entire view. To maintain
   * aspect ratio the video will be scaled to be larger than the view.
   */
  public static void scaleVideoAndFillView(
      TextureView textureView, float videoWidth, float videoHeight, float rotationDegrees) {
    float viewWidth = textureView.getWidth();
    float viewHeight = textureView.getHeight();
    float viewAspectRatio = viewWidth / viewHeight;
    float videoAspectRatio = videoWidth / videoHeight;
    float scaleWidth = 1.0f;
    float scaleHeight = 1.0f;

    if (viewAspectRatio > videoAspectRatio) {
      // Scale to exactly fit the width of the video. The top and bottom will be cropped.
      float scaleFactor = viewWidth / videoWidth;
      float desiredScaledHeight = videoHeight * scaleFactor;
      scaleHeight = desiredScaledHeight / viewHeight;
    } else {
      // Scale to exactly fit the height of the video. The sides will be cropped.
      float scaleFactor = viewHeight / videoHeight;
      float desiredScaledWidth = videoWidth * scaleFactor;
      scaleWidth = desiredScaledWidth / viewWidth;
    }

    if (rotationDegrees == 90.0f || rotationDegrees == 270.0f) {
      // We're in landscape mode but the camera feed is still drawing in portrait mode. Normally,
      // scale of 1.0 means that the video feed stretches to fit the view. In this case the X axis
      // is scaled to fit the height and the Y axis is scaled to fit the width.
      float scaleX = scaleWidth;
      float scaleY = scaleHeight;
      scaleWidth = viewHeight / viewWidth * scaleY;
      scaleHeight = viewWidth / viewHeight * scaleX;

      // This flips the view vertically. Without this the camera feed would be upside down.
      scaleWidth = scaleWidth * -1.0f;
      // This flips the view horizontally. Without this the camera feed would be mirrored (left
      // side would appear on right).
      scaleHeight = scaleHeight * -1.0f;
    }

    LogUtil.i(
        "VideoScale.scaleVideoAndFillView",
        "view: %f x %f, video: %f x %f scale: %f x %f, rotation: %f",
        viewWidth,
        viewHeight,
        videoWidth,
        videoHeight,
        scaleWidth,
        scaleHeight,
        rotationDegrees);

    Matrix transform = new Matrix();
    transform.setScale(
        scaleWidth,
        scaleHeight,
        // This performs the scaling from the horizontal middle of the view.
        viewWidth / 2.0f,
        // This perform the scaling from vertical middle of the view.
        viewHeight / 2.0f);
    if (rotationDegrees != 0) {
      transform.postRotate(rotationDegrees, viewWidth / 2.0f, viewHeight / 2.0f);
    }
    textureView.setTransform(transform);
  }

  /**
   * Scales the video in the given view such that all of the video is visible. This will result in
   * black bars on the top and bottom or the sides of the video.
   */
  public static void scaleVideoMaintainingAspectRatio(
      TextureView textureView, int videoWidth, int videoHeight) {
    int viewWidth = textureView.getWidth();
    int viewHeight = textureView.getHeight();
    float scaleWidth = 1.0f;
    float scaleHeight = 1.0f;

    if (viewWidth > viewHeight) {
      // Landscape layout.
      if (viewHeight * videoWidth > viewWidth * videoHeight) {
        // Current display height is too much. Correct it.
        int desiredHeight = viewWidth * videoHeight / videoWidth;
        scaleWidth = (float) desiredHeight / (float) viewHeight;
      } else if (viewHeight * videoWidth < viewWidth * videoHeight) {
        // Current display width is too much. Correct it.
        int desiredWidth = viewHeight * videoWidth / videoHeight;
        scaleWidth = (float) desiredWidth / (float) viewWidth;
      }
    } else {
      // Portrait layout.
      if (viewHeight * videoWidth > viewWidth * videoHeight) {
        // Current display height is too much. Correct it.
        int desiredHeight = viewWidth * videoHeight / videoWidth;
        scaleHeight = (float) desiredHeight / (float) viewHeight;
      } else if (viewHeight * videoWidth < viewWidth * videoHeight) {
        // Current display width is too much. Correct it.
        int desiredWidth = viewHeight * videoWidth / videoHeight;
        scaleHeight = (float) desiredWidth / (float) viewWidth;
      }
    }

    LogUtil.i(
        "VideoScale.scaleVideoMaintainingAspectRatio",
        "view: %d x %d, video: %d x %d scale: %f x %f",
        viewWidth,
        viewHeight,
        videoWidth,
        videoHeight,
        scaleWidth,
        scaleHeight);
    Matrix transform = new Matrix();
    transform.setScale(
        scaleWidth,
        scaleHeight,
        // This performs the scaling from the horizontal middle of the view.
        viewWidth / 2.0f,
        // This perform the scaling from vertical middle of the view.
        viewHeight / 2.0f);
    textureView.setTransform(transform);
  }

  private VideoScale() {}
}
