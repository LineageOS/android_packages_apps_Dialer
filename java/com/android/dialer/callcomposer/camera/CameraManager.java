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
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import com.android.dialer.callcomposer.camera.camerafocus.FocusOverlayManager;
import com.android.dialer.callcomposer.camera.camerafocus.RenderOverlay;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Class which manages interactions with the camera, but does not do any UI. This class is designed
 * to be a singleton to ensure there is one component managing the camera and releasing the native
 * resources. In order to acquire a camera, a caller must:
 *
 * <ul>
 *   <li>Call selectCamera to select front or back camera
 *   <li>Call setSurface to control where the preview is shown
 *   <li>Call openCamera to request the camera start preview
 * </ul>
 *
 * Callers should call onPause and onResume to ensure that the camera is release while the activity
 * is not active. This class is not thread safe. It should only be called from one thread (the UI
 * thread or test thread)
 */
public class CameraManager implements FocusOverlayManager.Listener {
  /** Callbacks for the camera manager listener */
  public interface CameraManagerListener {
    void onCameraError(int errorCode, Exception e);

    void onCameraChanged();
  }

  /** Callback when taking image or video */
  public interface MediaCallback {
    int MEDIA_CAMERA_CHANGED = 1;
    int MEDIA_NO_DATA = 2;

    void onMediaReady(Uri uriToMedia, String contentType, int width, int height);

    void onMediaFailed(Exception exception);

    void onMediaInfo(int what);
  }

  // Error codes
  private static final int ERROR_OPENING_CAMERA = 1;
  private static final int ERROR_SHOWING_PREVIEW = 2;
  private static final int ERROR_HARDWARE_ACCELERATION_DISABLED = 3;
  private static final int ERROR_TAKING_PICTURE = 4;

  private static final int NO_CAMERA_SELECTED = -1;

  private static final Camera.ShutterCallback DUMMY_SHUTTER_CALLBACK =
      new Camera.ShutterCallback() {
        @Override
        public void onShutter() {
          // Do nothing
        }
      };

  private static CameraManager instance;

  /** The CameraInfo for the currently selected camera */
  private final CameraInfo cameraInfo;

  /** The index of the selected camera or NO_CAMERA_SELECTED if a camera hasn't been selected yet */
  private int cameraIndex;

  /** True if the device has front and back cameras */
  private final boolean hasFrontAndBackCamera;

  /** True if the camera should be open (may not yet be actually open) */
  private boolean openRequested;

  /** The preview view to show the preview on */
  private CameraPreview cameraPreview;

  /** The helper classs to handle orientation changes */
  private OrientationHandler orientationHandler;

  /** Tracks whether the preview has hardware acceleration */
  private boolean isHardwareAccelerationSupported;

  /**
   * The task for opening the camera, so it doesn't block the UI thread Using AsyncTask rather than
   * SafeAsyncTask because the tasks need to be serialized, but don't need to be on the UI thread
   * TODO(blemmon): If we have other AyncTasks (not SafeAsyncTasks) this may contend and we may need
   * to create a dedicated thread, or synchronize the threads in the thread pool
   */
  private AsyncTask<Integer, Void, Camera> openCameraTask;

  /**
   * The camera index that is queued to be opened, but not completed yet, or NO_CAMERA_SELECTED if
   * no open task is pending
   */
  private int pendingOpenCameraIndex = NO_CAMERA_SELECTED;

  /** The instance of the currently opened camera */
  private Camera camera;

  /** The rotation of the screen relative to the camera's natural orientation */
  private int rotation;

  /** The callback to notify when errors or other events occur */
  private CameraManagerListener listener;

  /** True if the camera is currently in the process of taking an image */
  private boolean takingPicture;

  /** Manages auto focus visual and behavior */
  private final FocusOverlayManager focusOverlayManager;

