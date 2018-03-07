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

package com.android.incallui.answer.impl;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraDevice.StateCallback;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.incallui.video.protocol.VideoCallScreen;
import java.util.Arrays;

/**
 * Shows the local preview for the incoming video call or video upgrade request. This class is used
 * for RCS Video Share where we need to open the camera preview ourselves. For IMS Video the camera
 * is managed by the modem, see {@link AnswerVideoCallScreen}.
 */
public class SelfManagedAnswerVideoCallScreen extends StateCallback implements VideoCallScreen {

  private static final int MAX_WIDTH = 1920;
  private static final float ASPECT_TOLERANCE = 0.1f;
  private static final float TARGET_ASPECT = 16.f / 9.f;

  @NonNull private final String callId;
  @NonNull private final Fragment fragment;
  @NonNull private final FixedAspectSurfaceView surfaceView;
  private final Context context;

  private String cameraId;
  private CameraDevice camera;
  private CaptureRequest.Builder captureRequestBuilder;

  public SelfManagedAnswerVideoCallScreen(
      @NonNull String callId, @NonNull Fragment fragment, @NonNull View view) {
    this.callId = Assert.isNotNull(callId);
    this.fragment = Assert.isNotNull(fragment);
    this.context = Assert.isNotNull(fragment.getContext());

    surfaceView =
        Assert.isNotNull(
            (FixedAspectSurfaceView) view.findViewById(R.id.incoming_preview_surface_view));
    surfaceView.setVisibility(View.VISIBLE);
    view.findViewById(R.id.incoming_preview_texture_view_overlay).setVisibility(View.VISIBLE);
    view.setBackgroundColor(0xff000000);
  }

  @Override
  public void onVideoScreenStart() {
    openCamera();
  }

  @Override
  public void onVideoScreenStop() {
    closeCamera();
  }

  @Override
  public void showVideoViews(
      boolean shouldShowPreview, boolean shouldShowRemote, boolean isRemotelyHeld) {}

  @Override
  public void onLocalVideoDimensionsChanged() {}

  @Override
  public void onLocalVideoOrientationChanged() {}

  @Override
  public void onRemoteVideoDimensionsChanged() {}

  @Override
  public void updateFullscreenAndGreenScreenMode(
      boolean shouldShowFullscreen, boolean shouldShowGreenScreen) {}

  @Override
  public Fragment getVideoCallScreenFragment() {
    return fragment;
  }

  @Override
  public String getCallId() {
    return callId;
  }

  @Override
  public void onHandoverFromWiFiToLte() {}

  /**
   * Opens the first front facing camera on the device into a {@link SurfaceView} while preserving
   * aspect ratio.
   */
  private void openCamera() {
    CameraManager manager = context.getSystemService(CameraManager.class);

    StreamConfigurationMap configMap = getFrontFacingCameraSizes(manager);
    if (configMap == null) {
      return;
    }

    Size previewSize = getOptimalSize(configMap.getOutputSizes(SurfaceHolder.class));
    LogUtil.i("SelfManagedAnswerVideoCallScreen.openCamera", "Optimal size: " + previewSize);
    float outputAspect = (float) previewSize.getWidth() / previewSize.getHeight();
    surfaceView.setAspectRatio(outputAspect);
    surfaceView.getHolder().setFixedSize(previewSize.getWidth(), previewSize.getHeight());

    try {
      manager.openCamera(cameraId, this, null);
    } catch (CameraAccessException e) {
      LogUtil.e("SelfManagedAnswerVideoCallScreen.openCamera", "failed to open camera", e);
    }
  }

