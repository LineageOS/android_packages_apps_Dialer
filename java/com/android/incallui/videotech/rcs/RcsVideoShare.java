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

package com.android.incallui.videotech.rcs;

import android.support.annotation.NonNull;
import android.telecom.Call;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.enrichedcall.EnrichedCallCapabilities;
import com.android.dialer.enrichedcall.EnrichedCallManager;
import com.android.dialer.enrichedcall.EnrichedCallManager.CapabilitiesListener;
import com.android.dialer.enrichedcall.Session;
import com.android.dialer.enrichedcall.videoshare.VideoShareListener;
import com.android.incallui.videotech.VideoTech;

/** Allows the in-call UI to make video calls over RCS. */
public class RcsVideoShare implements VideoTech, CapabilitiesListener, VideoShareListener {
  private final EnrichedCallManager enrichedCallManager;
  private final VideoTechListener listener;
  private final String callingNumber;
  private int previousCallState = Call.STATE_NEW;
  private long inviteSessionId = Session.NO_SESSION_ID;
  private long transmittingSessionId = Session.NO_SESSION_ID;
  private long receivingSessionId = Session.NO_SESSION_ID;

  private @SessionModificationState int sessionModificationState =
      VideoTech.SESSION_MODIFICATION_STATE_NO_REQUEST;

  public RcsVideoShare(
      @NonNull EnrichedCallManager enrichedCallManager,
      @NonNull VideoTechListener listener,
      @NonNull String callingNumber) {
    this.enrichedCallManager = Assert.isNotNull(enrichedCallManager);
    this.listener = Assert.isNotNull(listener);
    this.callingNumber = Assert.isNotNull(callingNumber);

    enrichedCallManager.registerCapabilitiesListener(this);
    enrichedCallManager.registerVideoShareListener(this);
  }

  @Override
  public boolean isAvailable() {
    EnrichedCallCapabilities capabilities = enrichedCallManager.getCapabilities(callingNumber);
    return capabilities != null && capabilities.supportsVideoShare();
  }

  @Override
  public boolean isTransmittingOrReceiving() {
    return transmittingSessionId != Session.NO_SESSION_ID
        || receivingSessionId != Session.NO_SESSION_ID;
  }

  @Override
  public boolean isSelfManagedCamera() {
    return true;
  }

  @Override
  public void onCallStateChanged(int newState) {
    if (newState == Call.STATE_DISCONNECTING) {
      enrichedCallManager.unregisterVideoShareListener(this);
      enrichedCallManager.unregisterCapabilitiesListener(this);
    }

    if (newState != previousCallState && newState == Call.STATE_ACTIVE) {
      // Per spec, request capabilities when the call becomes active
      enrichedCallManager.requestCapabilities(callingNumber);
    }

    previousCallState = newState;
  }

  @Override
  public int getSessionModificationState() {
    return sessionModificationState;
  }

  private void setSessionModificationState(@SessionModificationState int state) {
    if (state != sessionModificationState) {
      LogUtil.i(
          "RcsVideoShare.setSessionModificationState", "%d -> %d", sessionModificationState, state);
      sessionModificationState = state;
      listener.onSessionModificationStateChanged();
    }
  }

  @Override
  public void upgradeToVideo() {
    LogUtil.enterBlock("RcsVideoShare.upgradeToVideo");
    transmittingSessionId = enrichedCallManager.startVideoShareSession(callingNumber);
    if (transmittingSessionId != Session.NO_SESSION_ID) {
      setSessionModificationState(
          VideoTech.SESSION_MODIFICATION_STATE_WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE);
    }
  }

  @Override
  public void acceptVideoRequest() {
    LogUtil.enterBlock("RcsVideoShare.acceptVideoRequest");
    if (enrichedCallManager.acceptVideoShareSession(inviteSessionId)) {
      receivingSessionId = inviteSessionId;
    }
    inviteSessionId = Session.NO_SESSION_ID;
    setSessionModificationState(VideoTech.SESSION_MODIFICATION_STATE_NO_REQUEST);
  }

  @Override
  public void acceptVideoRequestAsAudio() {
    throw Assert.createUnsupportedOperationFailException();
  }

  @Override
  public void declineVideoRequest() {
    LogUtil.enterBlock("RcsVideoTech.declineUpgradeRequest");
    enrichedCallManager.endVideoShareSession(
        enrichedCallManager.getVideoShareInviteSessionId(callingNumber));
    inviteSessionId = Session.NO_SESSION_ID;
    setSessionModificationState(VideoTech.SESSION_MODIFICATION_STATE_NO_REQUEST);
  }

  @Override
  public boolean isTransmitting() {
    return transmittingSessionId != Session.NO_SESSION_ID;
  }

  @Override
  public void stopTransmission() {
    LogUtil.enterBlock("RcsVideoTech.stopTransmission");
  }

  @Override
  public void resumeTransmission() {
    LogUtil.enterBlock("RcsVideoTech.resumeTransmission");
  }

  @Override
  public void pause() {}

  @Override
  public void unpause() {}

  @Override
  public void setCamera(String cameraId) {}

  @Override
  public void setDeviceOrientation(int rotation) {}

  @Override
  public void onCapabilitiesUpdated() {
    listener.onVideoTechStateChanged();
  }

  @Override
  public void onVideoShareChanged() {
    long existingInviteSessionId = inviteSessionId;

    inviteSessionId = enrichedCallManager.getVideoShareInviteSessionId(callingNumber);
    if (inviteSessionId != Session.NO_SESSION_ID) {
      if (existingInviteSessionId == Session.NO_SESSION_ID) {
        // This is a new invite
        setSessionModificationState(
            VideoTech.SESSION_MODIFICATION_STATE_RECEIVED_UPGRADE_TO_VIDEO_REQUEST);
        listener.onVideoUpgradeRequestReceived();
      }
    } else {
      setSessionModificationState(VideoTech.SESSION_MODIFICATION_STATE_NO_REQUEST);
    }

    if (sessionIsClosed(transmittingSessionId)) {
      LogUtil.i("RcsVideoShare.onSessionClosed", "transmitting session closed");
      transmittingSessionId = Session.NO_SESSION_ID;
    }

    if (sessionIsClosed(receivingSessionId)) {
      LogUtil.i("RcsVideoShare.onSessionClosed", "receiving session closed");
      receivingSessionId = Session.NO_SESSION_ID;
    }

    listener.onVideoTechStateChanged();
  }

  private boolean sessionIsClosed(long sessionId) {
    return sessionId != Session.NO_SESSION_ID
        && enrichedCallManager.getVideoShareSession(sessionId) == null;
  }
}
