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

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import com.android.dialer.logging.DialerImpression;
import com.android.incallui.video.protocol.VideoCallScreen;
import com.android.incallui.video.protocol.VideoCallScreenDelegate;
import com.android.incallui.videotech.utils.SessionModificationState;

/** Video calling interface. */
public interface VideoTech {

  boolean isAvailable(Context context, PhoneAccountHandle phoneAccountHandle);

  boolean isTransmittingOrReceiving();

  /**
   * Determines if the answer video UI should open the camera directly instead of letting the video
   * tech manage the camera.
   */
  boolean isSelfManagedCamera();

  boolean shouldUseSurfaceView();

  /**
   * Returns true if the video is paused. This is different than if the video stream has been turned
   * off.
   *
   * <p>See {@link #isTransmitting()}
   */
  boolean isPaused();

  VideoCallScreenDelegate createVideoCallScreenDelegate(
      Context context, VideoCallScreen videoCallScreen);

  void onCallStateChanged(Context context, int newState, PhoneAccountHandle phoneAccountHandle);

  void onRemovedFromCallList();

  @SessionModificationState
  int getSessionModificationState();

  void upgradeToVideo(@NonNull Context context);

  void acceptVideoRequest(@NonNull Context context);

  void acceptVideoRequestAsAudio();

  void declineVideoRequest();

  boolean isTransmitting();

  void stopTransmission();

  void resumeTransmission(@NonNull Context context);

  void pause();

  void unpause();

  void setCamera(@Nullable String cameraId);

  void setDeviceOrientation(int rotation);

  /**
   * Called on {@code VideoTechManager.savedTech} when it's first selected and it will always be
   * used.
   */
  void becomePrimary();

  com.android.dialer.logging.VideoTech.Type getVideoTechType();

  /** Listener for video call events. */
  interface VideoTechListener {

    void onVideoTechStateChanged();

    void onSessionModificationStateChanged();

    void onCameraDimensionsChanged(int width, int height);

    void onPeerDimensionsChanged(int width, int height);

    void onVideoUpgradeRequestReceived();

    void onUpgradedToVideo(boolean switchToSpeaker);

    void onImpressionLoggingNeeded(DialerImpression.Type impressionType);
  }
}