  @Nullable
  private StreamConfigurationMap getFrontFacingCameraSizes(CameraManager manager) {
    String[] cameraIds;
    try {
      cameraIds = manager.getCameraIdList();
    } catch (CameraAccessException e) {
      LogUtil.e(
          "SelfManagedAnswerVideoCallScreen.getFrontFacingCameraSizes",
          "failed to get camera ids",
          e);
      return null;
    }

    for (String cameraId : cameraIds) {
      CameraCharacteristics characteristics;
      try {
        characteristics = manager.getCameraCharacteristics(cameraId);
      } catch (CameraAccessException e) {
        LogUtil.e(
            "SelfManagedAnswerVideoCallScreen.getFrontFacingCameraSizes",
            "failed to get camera characteristics",
            e);
        continue;
      }

      if (characteristics.get(CameraCharacteristics.LENS_FACING)
          != CameraCharacteristics.LENS_FACING_FRONT) {
        continue;
      }

      StreamConfigurationMap configMap =
          characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      if (configMap == null) {
        continue;
      }

      this.cameraId = cameraId;
      return configMap;
    }
    LogUtil.e(
        "SelfManagedAnswerVideoCallScreen.getFrontFacingCameraSizes", "No valid configurations.");
    return null;
  }

  /**
   * Given an array of {@link Size}s, tries to find the largest Size such that the aspect ratio of
   * the returned size is within {@code ASPECT_TOLERANCE} of {@code TARGET_ASPECT}. This is useful
   * because it provides us with an adequate size/camera resolution that will experience the least
   * stretching from our fullscreen UI that doesn't match any of the camera sizes.
   */
  private static Size getOptimalSize(Size[] outputSizes) {
    Size bestCandidateSize = outputSizes[0];
    float bestCandidateAspect =
        (float) bestCandidateSize.getWidth() / bestCandidateSize.getHeight();

    for (Size candidateSize : outputSizes) {
      if (candidateSize.getWidth() < MAX_WIDTH) {
        float candidateAspect = (float) candidateSize.getWidth() / candidateSize.getHeight();
        boolean isGoodCandidateAspect =
            Math.abs(candidateAspect - TARGET_ASPECT) < ASPECT_TOLERANCE;
        boolean isGoodOutputAspect =
            Math.abs(bestCandidateAspect - TARGET_ASPECT) < ASPECT_TOLERANCE;

        if ((isGoodCandidateAspect && !isGoodOutputAspect)
            || candidateSize.getWidth() > bestCandidateSize.getWidth()) {
          bestCandidateSize = candidateSize;
          bestCandidateAspect = candidateAspect;
        }
      }
    }
    return bestCandidateSize;
  }

  @Override
  public void onOpened(CameraDevice camera) {
    LogUtil.i("SelfManagedAnswerVideoCallScreen.opOpened", "camera opened.");
    this.camera = camera;
    Surface surface = surfaceView.getHolder().getSurface();
    try {
      captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      captureRequestBuilder.addTarget(surface);
      camera.createCaptureSession(Arrays.asList(surface), new CaptureSessionCallback(), null);
    } catch (CameraAccessException e) {
      LogUtil.e(
          "SelfManagedAnswerVideoCallScreen.createCameraPreview", "failed to create preview", e);
    }
  }

  @Override
  public void onDisconnected(CameraDevice camera) {
    closeCamera();
  }

  @Override
  public void onError(CameraDevice camera, int error) {
    closeCamera();
  }

  private void closeCamera() {
    if (camera != null) {
      camera.close();
      camera = null;
    }
  }

  private class CaptureSessionCallback extends CameraCaptureSession.StateCallback {

    @Override
    public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
      LogUtil.i(
          "SelfManagedAnswerVideoCallScreen.onConfigured", "camera capture session configured.");
      // The camera is already closed.
      if (camera == null) {
        return;
      }

      // When the session is ready, we start displaying the preview.
      captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
      try {
        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
      } catch (CameraAccessException e) {
        LogUtil.e("CaptureSessionCallback.onConfigured", "failed to configure", e);
      }
    }

    @Override
    public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
      LogUtil.e("CaptureSessionCallback.onConfigureFailed", "failed to configure");
    }
  }
}
