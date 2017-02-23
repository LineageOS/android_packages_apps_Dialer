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

package com.android.incallui.call;

import android.content.Context;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.telecom.VideoProfile;
import com.android.dialer.compat.CompatUtils;
import com.android.dialer.util.DialerUtils;
import com.android.incallui.call.DialerCall.SessionModificationState;
import java.util.Objects;

public class VideoUtils {

  private static final String PREFERENCE_CAMERA_ALLOWED_BY_USER = "camera_allowed_by_user";

  public static boolean isVideoCall(@Nullable DialerCall call) {
    return call != null && isVideoCall(call.getVideoState());
  }

  public static boolean isVideoCall(int videoState) {
    return CompatUtils.isVideoCompatible()
        && (VideoProfile.isTransmissionEnabled(videoState)
            || VideoProfile.isReceptionEnabled(videoState));
  }

  public static boolean hasSentVideoUpgradeRequest(@Nullable DialerCall call) {
    return call != null && hasSentVideoUpgradeRequest(call.getSessionModificationState());
  }

  public static boolean hasSentVideoUpgradeRequest(@SessionModificationState int state) {
    return state == DialerCall.SESSION_MODIFICATION_STATE_WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE
        || state == DialerCall.SESSION_MODIFICATION_STATE_UPGRADE_TO_VIDEO_REQUEST_FAILED
        || state == DialerCall.SESSION_MODIFICATION_STATE_REQUEST_REJECTED
        || state == DialerCall.SESSION_MODIFICATION_STATE_UPGRADE_TO_VIDEO_REQUEST_TIMED_OUT;
  }

  public static boolean hasReceivedVideoUpgradeRequest(@Nullable DialerCall call) {
    return call != null && hasReceivedVideoUpgradeRequest(call.getSessionModificationState());
  }

  public static boolean hasReceivedVideoUpgradeRequest(@SessionModificationState int state) {
    return state == DialerCall.SESSION_MODIFICATION_STATE_RECEIVED_UPGRADE_TO_VIDEO_REQUEST;
  }

  public static boolean isBidirectionalVideoCall(DialerCall call) {
    return CompatUtils.isVideoCompatible() && VideoProfile.isBidirectional(call.getVideoState());
  }

  public static boolean isTransmissionEnabled(DialerCall call) {
    if (!CompatUtils.isVideoCompatible()) {
      return false;
    }

    return VideoProfile.isTransmissionEnabled(call.getVideoState());
  }

  public static boolean isIncomingVideoCall(DialerCall call) {
    if (!VideoUtils.isVideoCall(call)) {
      return false;
    }
    final int state = call.getState();
    return (state == DialerCall.State.INCOMING) || (state == DialerCall.State.CALL_WAITING);
  }

  public static boolean isActiveVideoCall(DialerCall call) {
    return VideoUtils.isVideoCall(call) && call.getState() == DialerCall.State.ACTIVE;
  }

  public static boolean isOutgoingVideoCall(DialerCall call) {
    if (!VideoUtils.isVideoCall(call)) {
      return false;
    }
    final int state = call.getState();
    return DialerCall.State.isDialing(state)
        || state == DialerCall.State.CONNECTING
        || state == DialerCall.State.SELECT_PHONE_ACCOUNT;
  }

  public static boolean isAudioCall(DialerCall call) {
    if (!CompatUtils.isVideoCompatible()) {
      return true;
    }

    return call != null && VideoProfile.isAudioOnly(call.getVideoState());
  }

  // TODO (ims-vt) Check if special handling is needed for CONF calls.
  public static boolean canVideoPause(DialerCall call) {
    return isVideoCall(call) && call.getState() == DialerCall.State.ACTIVE;
  }

  public static VideoProfile makeVideoPauseProfile(@NonNull DialerCall call) {
    Objects.requireNonNull(call);
    if (VideoProfile.isAudioOnly(call.getVideoState())) {
      throw new IllegalStateException();
    }
    return new VideoProfile(getPausedVideoState(call.getVideoState()));
  }

  public static VideoProfile makeVideoUnPauseProfile(@NonNull DialerCall call) {
    Objects.requireNonNull(call);
    return new VideoProfile(getUnPausedVideoState(call.getVideoState()));
  }

  public static int getUnPausedVideoState(int videoState) {
    return videoState & (~VideoProfile.STATE_PAUSED);
  }

  public static int getPausedVideoState(int videoState) {
    return videoState | VideoProfile.STATE_PAUSED;
  }

  public static boolean hasCameraPermissionAndAllowedByUser(@NonNull Context context) {
    return isCameraAllowedByUser(context) && hasCameraPermission(context);
  }

  public static boolean hasCameraPermission(@NonNull Context context) {
    return ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED;
  }

  public static boolean isCameraAllowedByUser(@NonNull Context context) {
    return DialerUtils.getDefaultSharedPreferenceForDeviceProtectedStorageContext(context)
        .getBoolean(PREFERENCE_CAMERA_ALLOWED_BY_USER, false);
  }

  public static void setCameraAllowedByUser(@NonNull Context context) {
    DialerUtils.getDefaultSharedPreferenceForDeviceProtectedStorageContext(context)
        .edit()
        .putBoolean(PREFERENCE_CAMERA_ALLOWED_BY_USER, true)
        .apply();
  }
}
