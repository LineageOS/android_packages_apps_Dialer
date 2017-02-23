/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.incallui;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Used to track which camera is used for outgoing video. */
public class InCallCameraManager {

  private final Set<Listener> mCameraSelectionListeners =
      Collections.newSetFromMap(new ConcurrentHashMap<Listener, Boolean>(8, 0.9f, 1));
  /** The camera ID for the front facing camera. */
  private String mFrontFacingCameraId;
  /** The camera ID for the rear facing camera. */
  private String mRearFacingCameraId;
  /** The currently active camera. */
  private boolean mUseFrontFacingCamera;
  /**
   * Indicates whether the list of cameras has been initialized yet. Initialization is delayed until
   * a video call is present.
   */
  private boolean mIsInitialized = false;
  /** The context. */
  private Context mContext;

  /**
   * Initializes the InCall CameraManager.
   *
   * @param context The current context.
   */
  public InCallCameraManager(Context context) {
    mUseFrontFacingCamera = true;
    mContext = context;
  }

  /**
   * Sets whether the front facing camera should be used or not.
   *
   * @param useFrontFacingCamera {@code True} if the front facing camera is to be used.
   */
  public void setUseFrontFacingCamera(boolean useFrontFacingCamera) {
    mUseFrontFacingCamera = useFrontFacingCamera;
    for (Listener listener : mCameraSelectionListeners) {
      listener.onActiveCameraSelectionChanged(mUseFrontFacingCamera);
    }
  }

  /**
   * Determines whether the front facing camera is currently in use.
   *
   * @return {@code True} if the front facing camera is in use.
   */
  public boolean isUsingFrontFacingCamera() {
    return mUseFrontFacingCamera;
  }

  /**
   * Determines the active camera ID.
   *
   * @return The active camera ID.
   */
  public String getActiveCameraId() {
    maybeInitializeCameraList(mContext);

    if (mUseFrontFacingCamera) {
      return mFrontFacingCameraId;
    } else {
      return mRearFacingCameraId;
    }
  }

  /** Calls when camera permission is granted by user. */
  public void onCameraPermissionGranted() {
    for (Listener listener : mCameraSelectionListeners) {
      listener.onCameraPermissionGranted();
    }
  }

  /**
   * Get the list of cameras available for use.
   *
   * @param context The context.
   */
  private void maybeInitializeCameraList(Context context) {
    if (mIsInitialized || context == null) {
      return;
    }

    Log.v(this, "initializeCameraList");

    CameraManager cameraManager = null;
    try {
      cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    } catch (Exception e) {
      Log.e(this, "Could not get camera service.");
      return;
    }

    if (cameraManager == null) {
      return;
    }

    String[] cameraIds = {};
    try {
      cameraIds = cameraManager.getCameraIdList();
    } catch (CameraAccessException e) {
      Log.d(this, "Could not access camera: " + e);
      // Camera disabled by device policy.
      return;
    }

    for (int i = 0; i < cameraIds.length; i++) {
      CameraCharacteristics c = null;
      try {
        c = cameraManager.getCameraCharacteristics(cameraIds[i]);
      } catch (IllegalArgumentException e) {
        // Device Id is unknown.
      } catch (CameraAccessException e) {
        // Camera disabled by device policy.
      }
      if (c != null) {
        int facingCharacteristic = c.get(CameraCharacteristics.LENS_FACING);
        if (facingCharacteristic == CameraCharacteristics.LENS_FACING_FRONT) {
          mFrontFacingCameraId = cameraIds[i];
        } else if (facingCharacteristic == CameraCharacteristics.LENS_FACING_BACK) {
          mRearFacingCameraId = cameraIds[i];
        }
      }
    }

    mIsInitialized = true;
    Log.v(this, "initializeCameraList : done");
  }

  public void addCameraSelectionListener(Listener listener) {
    if (listener != null) {
      mCameraSelectionListeners.add(listener);
    }
  }

  public void removeCameraSelectionListener(Listener listener) {
    if (listener != null) {
      mCameraSelectionListeners.remove(listener);
    }
  }

  public interface Listener {

    void onActiveCameraSelectionChanged(boolean isUsingFrontFacingCamera);

    void onCameraPermissionGranted();
  }
}
