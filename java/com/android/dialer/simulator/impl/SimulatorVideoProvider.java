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
 * limitations under the License
 */

package com.android.dialer.simulator.impl;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telecom.Connection;
import android.telecom.VideoProfile;
import android.view.Surface;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.simulator.Simulator.Event;

/**
 * Implements the telecom video provider API to simulate IMS video calling. A video capable phone
 * always has one video provider associated with it. Actual drawing of local and remote video is
 * done by {@link SimulatorPreviewCamera} and {@link SimulatorRemoteVideo} respectively.
 */
final class SimulatorVideoProvider extends Connection.VideoProvider {
  @NonNull private final Context context;
  @NonNull private final SimulatorConnection connection;
  @Nullable private String previewCameraId;;
  @Nullable private SimulatorPreviewCamera simulatorPreviewCamera;
  @Nullable private SimulatorRemoteVideo simulatorRemoteVideo;

  SimulatorVideoProvider(@NonNull Context context, @NonNull SimulatorConnection connection) {
    this.context = Assert.isNotNull(context);
    this.connection = Assert.isNotNull(connection);
  }

  @Override
  public void onSetCamera(String previewCameraId) {
    LogUtil.i("SimulatorVideoProvider.onSetCamera", "previewCameraId: " + previewCameraId);
    this.previewCameraId = previewCameraId;
    if (simulatorPreviewCamera != null) {
      simulatorPreviewCamera.stopCamera();
      simulatorPreviewCamera = null;
    }
    if (previewCameraId == null && simulatorRemoteVideo != null) {
      simulatorRemoteVideo.stopVideo();
      simulatorRemoteVideo = null;
    }
  }

  @Override
  public void onSetPreviewSurface(Surface surface) {
    LogUtil.enterBlock("SimulatorVideoProvider.onSetPreviewSurface");
    if (simulatorPreviewCamera != null) {
      simulatorPreviewCamera.stopCamera();
      simulatorPreviewCamera = null;
    }
    if (surface != null && previewCameraId != null) {
      simulatorPreviewCamera = new SimulatorPreviewCamera(context, previewCameraId, surface);
      simulatorPreviewCamera.startCamera();
    }
  }

  @Override
  public void onSetDisplaySurface(Surface surface) {
    LogUtil.enterBlock("SimulatorVideoProvider.onSetDisplaySurface");
    if (simulatorRemoteVideo != null) {
      simulatorRemoteVideo.stopVideo();
      simulatorRemoteVideo = null;
    }
    if (surface != null) {
      simulatorRemoteVideo = new SimulatorRemoteVideo(surface);
      simulatorRemoteVideo.startVideo();
    }
  }

  @Override
  public void onSetDeviceOrientation(int rotation) {
    LogUtil.i("SimulatorVideoProvider.onSetDeviceOrientation", "rotation: " + rotation);
  }

  @Override
  public void onSetZoom(float value) {
    LogUtil.i("SimulatorVideoProvider.onSetZoom", "zoom: " + value);
  }

  @Override
  public void onSendSessionModifyRequest(VideoProfile fromProfile, VideoProfile toProfile) {
    LogUtil.enterBlock("SimulatorVideoProvider.onSendSessionModifyRequest");
    connection.onEvent(
        new Event(
            Event.SESSION_MODIFY_REQUEST,
            Integer.toString(fromProfile.getVideoState()),
            Integer.toString(toProfile.getVideoState())));
  }

  @Override
  public void onSendSessionModifyResponse(VideoProfile responseProfile) {
    LogUtil.enterBlock("SimulatorVideoProvider.onSendSessionModifyResponse");
  }

  @Override
  public void onRequestCameraCapabilities() {
    LogUtil.enterBlock("SimulatorVideoProvider.onRequestCameraCapabilities");
    changeCameraCapabilities(
        SimulatorPreviewCamera.getCameraCapabilities(context, previewCameraId));
  }

  @Override
  public void onRequestConnectionDataUsage() {
    LogUtil.enterBlock("SimulatorVideoProvider.onRequestConnectionDataUsage");
    setCallDataUsage(10 * 1024);
  }

  @Override
  public void onSetPauseImage(Uri uri) {
    LogUtil.enterBlock("SimulatorVideoProvider.onSetPauseImage");
  }
}
