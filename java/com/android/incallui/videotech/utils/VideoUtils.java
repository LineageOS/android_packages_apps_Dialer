/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.incallui.videotech.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import com.android.dialer.util.PermissionsUtil;

public class VideoUtils {

  public static boolean hasSentVideoUpgradeRequest(@SessionModificationState int state) {
    return state == SessionModificationState.WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE
        || state == SessionModificationState.UPGRADE_TO_VIDEO_REQUEST_FAILED
        || state == SessionModificationState.REQUEST_REJECTED
        || state == SessionModificationState.UPGRADE_TO_VIDEO_REQUEST_TIMED_OUT;
  }

  public static boolean hasReceivedVideoUpgradeRequest(@SessionModificationState int state) {
    return state == SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST;
  }

  public static boolean hasCameraPermissionAndShownPrivacyToast(@NonNull Context context) {
    return PermissionsUtil.hasCameraPrivacyToastShown(context) && hasCameraPermission(context);
  }

  public static boolean hasCameraPermission(@NonNull Context context) {
    return ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED;
  }
}
