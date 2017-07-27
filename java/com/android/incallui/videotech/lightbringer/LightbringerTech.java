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

package com.android.incallui.videotech.lightbringer;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telecom.Call;
import com.android.contacts.common.compat.telecom.TelecomManagerCompat;
import com.android.dialer.common.Assert;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.lightbringer.Lightbringer;
import com.android.dialer.lightbringer.LightbringerListener;
import com.android.incallui.video.protocol.VideoCallScreen;
import com.android.incallui.video.protocol.VideoCallScreenDelegate;
import com.android.incallui.videotech.VideoTech;
import com.android.incallui.videotech.utils.SessionModificationState;

public class LightbringerTech implements VideoTech, LightbringerListener {
  private final Lightbringer lightbringer;
  private final VideoTechListener listener;
  private final Call call;
  private final String callingNumber;
  private int callState = Call.STATE_NEW;

  public LightbringerTech(
      @NonNull Lightbringer lightbringer,
      @NonNull VideoTechListener listener,
      @NonNull Call call,
      @NonNull String callingNumber) {
    this.lightbringer = Assert.isNotNull(lightbringer);
    this.listener = Assert.isNotNull(listener);
    this.call = Assert.isNotNull(call);
    this.callingNumber = Assert.isNotNull(callingNumber);

    lightbringer.registerListener(this);
  }

  @Override
  public boolean isAvailable(Context context) {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        && ConfigProviderBindings.get(context).getBoolean("enable_lightbringer_video_upgrade", true)
        && callState == Call.STATE_ACTIVE
        && lightbringer.supportsUpgrade(context, callingNumber)
        && TelecomManagerCompat.supportsHandover();
  }

  @Override
  public boolean isTransmittingOrReceiving() {
    return false;
  }

  @Override
  public boolean isSelfManagedCamera() {
    return false;
  }

  @Override
  public boolean shouldUseSurfaceView() {
    return false;
  }

  @Override
  public VideoCallScreenDelegate createVideoCallScreenDelegate(
      Context context, VideoCallScreen videoCallScreen) {
    throw Assert.createUnsupportedOperationFailException();
  }

  @Override
  public void onCallStateChanged(Context context, int newState) {
    if (newState == Call.STATE_DISCONNECTING) {
      lightbringer.unregisterListener(this);
    }

    callState = newState;
  }

  @Override
  public void onRemovedFromCallList() {
    lightbringer.unregisterListener(this);
  }

  @Override
  public int getSessionModificationState() {
    return SessionModificationState.NO_REQUEST;
  }

  @Override
  public void upgradeToVideo() {
    lightbringer.requestUpgrade(call);
  }

  @Override
  public void acceptVideoRequest() {
    throw Assert.createUnsupportedOperationFailException();
  }

  @Override
  public void acceptVideoRequestAsAudio() {
    throw Assert.createUnsupportedOperationFailException();
  }

  @Override
  public void declineVideoRequest() {
    throw Assert.createUnsupportedOperationFailException();
  }

  @Override
  public boolean isTransmitting() {
    return false;
  }

  @Override
  public void stopTransmission() {
    throw Assert.createUnsupportedOperationFailException();
  }

  @Override
  public void resumeTransmission() {
    throw Assert.createUnsupportedOperationFailException();
  }

  @Override
  public void pause() {}

  @Override
  public void unpause() {}

  @Override
  public void setCamera(@Nullable String cameraId) {
    throw Assert.createUnsupportedOperationFailException();
  }

  @Override
  public void setDeviceOrientation(int rotation) {
    throw Assert.createUnsupportedOperationFailException();
  }

  @Override
  public void onLightbringerStateChanged() {
    listener.onVideoTechStateChanged();
  }
}