  private CameraManager() {
    this.cameraInfo = new CameraInfo();
    cameraIndex = NO_CAMERA_SELECTED;

    // Check to see if a front and back camera exist
    boolean hasFrontCamera = false;
    boolean hasBackCamera = false;
    final CameraInfo cameraInfo = new CameraInfo();
    final int cameraCount = Camera.getNumberOfCameras();
    try {
      for (int i = 0; i < cameraCount; i++) {
        Camera.getCameraInfo(i, cameraInfo);
        if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
          hasFrontCamera = true;
        } else if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK) {
          hasBackCamera = true;
        }
        if (hasFrontCamera && hasBackCamera) {
          break;
        }
      }
    } catch (final RuntimeException e) {
      LogUtil.e("CameraManager.CameraManager", "Unable to load camera info", e);
    }
    hasFrontAndBackCamera = hasFrontCamera && hasBackCamera;
    focusOverlayManager = new FocusOverlayManager(this, Looper.getMainLooper());

    // Assume the best until we are proven otherwise
    isHardwareAccelerationSupported = true;
  }

  /** Gets the singleton instance */
  public static CameraManager get() {
    if (instance == null) {
      instance = new CameraManager();
    }
    return instance;
  }

  /**
   * Sets the surface to use to display the preview This must only be called AFTER the CameraPreview
   * has a texture ready
   *
   * @param preview The preview surface view
   */
  void setSurface(final CameraPreview preview) {
    if (preview == cameraPreview) {
      return;
    }

    if (preview != null) {
      Assert.checkArgument(preview.isValid());
      preview.setOnTouchListener(
          new View.OnTouchListener() {
            @Override
            public boolean onTouch(final View view, final MotionEvent motionEvent) {
              if ((motionEvent.getActionMasked() & MotionEvent.ACTION_UP)
                  == MotionEvent.ACTION_UP) {
                focusOverlayManager.setPreviewSize(view.getWidth(), view.getHeight());
                focusOverlayManager.onSingleTapUp(
                    (int) motionEvent.getX() + view.getLeft(),
                    (int) motionEvent.getY() + view.getTop());
              }
              view.performClick();
              return true;
            }
          });
    }
    cameraPreview = preview;
    tryShowPreview();
  }

  public void setRenderOverlay(final RenderOverlay renderOverlay) {
    focusOverlayManager.setFocusRenderer(
        renderOverlay != null ? renderOverlay.getPieRenderer() : null);
  }

  /** Convenience function to swap between front and back facing cameras */
  public void swapCamera() {
    Assert.checkState(cameraIndex >= 0);
    selectCamera(
        cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT
            ? CameraInfo.CAMERA_FACING_BACK
            : CameraInfo.CAMERA_FACING_FRONT);
  }

  /**
   * Selects the first camera facing the desired direction, or the first camera if there is no
   * camera in the desired direction
   *
   * @param desiredFacing One of the CameraInfo.CAMERA_FACING_* constants
   * @return True if a camera was selected, or false if selecting a camera failed
   */
  public boolean selectCamera(final int desiredFacing) {
    try {
      // We already selected a camera facing that direction
      if (cameraIndex >= 0 && this.cameraInfo.facing == desiredFacing) {
        return true;
      }

      final int cameraCount = Camera.getNumberOfCameras();
      Assert.checkState(cameraCount > 0);

      cameraIndex = NO_CAMERA_SELECTED;
      setCamera(null);
      final CameraInfo cameraInfo = new CameraInfo();
      for (int i = 0; i < cameraCount; i++) {
        Camera.getCameraInfo(i, cameraInfo);
        if (cameraInfo.facing == desiredFacing) {
          cameraIndex = i;
          Camera.getCameraInfo(i, this.cameraInfo);
          break;
        }
      }

      // There's no camera in the desired facing direction, just select the first camera
      // regardless of direction
      if (cameraIndex < 0) {
        cameraIndex = 0;
        Camera.getCameraInfo(0, this.cameraInfo);
      }

      if (openRequested) {
        // The camera is open, so reopen with the newly selected camera
        openCamera();
      }
      return true;
    } catch (final RuntimeException e) {
      LogUtil.e("CameraManager.selectCamera", "RuntimeException in CameraManager.selectCamera", e);
      if (listener != null) {
        listener.onCameraError(ERROR_OPENING_CAMERA, e);
      }
      return false;
    }
  }

  public int getCameraIndex() {
    return cameraIndex;
  }

  public void selectCameraByIndex(final int cameraIndex) {
    if (this.cameraIndex == cameraIndex) {
      return;
    }

    try {
      this.cameraIndex = cameraIndex;
      Camera.getCameraInfo(this.cameraIndex, cameraInfo);
      if (openRequested) {
        openCamera();
      }
    } catch (final RuntimeException e) {
      LogUtil.e(
          "CameraManager.selectCameraByIndex",
          "RuntimeException in CameraManager.selectCameraByIndex",
          e);
      if (listener != null) {
        listener.onCameraError(ERROR_OPENING_CAMERA, e);
      }
    }
  }

  @Nullable
  @VisibleForTesting
  public CameraInfo getCameraInfo() {
    if (cameraIndex == NO_CAMERA_SELECTED) {
      return null;
    }
    return cameraInfo;
  }

  /** @return True if the device has both a front and back camera */
  public boolean hasFrontAndBackCamera() {
    return hasFrontAndBackCamera;
  }

  /** Opens the camera on a separate thread and initiates the preview if one is available */
  void openCamera() {
    if (this.cameraIndex == NO_CAMERA_SELECTED) {
      // Ensure a selected camera if none is currently selected. This may happen if the
      // camera chooser is not the default media chooser.
      selectCamera(CameraInfo.CAMERA_FACING_BACK);
    }
    openRequested = true;
    // We're already opening the camera or already have the camera handle, nothing more to do
    if (pendingOpenCameraIndex == this.cameraIndex || this.camera != null) {
      return;
    }

    // True if the task to open the camera has to be delayed until the current one completes
    boolean delayTask = false;

    // Cancel any previous open camera tasks
    if (openCameraTask != null) {
      pendingOpenCameraIndex = NO_CAMERA_SELECTED;
      delayTask = true;
    }

    pendingOpenCameraIndex = this.cameraIndex;
    openCameraTask =
        new AsyncTask<Integer, Void, Camera>() {
          private Exception exception;

          @Override
          protected Camera doInBackground(final Integer... params) {
            try {
              final int cameraIndex = params[0];
              LogUtil.v(
                  "CameraManager.doInBackground",
                  "Opening camera " + CameraManager.this.cameraIndex);
              return Camera.open(cameraIndex);
            } catch (final Exception e) {
              LogUtil.e("CameraManager.doInBackground", "Exception while opening camera", e);
              exception = e;
              return null;
            }
          }

          @Override
          protected void onPostExecute(final Camera camera) {
            // If we completed, but no longer want this camera, then release the camera
            if (openCameraTask != this || !openRequested) {
              releaseCamera(camera);
              cleanup();
              return;
            }

            cleanup();

            LogUtil.v(
                "CameraManager.onPostExecute",
                "Opened camera " + CameraManager.this.cameraIndex + " " + (camera != null));
            setCamera(camera);
            if (camera == null) {
              if (listener != null) {
                listener.onCameraError(ERROR_OPENING_CAMERA, exception);
              }
              LogUtil.e("CameraManager.onPostExecute", "Error opening camera");
            }
          }

          @Override
          protected void onCancelled() {
            super.onCancelled();
            cleanup();
          }

          private void cleanup() {
            pendingOpenCameraIndex = NO_CAMERA_SELECTED;
            if (openCameraTask != null && openCameraTask.getStatus() == Status.PENDING) {
              // If there's another task waiting on this one to complete, start it now
              openCameraTask.execute(CameraManager.this.cameraIndex);
            } else {
              openCameraTask = null;
            }
          }
        };
    LogUtil.v("CameraManager.openCamera", "Start opening camera " + this.cameraIndex);
    if (!delayTask) {
      openCameraTask.execute(this.cameraIndex);
    }
  }

  /** Closes the camera releasing the resources it uses */
  void closeCamera() {
    openRequested = false;
    setCamera(null);
  }

  /**
   * Sets the listener which will be notified of errors or other events in the camera
   *
   * @param listener The listener to notify
   */
  public void setListener(final CameraManagerListener listener) {
    Assert.isMainThread();
    this.listener = listener;
    if (!isHardwareAccelerationSupported && this.listener != null) {
      this.listener.onCameraError(ERROR_HARDWARE_ACCELERATION_DISABLED, null);
    }
  }

  public void takePicture(final float heightPercent, @NonNull final MediaCallback callback) {
    Assert.checkState(!takingPicture);
    Assert.isNotNull(callback);
    cameraPreview.setFocusable(false);
    focusOverlayManager.cancelAutoFocus();
    if (this.camera == null) {
      // The caller should have checked isCameraAvailable first, but just in case, protect
      // against a null camera by notifying the callback that taking the picture didn't work
      callback.onMediaFailed(null);
      return;
    }
    final Camera.PictureCallback jpegCallback =
        new Camera.PictureCallback() {
          @Override
          public void onPictureTaken(final byte[] bytes, final Camera camera) {
            takingPicture = false;
            if (CameraManager.this.camera != camera) {
              // This may happen if the camera was changed between front/back while the
              // picture is being taken.
              callback.onMediaInfo(MediaCallback.MEDIA_CAMERA_CHANGED);
              return;
            }

            if (bytes == null) {
              callback.onMediaInfo(MediaCallback.MEDIA_NO_DATA);
              return;
            }

            final Camera.Size size = camera.getParameters().getPictureSize();
            int width;
            int height;
            if (rotation == 90 || rotation == 270) {
              // Is rotated, so swapping dimensions is desired
              // noinspection SuspiciousNameCombination
              width = size.height;
              // noinspection SuspiciousNameCombination
              height = size.width;
            } else {
              width = size.width;
              height = size.height;
            }
            LogUtil.i(
                "CameraManager.onPictureTaken", "taken picture size: " + bytes.length + " bytes");
            DialerExecutorComponent.get(cameraPreview.getContext())
                .dialerExecutorFactory()
                .createNonUiTaskBuilder(
                    new ImagePersistWorker(
                        width, height, heightPercent, bytes, cameraPreview.getContext()))
                .onSuccess(
                    (result) -> {
                      callback.onMediaReady(
                          result.getUri(), "image/jpeg", result.getWidth(), result.getHeight());
                    })
                .onFailure(
                    (throwable) -> {
                      callback.onMediaFailed(new Exception("Persisting image failed", throwable));
                    })
                .build()
                .executeSerial(null);
          }
        };

    takingPicture = true;
    try {
      this.camera.takePicture(
          // A shutter callback is required to enable shutter sound
          DUMMY_SHUTTER_CALLBACK, null /* raw */, null /* postView */, jpegCallback);
    } catch (final RuntimeException e) {
      LogUtil.e("CameraManager.takePicture", "RuntimeException in CameraManager.takePicture", e);
      takingPicture = false;
      if (listener != null) {
        listener.onCameraError(ERROR_TAKING_PICTURE, e);
      }
    }
  }

  /**
   * Asynchronously releases a camera
   *
   * @param camera The camera to release
   */
  private void releaseCamera(final Camera camera) {
    if (camera == null) {
      return;
    }

    focusOverlayManager.onCameraReleased();

    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(final Void... params) {
        LogUtil.v("CameraManager.doInBackground", "Releasing camera " + cameraIndex);
        camera.release();
        return null;
      }
    }.execute();
  }

  /**
   * Updates the orientation of the {@link Camera} w.r.t. the orientation of the device and the
   * orientation that the physical camera is mounted on the device.
   *
   * @param camera that needs to be reorientated
   * @param screenRotation rotation of the physical device
   * @param cameraOrientation {@link CameraInfo#orientation}
   * @param cameraIsFrontFacing {@link CameraInfo#CAMERA_FACING_FRONT}
   * @return rotation that images returned from {@link
   *     android.hardware.Camera.PictureCallback#onPictureTaken(byte[], Camera)} will be rotated.
   */
  @VisibleForTesting
  static int updateCameraRotation(
      @NonNull Camera camera,
      int screenRotation,
      int cameraOrientation,
      boolean cameraIsFrontFacing) {
    Assert.isNotNull(camera);
    Assert.checkArgument(cameraOrientation % 90 == 0);

    int rotation = screenRotationToDegress(screenRotation);
    boolean portrait = rotation == 0 || rotation == 180;

    if (!portrait && !cameraIsFrontFacing) {
      rotation += 180;
    }
    rotation += cameraOrientation;
    rotation %= 360;

    // Rotate the camera
    if (portrait && cameraIsFrontFacing) {
      camera.setDisplayOrientation((rotation + 180) % 360);
    } else {
      camera.setDisplayOrientation(rotation);
    }

    // Rotate the images returned when a picture is taken
    Camera.Parameters params = camera.getParameters();
    params.setRotation(rotation);
    camera.setParameters(params);
    return rotation;
  }

  private static int screenRotationToDegress(int screenRotation) {
    switch (screenRotation) {
      case Surface.ROTATION_0:
        return 0;
      case Surface.ROTATION_90:
        return 90;
      case Surface.ROTATION_180:
        return 180;
      case Surface.ROTATION_270:
        return 270;
      default:
        throw Assert.createIllegalStateFailException("Invalid surface rotation.");
    }
  }

  /** Sets the current camera, releasing any previously opened camera */
  private void setCamera(final Camera camera) {
    if (this.camera == camera) {
      return;
    }

    releaseCamera(this.camera);
    this.camera = camera;
    tryShowPreview();
    if (listener != null) {
      listener.onCameraChanged();
    }
  }

  /** Shows the preview if the camera is open and the preview is loaded */
  private void tryShowPreview() {
    if (cameraPreview == null || this.camera == null) {
      if (orientationHandler != null) {
        orientationHandler.disable();
        orientationHandler = null;
      }
      focusOverlayManager.onPreviewStopped();
      return;
    }
    try {
      this.camera.stopPreview();
      if (!takingPicture) {
        rotation =
            updateCameraRotation(
                this.camera,
                getScreenRotation(),
                cameraInfo.orientation,
                cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT);
      }

      final Camera.Parameters params = this.camera.getParameters();
      final Camera.Size pictureSize = chooseBestPictureSize();
      final Camera.Size previewSize = chooseBestPreviewSize(pictureSize);
      params.setPreviewSize(previewSize.width, previewSize.height);
      params.setPictureSize(pictureSize.width, pictureSize.height);
      logCameraSize("Setting preview size: ", previewSize);
      logCameraSize("Setting picture size: ", pictureSize);
      cameraPreview.setSize(previewSize, cameraInfo.orientation);
      for (final String focusMode : params.getSupportedFocusModes()) {
        if (TextUtils.equals(focusMode, Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
          // Use continuous focus if available
          params.setFocusMode(focusMode);
          break;
        }
      }

      this.camera.setParameters(params);
      cameraPreview.startPreview(this.camera);
      this.camera.startPreview();
      this.camera.setAutoFocusMoveCallback(
          new Camera.AutoFocusMoveCallback() {
            @Override
            public void onAutoFocusMoving(final boolean start, final Camera camera) {
              focusOverlayManager.onAutoFocusMoving(start);
            }
          });
      focusOverlayManager.setParameters(this.camera.getParameters());
      focusOverlayManager.setMirror(cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK);
      focusOverlayManager.onPreviewStarted();
      if (orientationHandler == null) {
        orientationHandler = new OrientationHandler(cameraPreview.getContext());
        orientationHandler.enable();
      }
    } catch (final IOException e) {
      LogUtil.e("CameraManager.tryShowPreview", "IOException in CameraManager.tryShowPreview", e);
      if (listener != null) {
        listener.onCameraError(ERROR_SHOWING_PREVIEW, e);
      }
    } catch (final RuntimeException e) {
      LogUtil.e(
          "CameraManager.tryShowPreview", "RuntimeException in CameraManager.tryShowPreview", e);
      if (listener != null) {
        listener.onCameraError(ERROR_SHOWING_PREVIEW, e);
      }
    }
  }

  private int getScreenRotation() {
    return cameraPreview
        .getContext()
        .getSystemService(WindowManager.class)
        .getDefaultDisplay()
        .getRotation();
  }

  public boolean isCameraAvailable() {
    return camera != null && !takingPicture && isHardwareAccelerationSupported;
  }

  /**
   * Choose the best picture size by trying to find a size close to the MmsConfig's max size, which
   * is closest to the screen aspect ratio. In case of RCS conversation returns default size.
   */
  private Camera.Size chooseBestPictureSize() {
    return camera.getParameters().getPictureSize();
  }

  /**
   * Chose the best preview size based on the picture size. Try to find a size with the same aspect
   * ratio and size as the picture if possible
   */
  private Camera.Size chooseBestPreviewSize(final Camera.Size pictureSize) {
    final List<Camera.Size> sizes =
        new ArrayList<Camera.Size>(camera.getParameters().getSupportedPreviewSizes());
    final float aspectRatio = pictureSize.width / (float) pictureSize.height;
    final int capturePixels = pictureSize.width * pictureSize.height;

    // Sort the sizes so the best size is first
    Collections.sort(
        sizes,
        new SizeComparator(Integer.MAX_VALUE, Integer.MAX_VALUE, aspectRatio, capturePixels));

    return sizes.get(0);
  }

  private class OrientationHandler extends OrientationEventListener {
    OrientationHandler(final Context context) {
      super(context);
    }

    @Override
    public void onOrientationChanged(final int orientation) {
      if (!takingPicture) {
        rotation =
            updateCameraRotation(
                camera,
                getScreenRotation(),
                cameraInfo.orientation,
                cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT);
      }
    }
  }

  private static class SizeComparator implements Comparator<Camera.Size> {
    private static final int PREFER_LEFT = -1;
    private static final int PREFER_RIGHT = 1;

    // The max width/height for the preferred size. Integer.MAX_VALUE if no size limit
    private final int maxWidth;
    private final int maxHeight;

    // The desired aspect ratio
    private final float targetAspectRatio;

    // The desired size (width x height) to try to match
    private final int targetPixels;

    public SizeComparator(
        final int maxWidth,
        final int maxHeight,
        final float targetAspectRatio,
        final int targetPixels) {
      this.maxWidth = maxWidth;
      this.maxHeight = maxHeight;
      this.targetAspectRatio = targetAspectRatio;
      this.targetPixels = targetPixels;
    }

    /**
     * Returns a negative value if left is a better choice than right, or a positive value if right
     * is a better choice is better than left. 0 if they are equal
     */
    @Override
    public int compare(final Camera.Size left, final Camera.Size right) {
      // If one size is less than the max size prefer it over the other
      if ((left.width <= maxWidth && left.height <= maxHeight)
          != (right.width <= maxWidth && right.height <= maxHeight)) {
        return left.width <= maxWidth ? PREFER_LEFT : PREFER_RIGHT;
      }

      // If one is closer to the target aspect ratio, prefer it.
      final float leftAspectRatio = left.width / (float) left.height;
      final float rightAspectRatio = right.width / (float) right.height;
      final float leftAspectRatioDiff = Math.abs(leftAspectRatio - targetAspectRatio);
      final float rightAspectRatioDiff = Math.abs(rightAspectRatio - targetAspectRatio);
      if (leftAspectRatioDiff != rightAspectRatioDiff) {
        return (leftAspectRatioDiff - rightAspectRatioDiff) < 0 ? PREFER_LEFT : PREFER_RIGHT;
      }

      // At this point they have the same aspect ratio diff and are either both bigger
      // than the max size or both smaller than the max size, so prefer the one closest
      // to target size
      final int leftDiff = Math.abs((left.width * left.height) - targetPixels);
      final int rightDiff = Math.abs((right.width * right.height) - targetPixels);
      return leftDiff - rightDiff;
    }
  }

  @Override // From FocusOverlayManager.Listener
  public void autoFocus() {
    if (this.camera == null) {
      return;
    }

    try {
      this.camera.autoFocus(
          new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(final boolean success, final Camera camera) {
              focusOverlayManager.onAutoFocus(success, false /* shutterDown */);
            }
          });
    } catch (final RuntimeException e) {
      LogUtil.e("CameraManager.autoFocus", "RuntimeException in CameraManager.autoFocus", e);
      // If autofocus fails, the camera should have called the callback with success=false,
      // but some throw an exception here
      focusOverlayManager.onAutoFocus(false /*success*/, false /*shutterDown*/);
    }
  }

  @Override // From FocusOverlayManager.Listener
  public void cancelAutoFocus() {
    if (camera == null) {
      return;
    }
    try {
      camera.cancelAutoFocus();
    } catch (final RuntimeException e) {
      // Ignore
      LogUtil.e(
          "CameraManager.cancelAutoFocus", "RuntimeException in CameraManager.cancelAutoFocus", e);
    }
  }

  @Override // From FocusOverlayManager.Listener
  public boolean capture() {
    return false;
  }

  @Override // From FocusOverlayManager.Listener
  public void setFocusParameters() {
    if (camera == null) {
      return;
    }
    try {
      final Camera.Parameters parameters = camera.getParameters();
      parameters.setFocusMode(focusOverlayManager.getFocusMode());
      if (parameters.getMaxNumFocusAreas() > 0) {
        // Don't set focus areas (even to null) if focus areas aren't supported, camera may
        // crash
        parameters.setFocusAreas(focusOverlayManager.getFocusAreas());
      }
      parameters.setMeteringAreas(focusOverlayManager.getMeteringAreas());
      camera.setParameters(parameters);
    } catch (final RuntimeException e) {
      // This occurs when the device is out of space or when the camera is locked
      LogUtil.e(
          "CameraManager.setFocusParameters",
          "RuntimeException in CameraManager setFocusParameters");
    }
  }

  public void resetPreview() {
    camera.startPreview();
    if (cameraPreview != null) {
      cameraPreview.setFocusable(true);
    }
  }

  private void logCameraSize(final String prefix, final Camera.Size size) {
    // Log the camera size and aspect ratio for help when examining bug reports for camera
    // failures
    LogUtil.i(
        "CameraManager.logCameraSize",
        prefix + size.width + "x" + size.height + " (" + (size.width / (float) size.height) + ")");
  }

  @VisibleForTesting
  public void resetCameraManager() {
    instance = null;
  }
}
