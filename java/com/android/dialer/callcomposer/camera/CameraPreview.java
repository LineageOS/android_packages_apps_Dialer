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

package com.android.dialer.callcomposer.camera;

import android.content.Context;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnTouchListener;
import com.android.dialer.common.Assert;
import com.android.dialer.util.PermissionsUtil;
import java.io.IOException;

/**
 * Contains shared code for SoftwareCameraPreview and HardwareCameraPreview, cannot use inheritance
 * because those classes must inherit from separate Views, so those classes delegate calls to this
 * helper class. Specifics for each implementation are in CameraPreviewHost
 */
public class CameraPreview {
  /** Implemented by the camera for rendering. */
  public interface CameraPreviewHost {
    View getView();

    boolean isValid();

    void startPreview(final Camera camera) throws IOException;

    void onCameraPermissionGranted();

    void setShown();
  }

  private int mCameraWidth = -1;
  private int mCameraHeight = -1;
  private boolean mTabHasBeenShown = false;
  private OnTouchListener mListener;

  private final CameraPreviewHost mHost;

  public CameraPreview(final CameraPreviewHost host) {
    Assert.isNotNull(host);
    Assert.isNotNull(host.getView());
    mHost = host;
  }

  // This is set when the tab is actually selected.
  public void setShown() {
    mTabHasBeenShown = true;
    maybeOpenCamera();
  }

  // Opening camera is very expensive. Most of the ANR reports seem to be related to the camera.
  // So we delay until the camera is actually needed.  See b/23287938
  private void maybeOpenCamera() {
    boolean visible = mHost.getView().getVisibility() == View.VISIBLE;
    if (mTabHasBeenShown && visible && PermissionsUtil.hasCameraPermissions(getContext())) {
      CameraManager.get().openCamera();
    }
  }

  public void setSize(final Camera.Size size, final int orientation) {
    switch (orientation) {
      case 0:
      case 180:
        mCameraWidth = size.width;
        mCameraHeight = size.height;
        break;
      case 90:
      case 270:
      default:
        mCameraWidth = size.height;
        mCameraHeight = size.width;
    }
    mHost.getView().requestLayout();
  }

  public int getWidthMeasureSpec(final int widthMeasureSpec, final int heightMeasureSpec) {
    if (mCameraHeight >= 0) {
      final int width = View.MeasureSpec.getSize(widthMeasureSpec);
      return MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
    } else {
      return widthMeasureSpec;
    }
  }

  public int getHeightMeasureSpec(final int widthMeasureSpec, final int heightMeasureSpec) {
    if (mCameraHeight >= 0) {
      final int orientation = getContext().getResources().getConfiguration().orientation;
      final int width = View.MeasureSpec.getSize(widthMeasureSpec);
      final float aspectRatio = (float) mCameraWidth / (float) mCameraHeight;
      int height;
      if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        height = (int) (width * aspectRatio);
      } else {
        height = (int) (width / aspectRatio);
      }
      return View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
    } else {
      return heightMeasureSpec;
    }
  }

  // onVisibilityChanged is set to Visible when the tab is _created_,
  //   which may be when the user is viewing a different tab.
  public void onVisibilityChanged(final int visibility) {
    if (PermissionsUtil.hasCameraPermissions(getContext())) {
      if (visibility == View.VISIBLE) {
        maybeOpenCamera();
      } else {
        CameraManager.get().closeCamera();
      }
    }
  }

  public Context getContext() {
    return mHost.getView().getContext();
  }

  public void setOnTouchListener(final View.OnTouchListener listener) {
    mListener = listener;
    mHost.getView().setOnTouchListener(listener);
  }

  public void setFocusable(boolean focusable) {
    mHost.getView().setOnTouchListener(focusable ? mListener : null);
  }

  public int getHeight() {
    return mHost.getView().getHeight();
  }

  public void onAttachedToWindow() {
    maybeOpenCamera();
  }

  public void onDetachedFromWindow() {
    CameraManager.get().closeCamera();
  }

  public void onRestoreInstanceState() {
    maybeOpenCamera();
  }

  public void onCameraPermissionGranted() {
    maybeOpenCamera();
  }

  /** @return True if the view is valid and prepared for the camera to start showing the preview */
  public boolean isValid() {
    return mHost.isValid();
  }

  /**
   * Starts the camera preview on the current surface. Abstracts out the differences in API from the
   * CameraManager
   *
   * @throws IOException Which is caught by the CameraManager to display an error
   */
  public void startPreview(final Camera camera) throws IOException {
    mHost.startPreview(camera);
  }
}
