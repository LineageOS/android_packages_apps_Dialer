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
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telecom.VideoProfile.CameraCapabilities;
import android.util.Size;
import android.view.Surface;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import java.util.Arrays;

/**
 * Used by the video provider to draw the local camera. The in-call UI is responsible for setting
 * the camera (front or back) and the view to draw to. The video provider then uses this class to
 * capture frames from the given camera and draw to the given view.
 */
final class SimulatorPreviewCamera {
  @NonNull private final Context context;
  @NonNull private final String cameraId;
  @NonNull private final Surface surface;
  @Nullable private CameraDevice camera;
  private boolean isStopped;

  SimulatorPreviewCamera(
      @NonNull Context context, @NonNull String cameraId, @NonNull Surface surface) {
    this.context = Assert.isNotNull(context);
    this.cameraId = Assert.isNotNull(cameraId);
    this.surface = Assert.isNotNull(surface);
  }

  void startCamera() {
    LogUtil.enterBlock("SimulatorPreviewCamera.startCamera");
    Assert.checkState(!isStopped);
    try {
      context
          .getSystemService(CameraManager.class)
          .openCamera(cameraId, new CameraListener(), null /* handler */);
    } catch (CameraAccessException | SecurityException e) {
      throw Assert.createIllegalStateFailException("camera error: " + e);
    }
  }

  void stopCamera() {
    LogUtil.enterBlock("SimulatorPreviewCamera.stopCamera");
    isStopped = true;
    if (camera != null) {
      camera.close();
      camera = null;
    }
  }

  @Nullable
  static CameraCapabilities getCameraCapabilities(
      @NonNull Context context, @Nullable String cameraId) {
    if (cameraId == null) {
      LogUtil.e("SimulatorPreviewCamera.getCameraCapabilities", "null camera ID");
      return null;
    }

    CameraManager cameraManager = context.getSystemService(CameraManager.class);
    CameraCharacteristics characteristics;
    try {
      characteristics = cameraManager.getCameraCharacteristics(cameraId);
    } catch (CameraAccessException e) {
      throw Assert.createIllegalStateFailException("camera error: " + e);
    }

    StreamConfigurationMap map =
        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
    Size previewSize = map.getOutputSizes(SurfaceTexture.class)[0];
    LogUtil.i("SimulatorPreviewCamera.getCameraCapabilities", "preview size: " + previewSize);
    return new CameraCapabilities(previewSize.getWidth(), previewSize.getHeight());
  }

  private final class CameraListener extends CameraDevice.StateCallback {
    @Override
    public void onOpened(CameraDevice camera) {
      LogUtil.enterBlock("SimulatorPreviewCamera.CameraListener.onOpened");
      SimulatorPreviewCamera.this.camera = camera;
      if (isStopped) {
        LogUtil.i("SimulatorPreviewCamera.CameraListener.onOpened", "stopped");
        stopCamera();
        return;
      }

      try {
        camera.createCaptureSession(
            Arrays.asList(Assert.isNotNull(surface)),
            new CaptureSessionCallback(),
            null /* handler */);
      } catch (CameraAccessException e) {
        throw Assert.createIllegalStateFailException("camera error: " + e);
      }
    }

    @Override
    public void onError(CameraDevice camera, int error) {
      LogUtil.i("SimulatorPreviewCamera.CameraListener.onError", "error: " + error);
      stopCamera();
    }

    @Override
    public void onDisconnected(CameraDevice camera) {
      LogUtil.enterBlock("SimulatorPreviewCamera.CameraListener.onDisconnected");
      stopCamera();
    }

    @Override
    public void onClosed(CameraDevice camera) {
      LogUtil.enterBlock("SimulatorPreviewCamera.CameraListener.onCLosed");
    }
  }

  private final class CaptureSessionCallback extends CameraCaptureSession.StateCallback {
    @Override
    public void onConfigured(@NonNull CameraCaptureSession session) {
      LogUtil.enterBlock("SimulatorPreviewCamera.CaptureSessionCallback.onConfigured");

      if (isStopped) {
        LogUtil.i("SimulatorPreviewCamera.CaptureSessionCallback.onConfigured", "stopped");
        stopCamera();
        return;
      }
      try {
        CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        builder.addTarget(surface);
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        session.setRepeatingRequest(
            builder.build(), null /* captureCallback */, null /* handler */);
      } catch (CameraAccessException e) {
        throw Assert.createIllegalStateFailException("camera error: " + e);
      }
    }

    @Override
    public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
      LogUtil.enterBlock("SimulatorPreviewCamera.CaptureSessionCallback.onConfigureFailed");
    }
  }
}
