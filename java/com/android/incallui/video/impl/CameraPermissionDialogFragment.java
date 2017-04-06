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

package com.android.incallui.video.impl;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import com.android.dialer.common.FragmentUtils;

/** Dialog fragment to ask for camera permission from user. */
public class CameraPermissionDialogFragment extends DialogFragment {

  static CameraPermissionDialogFragment newInstance() {
    CameraPermissionDialogFragment fragment = new CameraPermissionDialogFragment();
    return fragment;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle bundle) {
    return new AlertDialog.Builder(getContext())
        .setTitle(R.string.camera_permission_dialog_title)
        .setMessage(R.string.camera_permission_dialog_message)
        .setPositiveButton(
            R.string.camera_permission_dialog_positive_button,
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                CameraPermissionDialogCallback fragment =
                    FragmentUtils.getParentUnsafe(
                        CameraPermissionDialogFragment.this, CameraPermissionDialogCallback.class);
                fragment.onCameraPermissionGranted();
              }
            })
        .setNegativeButton(
            R.string.camera_permission_dialog_negative_button,
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
              }
            })
        .create();
  }

  /** Callback for being granted camera permission. */
  public interface CameraPermissionDialogCallback {
    void onCameraPermissionGranted();
  }
}
