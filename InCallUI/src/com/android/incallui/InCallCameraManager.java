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
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Size;

import java.lang.String;

/**
 * Used to track which camera is used for outgoing video.
 */
public class InCallCameraManager {

    /**
     * The camera ID for the front facing camera.
     */
    private String mFrontFacingCameraId;

    /**
     * The camera ID for the rear facing camera.
     */
    private String mRearFacingCameraId;

    /**
     * The currently active camera.
     */
    private boolean mUseFrontFacingCamera;

    /**
     * Aspect ratio of the front facing camera.
     */
    private float mFrontFacingCameraAspectRatio;

    /**
     * Aspect ratio of the rear facing camera.
     */
    private float mRearFacingCameraAspectRatio;

    /**
     * Initializes the InCall CameraManager.
     *
     * @param context The current context.
     */
    public InCallCameraManager(Context context) {
        mUseFrontFacingCamera = true;
        initializeCameraList(context);
    }

    /**
     * Sets whether the front facing camera should be used or not.
     *
     * @param useFrontFacingCamera {@code True} if the front facing camera is to be used.
     */
    public void setUseFrontFacingCamera(boolean useFrontFacingCamera) {
        mUseFrontFacingCamera = useFrontFacingCamera;
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
        if (mUseFrontFacingCamera) {
            return mFrontFacingCameraId;
        } else {
            return mRearFacingCameraId;
        }
    }

    /**
     * Get the camera ID and aspect ratio for the front and rear cameras.
     *
     * @param context The context.
     */
    private void initializeCameraList(Context context) {
        if (context == null) {
            return;
        }

        CameraManager cameraManager = null;
        try {
            cameraManager = (CameraManager) context.getSystemService(
                    Context.CAMERA_SERVICE);
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
            Log.d(this, "Could not access camera: "+e);
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
    }
}
