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

package com.android.incallui.incall.protocol;

import android.graphics.drawable.Drawable;
import android.telecom.DisconnectCause;
import android.telecom.VideoProfile;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.DialerCall.SessionModificationState;
import java.util.Locale;

/** State of the primary call. */
public class PrimaryCallState {
  public final int state;
  public final int videoState;
  @SessionModificationState public final int sessionModificationState;
  public final DisconnectCause disconnectCause;
  public final String connectionLabel;
  public final Drawable connectionIcon;
  public final String gatewayNumber;
  public final String callSubject;
  public final String callbackNumber;
  public final boolean isWifi;
  public final boolean isConference;
  public final boolean isWorkCall;
  public final boolean isHdAudioCall;
  public final boolean isForwardedNumber;
  public final boolean shouldShowContactPhoto;
  public final long connectTimeMillis;
  public final boolean isVoiceMailNumber;
  public final boolean isRemotelyHeld;

  // TODO: Convert to autovalue. b/34502119
  public static PrimaryCallState createEmptyPrimaryCallState() {
    return new PrimaryCallState(
        DialerCall.State.IDLE,
        VideoProfile.STATE_AUDIO_ONLY,
        DialerCall.SESSION_MODIFICATION_STATE_NO_REQUEST,
        new DisconnectCause(DisconnectCause.UNKNOWN),
        null, /* connectionLabel */
        null, /* connectionIcon */
        null, /* gatewayNumber */
        null, /* callSubject */
        null, /* callbackNumber */
        false /* isWifi */,
        false /* isConference */,
        false /* isWorkCall */,
        false /* isHdAudioCall */,
        false /* isForwardedNumber */,
        false /* shouldShowContactPhoto */,
        0,
        false /* isVoiceMailNumber */,
        false /* isRemotelyHeld */);
  }

  public PrimaryCallState(
      int state,
      int videoState,
      @SessionModificationState int sessionModificationState,
      DisconnectCause disconnectCause,
      String connectionLabel,
      Drawable connectionIcon,
      String gatewayNumber,
      String callSubject,
      String callbackNumber,
      boolean isWifi,
      boolean isConference,
      boolean isWorkCall,
      boolean isHdAudioCall,
      boolean isForwardedNumber,
      boolean shouldShowContactPhoto,
      long connectTimeMillis,
      boolean isVoiceMailNumber,
      boolean isRemotelyHeld) {
    this.state = state;
    this.videoState = videoState;
    this.sessionModificationState = sessionModificationState;
    this.disconnectCause = disconnectCause;
    this.connectionLabel = connectionLabel;
    this.connectionIcon = connectionIcon;
    this.gatewayNumber = gatewayNumber;
    this.callSubject = callSubject;
    this.callbackNumber = callbackNumber;
    this.isWifi = isWifi;
    this.isConference = isConference;
    this.isWorkCall = isWorkCall;
    this.isHdAudioCall = isHdAudioCall;
    this.isForwardedNumber = isForwardedNumber;
    this.shouldShowContactPhoto = shouldShowContactPhoto;
    this.connectTimeMillis = connectTimeMillis;
    this.isVoiceMailNumber = isVoiceMailNumber;
    this.isRemotelyHeld = isRemotelyHeld;
  }

  @Override
  public String toString() {
    return String.format(
        Locale.US, "PrimaryCallState, state: %d, connectionLabel: %s", state, connectionLabel);
  }
}
