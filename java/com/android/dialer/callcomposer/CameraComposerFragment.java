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

package com.android.dialer.callcomposer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Animatable;
import android.hardware.Camera.CameraInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.android.dialer.callcomposer.camera.CameraManager;
import com.android.dialer.callcomposer.camera.CameraManager.CameraManagerListener;
import com.android.dialer.callcomposer.camera.CameraManager.MediaCallback;
import com.android.dialer.callcomposer.camera.CameraPreview.CameraPreviewHost;
import com.android.dialer.callcomposer.camera.camerafocus.RenderOverlay;
import com.android.dialer.callcomposer.cameraui.CameraMediaChooserView;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.theme.base.ThemeComponent;
import com.android.dialer.util.PermissionsUtil;

/** Fragment used to compose call with image from the user's camera. */
public class CameraComposerFragment extends CallComposerFragment
    implements CameraManagerListener, OnClickListener, CameraManager.MediaCallback {

  private static final String CAMERA_DIRECTION_KEY = "camera_direction";
  private static final String CAMERA_URI_KEY = "camera_key";

  private View permissionView;
  private ImageButton exitFullscreen;
  private ImageButton fullscreen;
  private ImageButton swapCamera;
  private ImageButton capture;
  private ImageButton cancel;
  private CameraMediaChooserView cameraView;
  private RenderOverlay focus;
  private View shutter;
  private View allowPermission;
  private CameraPreviewHost preview;
  private ProgressBar loading;
  private ImageView previewImageView;

  private Uri cameraUri;
  private boolean processingUri;
  private String[] permissions = new String[] {Manifest.permission.CAMERA};
  private CameraUriCallback uriCallback;
  private int cameraDirection = CameraInfo.CAMERA_FACING_BACK;

  public static CameraComposerFragment newInstance() {
    return new CameraComposerFragment();
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle bundle) {
    View root = inflater.inflate(R.layout.fragment_camera_composer, container, false);
    permissionView = root.findViewById(R.id.permission_view);
    loading = root.findViewById(R.id.loading);
    cameraView = root.findViewById(R.id.camera_view);
    shutter = cameraView.findViewById(R.id.camera_shutter_visual);
    exitFullscreen = cameraView.findViewById(R.id.camera_exit_fullscreen);
    fullscreen = cameraView.findViewById(R.id.camera_fullscreen);
    swapCamera = cameraView.findViewById(R.id.swap_camera_button);
    capture = cameraView.findViewById(R.id.camera_capture_button);
    cancel = cameraView.findViewById(R.id.camera_cancel_button);
    focus = cameraView.findViewById(R.id.focus_visual);
    preview = cameraView.findViewById(R.id.camera_preview);
    previewImageView = root.findViewById(R.id.preview_image_view);

    exitFullscreen.setOnClickListener(this);
    fullscreen.setOnClickListener(this);
    swapCamera.setOnClickListener(this);
    capture.setOnClickListener(this);
    cancel.setOnClickListener(this);


    if (!PermissionsUtil.hasCameraPermissions(getContext())) {
      LogUtil.i("CameraComposerFragment.onCreateView", "Permission view shown.");
      Logger.get(getContext()).logImpression(DialerImpression.Type.CAMERA_PERMISSION_DISPLAYED);
      ImageView permissionImage = permissionView.findViewById(R.id.permission_icon);
      TextView permissionText = permissionView.findViewById(R.id.permission_text);
      allowPermission = permissionView.findViewById(R.id.allow);

      allowPermission.setOnClickListener(this);
      permissionText.setText(R.string.camera_permission_text);
      permissionImage.setImageResource(R.drawable.quantum_ic_camera_alt_white_48);
      permissionImage.setColorFilter(ThemeComponent.get(getContext()).theme().getColorPrimary());
      permissionView.setVisibility(View.VISIBLE);
    } else {
      if (bundle != null) {
        cameraDirection = bundle.getInt(CAMERA_DIRECTION_KEY);
        cameraUri = bundle.getParcelable(CAMERA_URI_KEY);
      }
      setupCamera();
    }
    return root;
  }

  private void setupCamera() {
    if (!PermissionsUtil.hasCameraPrivacyToastShown(getContext())) {
      PermissionsUtil.showCameraPermissionToast(getContext());
    }
    CameraManager.get().setListener(this);
    preview.setShown();
    CameraManager.get().setRenderOverlay(focus);
    CameraManager.get().selectCamera(cameraDirection);
    setCameraUri(cameraUri);
  }

  @Override
  public void onCameraError(int errorCode, Exception exception) {
    LogUtil.e("CameraComposerFragment.onCameraError", "errorCode: ", errorCode, exception);
  }

  @Override
  public void onCameraChanged() {
    updateViewState();
  }

  @Override
  public boolean shouldHide() {
    return !processingUri && cameraUri == null;
  }

  @Override
  public void clearComposer() {
    processingUri = false;
    setCameraUri(null);
  }

  @Override
  public void onClick(View view) {
    if (view == capture) {
      float heightPercent = 1;
      if (!getListener().isFullscreen() && !getListener().isLandscapeLayout()) {
        heightPercent = Math.min((float) cameraView.getHeight() / preview.getView().getHeight(), 1);
      }

      showShutterEffect(shutter);
      processingUri = true;
      setCameraUri(null);
      focus.getPieRenderer().clear();
      CameraManager.get().takePicture(heightPercent, this);
    } else if (view == swapCamera) {
      ((Animatable) swapCamera.getDrawable()).start();
      CameraManager.get().swapCamera();
      cameraDirection = CameraManager.get().getCameraInfo().facing;
    } else if (view == cancel) {
      clearComposer();
    } else if (view == exitFullscreen) {
      getListener().showFullscreen(false);
      fullscreen.setVisibility(View.VISIBLE);
      exitFullscreen.setVisibility(View.GONE);
    } else if (view == fullscreen) {
      getListener().showFullscreen(true);
      fullscreen.setVisibility(View.GONE);
      exitFullscreen.setVisibility(View.VISIBLE);
    } else if (view == allowPermission) {
      // Checks to see if the user has permanently denied this permission. If this is the first
      // time seeing this permission or they only pressed deny previously, they will see the
      // permission request. If they permanently denied the permission, they will be sent to Dialer
      // settings in order enable the permission.
      if (PermissionsUtil.isFirstRequest(getContext(), permissions[0])
          || shouldShowRequestPermissionRationale(permissions[0])) {
        Logger.get(getContext()).logImpression(DialerImpression.Type.CAMERA_PERMISSION_REQUESTED);
        LogUtil.i("CameraComposerFragment.onClick", "Camera permission requested.");
        requestPermissions(permissions, CAMERA_PERMISSION);
      } else {
        Logger.get(getContext()).logImpression(DialerImpression.Type.CAMERA_PERMISSION_SETTINGS);
        LogUtil.i("CameraComposerFragment.onClick", "Settings opened to enable permission.");
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setData(Uri.parse("package:" + getContext().getPackageName()));
        startActivity(intent);
      }
    }
  }

  /**
   * Called by {@link com.android.dialer.callcomposer.camera.ImagePersistTask} when the image is
   * finished being cropped and stored on the device.
   */
  @Override
  public void onMediaReady(Uri uri, String contentType, int width, int height) {
    if (processingUri) {
      processingUri = false;
      setCameraUri(uri);
      // If the user needed the URI before it was ready, uriCallback will be set and we should
      // send the URI to them ASAP.
      if (uriCallback != null) {
        uriCallback.uriReady(uri);
        uriCallback = null;
      }
    } else {
      updateViewState();
    }
  }

  /**
   * Called by {@link com.android.dialer.callcomposer.camera.ImagePersistTask} when the image failed
   * to crop or be stored on the device.
   */
  @Override
  public void onMediaFailed(Exception exception) {
    LogUtil.e("CallComposerFragment.onMediaFailed", null, exception);
    Toast.makeText(getContext(), R.string.camera_media_failure, Toast.LENGTH_LONG).show();
    setCameraUri(null);
    processingUri = false;
    if (uriCallback != null) {
      loading.setVisibility(View.GONE);
      uriCallback = null;
    }
  }

  /**
   * Usually called by {@link CameraManager} if the user does something to interrupt the picture
   * while it's being taken (like switching the camera).
   */
  @Override
  public void onMediaInfo(int what) {
    if (what == MediaCallback.MEDIA_NO_DATA) {
      Toast.makeText(getContext(), R.string.camera_media_failure, Toast.LENGTH_LONG).show();
    }
    setCameraUri(null);
    processingUri = false;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    CameraManager.get().setListener(null);
  }

  private void showShutterEffect(final View shutterVisual) {
    float maxAlpha = .7f;
    int animationDurationMillis = 100;

    AnimationSet animation = new AnimationSet(false /* shareInterpolator */);
    Animation alphaInAnimation = new AlphaAnimation(0.0f, maxAlpha);
    alphaInAnimation.setDuration(animationDurationMillis);
    animation.addAnimation(alphaInAnimation);

    Animation alphaOutAnimation = new AlphaAnimation(maxAlpha, 0.0f);
    alphaOutAnimation.setStartOffset(animationDurationMillis);
    alphaOutAnimation.setDuration(animationDurationMillis);
    animation.addAnimation(alphaOutAnimation);

    animation.setAnimationListener(
        new Animation.AnimationListener() {
          @Override
          public void onAnimationStart(Animation animation) {
            shutterVisual.setVisibility(View.VISIBLE);
          }

          @Override
          public void onAnimationEnd(Animation animation) {
            shutterVisual.setVisibility(View.GONE);
          }

          @Override
          public void onAnimationRepeat(Animation animation) {}
        });
    shutterVisual.startAnimation(animation);
  }

  @NonNull
  public String getMimeType() {
    return "image/jpeg";
  }

  private void setCameraUri(Uri uri) {
    cameraUri = uri;
    // It's possible that if the user takes a picture and press back very quickly, the activity will
    // no longer be alive and when the image cropping process completes, so we need to check that
    // activity is still alive before trying to invoke it.
    if (getListener() != null) {
      updateViewState();
      getListener().composeCall(this);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (PermissionsUtil.hasCameraPermissions(getContext())) {
      permissionView.setVisibility(View.GONE);
      setupCamera();
    }
  }

  /** Updates the state of the buttons and overlays based on the current state of the view */
  private void updateViewState() {
    Assert.isNotNull(cameraView);
    if (isDetached() || getContext() == null) {
      LogUtil.i(
          "CameraComposerFragment.updateViewState", "Fragment detached, cannot update view state");
      return;
    }

    boolean isCameraAvailable = CameraManager.get().isCameraAvailable();
    boolean uriReadyOrProcessing = cameraUri != null || processingUri;

    if (cameraUri != null) {
      previewImageView.setImageURI(cameraUri);
      previewImageView.setVisibility(View.VISIBLE);
      previewImageView.setScaleX(cameraDirection == CameraInfo.CAMERA_FACING_FRONT ? -1 : 1);
    } else {
      previewImageView.setVisibility(View.GONE);
    }

    if (cameraDirection == CameraInfo.CAMERA_FACING_FRONT) {
      swapCamera.setContentDescription(getString(R.string.description_camera_switch_camera_rear));
    } else {
      swapCamera.setContentDescription(getString(R.string.description_camera_switch_camera_facing));
    }

    if (cameraUri == null && isCameraAvailable) {
      CameraManager.get().resetPreview();
      cancel.setVisibility(View.GONE);
    }

    if (!CameraManager.get().hasFrontAndBackCamera()) {
      swapCamera.setVisibility(View.GONE);
    } else {
      swapCamera.setVisibility(uriReadyOrProcessing ? View.GONE : View.VISIBLE);
    }

    capture.setVisibility(uriReadyOrProcessing ? View.GONE : View.VISIBLE);
    cancel.setVisibility(uriReadyOrProcessing ? View.VISIBLE : View.GONE);

    if (uriReadyOrProcessing || getListener().isLandscapeLayout()) {
      fullscreen.setVisibility(View.GONE);
      exitFullscreen.setVisibility(View.GONE);
    } else if (getListener().isFullscreen()) {
      exitFullscreen.setVisibility(View.VISIBLE);
      fullscreen.setVisibility(View.GONE);
    } else {
      exitFullscreen.setVisibility(View.GONE);
      fullscreen.setVisibility(View.VISIBLE);
    }

    swapCamera.setEnabled(isCameraAvailable);
    capture.setEnabled(isCameraAvailable);
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt(CAMERA_DIRECTION_KEY, cameraDirection);
    outState.putParcelable(CAMERA_URI_KEY, cameraUri);
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (permissions.length > 0 && permissions[0].equals(this.permissions[0])) {
      PermissionsUtil.permissionRequested(getContext(), permissions[0]);
    }
    if (requestCode == CAMERA_PERMISSION
        && grantResults.length > 0
        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      Logger.get(getContext()).logImpression(DialerImpression.Type.CAMERA_PERMISSION_GRANTED);
      LogUtil.i("CameraComposerFragment.onRequestPermissionsResult", "Permission granted.");
      permissionView.setVisibility(View.GONE);
      PermissionsUtil.setCameraPrivacyToastShown(getContext());
      setupCamera();
    } else if (requestCode == CAMERA_PERMISSION) {
      Logger.get(getContext()).logImpression(DialerImpression.Type.CAMERA_PERMISSION_DENIED);
      LogUtil.i("CameraComposerFragment.onRequestPermissionsResult", "Permission denied.");
    }
  }

  public void getCameraUriWhenReady(CameraUriCallback callback) {
    if (processingUri) {
      loading.setVisibility(View.VISIBLE);
      uriCallback = callback;
    } else {
      callback.uriReady(cameraUri);
    }
  }

  /** Callback to let the caller know when the URI is ready. */
  public interface CameraUriCallback {
    void uriReady(Uri uri);
  }
}
