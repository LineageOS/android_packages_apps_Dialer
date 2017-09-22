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
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.lightbringer.Lightbringer;
import com.android.dialer.lightbringer.LightbringerListener;
import com.android.dialer.logging.DialerImpression;
import com.android.incallui.video.protocol.VideoCallScreen;
import com.android.incallui.video.protocol.VideoCallScreenDelegate;
import com.android.incallui.videotech.VideoTech;
import com.android.incallui.videotech.utils.SessionModificationState;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public class LightbringerTech implements VideoTech, LightbringerListener {
  private final Lightbringer lightbringer;
  private final VideoTechListener listener;
  private final Call call;
  private final String callingNumber;
  private int callState = Call.STATE_NEW;
  private boolean isRemoteUpgradeAvailabilityQueried;

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
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
      LogUtil.v("LightbringerTech.isAvailable", "upgrade unavailable, only supported on O+");
      return false;
    }

    if (!ConfigProviderBindings.get(context)
        .getBoolean("enable_lightbringer_video_upgrade", true)) {
      LogUtil.v("LightbringerTech.isAvailable", "upgrade disabled by flag");
      return false;
    }

    if (callState != Call.STATE_ACTIVE) {
      LogUtil.v("LightbringerTech.isAvailable", "upgrade unavailable, call must be active");
      return false;
    }
    Optional<Boolean> localResult = lightbringer.supportsUpgrade(context, callingNumber);
    if (localResult.isPresent()) {
      LogUtil.v(
          "LightbringerTech.isAvailable", "upgrade supported in local cache: " + localResult.get());
      return localResult.get();
    }

    if (!isRemoteUpgradeAvailabilityQueried) {
      LogUtil.v("LightbringerTech.isAvailable", "reachability unknown, starting remote query");
      isRemoteUpgradeAvailabilityQueried = true;
      lightbringer.updateReachability(context, ImmutableList.of(callingNumber));
    }

    return false;
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
  public boolean isPaused() {
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
  public void upgradeToVideo(@NonNull Context context) {
    listener.onImpressionLoggingNeeded(DialerImpression.Type.LIGHTBRINGER_UPGRADE_REQUESTED);
    lightbringer.requestUpgrade(context, call);
  }

  @Override
  public void acceptVideoRequest(@NonNull Context context) {
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
  public void resumeTransmission(@NonNull Context context) {
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
  public void becomePrimary() {
    listener.onImpressionLoggingNeeded(
        DialerImpression.Type.UPGRADE_TO_VIDEO_CALL_BUTTON_SHOWN_FOR_LIGHTBRINGER);
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
