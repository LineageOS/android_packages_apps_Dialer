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

package com.android.incallui.videotech;

import android.support.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Video calling interface. */
public interface VideoTech {

  boolean isAvailable();

  boolean isTransmittingOrReceiving();

  /**
   * Determines if the answer video UI should open the camera directly instead of letting the video
   * tech manage the camera.
   */
  boolean isSelfManagedCamera();

  void onCallStateChanged(int newState);

  @SessionModificationState
  int getSessionModificationState();

  void upgradeToVideo();

  void acceptVideoRequest();

  void acceptVideoRequestAsAudio();

  void declineVideoRequest();

  boolean isTransmitting();

  void stopTransmission();

  void resumeTransmission();

  void pause();

  void unpause();

  void setCamera(String cameraId);

  void setDeviceOrientation(int rotation);

  /** Listener for video call events. */
  interface VideoTechListener {

    void onVideoTechStateChanged();

    void onSessionModificationStateChanged();

    void onCameraDimensionsChanged(int width, int height);

    void onPeerDimensionsChanged(int width, int height);

    void onVideoUpgradeRequestReceived();
  }

  /**
   * Defines different states of session modify requests, which are used to upgrade to video, or
   * downgrade to audio.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    SESSION_MODIFICATION_STATE_NO_REQUEST,
    SESSION_MODIFICATION_STATE_WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE,
    SESSION_MODIFICATION_STATE_REQUEST_FAILED,
    SESSION_MODIFICATION_STATE_RECEIVED_UPGRADE_TO_VIDEO_REQUEST,
    SESSION_MODIFICATION_STATE_UPGRADE_TO_VIDEO_REQUEST_TIMED_OUT,
    SESSION_MODIFICATION_STATE_UPGRADE_TO_VIDEO_REQUEST_FAILED,
    SESSION_MODIFICATION_STATE_REQUEST_REJECTED,
    SESSION_MODIFICATION_STATE_WAITING_FOR_RESPONSE
  })
  @interface SessionModificationState {}

  int SESSION_MODIFICATION_STATE_NO_REQUEST = 0;
  int SESSION_MODIFICATION_STATE_WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE = 1;
  int SESSION_MODIFICATION_STATE_REQUEST_FAILED = 2;
  int SESSION_MODIFICATION_STATE_RECEIVED_UPGRADE_TO_VIDEO_REQUEST = 3;
  int SESSION_MODIFICATION_STATE_UPGRADE_TO_VIDEO_REQUEST_TIMED_OUT = 4;
  int SESSION_MODIFICATION_STATE_UPGRADE_TO_VIDEO_REQUEST_FAILED = 5;
  int SESSION_MODIFICATION_STATE_REQUEST_REJECTED = 6;
  int SESSION_MODIFICATION_STATE_WAITING_FOR_RESPONSE = 7;
}
