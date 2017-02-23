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

package com.android.incallui.videosurface.protocol;

import android.graphics.Point;
import android.support.annotation.IntDef;
import android.view.Surface;
import android.view.TextureView;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Represents a surface texture for a video feed. */
public interface VideoSurfaceTexture {

  /** Whether this represents the preview or remote display. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    SURFACE_TYPE_LOCAL,
    SURFACE_TYPE_REMOTE,
  })
  @interface SurfaceType {}

  int SURFACE_TYPE_LOCAL = 1;
  int SURFACE_TYPE_REMOTE = 2;

  void setDelegate(VideoSurfaceDelegate delegate);

  int getSurfaceType();

  Surface getSavedSurface();

  void setSurfaceDimensions(Point surfaceDimensions);

  Point getSurfaceDimensions();

  void setSourceVideoDimensions(Point sourceVideoDimensions);

  Point getSourceVideoDimensions();

  void attachToTextureView(TextureView textureView);

  void setDoneWithSurface();
}
