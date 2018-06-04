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

package com.android.incallui.videotech.duo;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telecom.Call;
import android.telecom.PhoneAccountHandle;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DefaultFutureCallback;
import com.android.dialer.configprovider.ConfigProviderComponent;
import com.android.dialer.duo.Duo;
import com.android.dialer.duo.DuoListener;
import com.android.dialer.logging.DialerImpression;
import com.android.incallui.video.protocol.VideoCallScreen;
import com.android.incallui.video.protocol.VideoCallScreenDelegate;
import com.android.incallui.videotech.VideoTech;
import com.android.incallui.videotech.utils.SessionModificationState;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

public class DuoVideoTech implements VideoTech, DuoListener {
  private final Duo duo;
  private final VideoTechListener listener;
  private final Call call;
  private final String callingNumber;
  private int callState = Call.STATE_NEW;
  private boolean isRemoteUpgradeAvailabilityQueried;

  public DuoVideoTech(
      @NonNull Duo duo,
      @NonNull VideoTechListener listener,
      @NonNull Call call,
      @NonNull String callingNumber) {
    this.duo = Assert.isNotNull(duo);
    this.listener = Assert.isNotNull(listener);
    this.call = Assert.isNotNull(call);
    this.callingNumber = Assert.isNotNull(callingNumber);

    duo.registerListener(this);
  }

  @Override
  public boolean isAvailable(Context context, PhoneAccountHandle phoneAccountHandle) {
    if (!ConfigProviderComponent.get(context)
        .getConfigProvider()
        .getBoolean("enable_lightbringer_video_upgrade", true)) {
      LogUtil.v("DuoVideoTech.isAvailable", "upgrade disabled by flag");
      return false;
    }

    if (callState != Call.STATE_ACTIVE) {
      LogUtil.v("DuoVideoTech.isAvailable", "upgrade unavailable, call must be active");
      return false;
    }
    Optional<Boolean> localResult = duo.supportsUpgrade(context, callingNumber, phoneAccountHandle);
    if (localResult.isPresent()) {
      LogUtil.v(
          "DuoVideoTech.isAvailable", "upgrade supported in local cache: " + localResult.get());
      return localResult.get();
    }

    if (!isRemoteUpgradeAvailabilityQueried) {
      LogUtil.v("DuoVideoTech.isAvailable", "reachability unknown, starting remote query");
      isRemoteUpgradeAvailabilityQueried = true;
      Futures.addCallback(
          duo.updateReachability(context, ImmutableList.of(callingNumber)),
          new DefaultFutureCallback<>(),
          MoreExecutors.directExecutor());
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
  public void onCallStateChanged(
      Context context, int newState, PhoneAccountHandle phoneAccountHandle) {
    if (newState == Call.STATE_DISCONNECTING) {
      duo.unregisterListener(this);
    }

    callState = newState;
  }

  @Override
  public void onRemovedFromCallList() {
    duo.unregisterListener(this);
  }

  @Override
  public int getSessionModificationState() {
    return SessionModificationState.NO_REQUEST;
  }

  @Override
  public void upgradeToVideo(@NonNull Context context) {
    listener.onImpressionLoggingNeeded(DialerImpression.Type.LIGHTBRINGER_UPGRADE_REQUESTED);
    duo.requestUpgrade(context, call);
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
  public void setDeviceOrientation(int rotation) {}

  @Override
  public void onDuoStateChanged() {
    listener.onVideoTechStateChanged();
  }

  @Override
  public com.android.dialer.logging.VideoTech.Type getVideoTechType() {
    return com.android.dialer.logging.VideoTech.Type.LIGHTBRINGER_VIDEO_TECH;
  }
}
