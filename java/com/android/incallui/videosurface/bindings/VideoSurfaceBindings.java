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

package com.android.incallui.videosurface.bindings;

import android.view.TextureView;
import com.android.incallui.videosurface.impl.VideoScale;
import com.android.incallui.videosurface.impl.VideoSurfaceTextureImpl;
import com.android.incallui.videosurface.protocol.VideoSurfaceTexture;

/** Bindings for video surface module. */
public class VideoSurfaceBindings {

  public static VideoSurfaceTexture createLocalVideoSurfaceTexture() {
    return new VideoSurfaceTextureImpl(VideoSurfaceTexture.SURFACE_TYPE_LOCAL);
  }

  public static VideoSurfaceTexture createRemoteVideoSurfaceTexture() {
    return new VideoSurfaceTextureImpl(VideoSurfaceTexture.SURFACE_TYPE_REMOTE);
  }

  public static void scaleVideoAndFillView(
      TextureView textureView, float videoWidth, float videoHeight, float rotationDegrees) {
    VideoScale.scaleVideoAndFillView(textureView, videoWidth, videoHeight, rotationDegrees);
  }

  public static void scaleVideoMaintainingAspectRatio(
      TextureView textureView, int videoWidth, int videoHeight) {
    VideoScale.scaleVideoMaintainingAspectRatio(textureView, videoWidth, videoHeight);
  }
}
