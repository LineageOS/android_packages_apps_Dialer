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

package com.android.incallui.videotech.empty;

import com.android.incallui.videotech.VideoTech;

/** Default video tech that is always available but doesn't do anything. */
public class EmptyVideoTech implements VideoTech {

  @Override
  public boolean isAvailable() {
    return false;
  }

  @Override
  public boolean isTransmittingOrReceiving() {
    return false;
  }

  @Override
  public void onCallStateChanged(int newState) {}

  @Override
  public int getSessionModificationState() {
    return VideoTech.SESSION_MODIFICATION_STATE_NO_REQUEST;
  }

  @Override
  public void upgradeToVideo() {}

  @Override
  public void acceptVideoRequest() {}

  @Override
  public void acceptVideoRequestAsAudio() {}

  @Override
  public void declineVideoRequest() {}

  @Override
  public boolean isTransmitting() {
    return false;
  }

  @Override
  public void stopTransmission() {}

  @Override
  public void resumeTransmission() {}

  @Override
  public void pause() {}

  @Override
  public void unpause() {}

  @Override
  public void setCamera(String cameraId) {}

  @Override
  public void setDeviceOrientation(int rotation) {}
}
