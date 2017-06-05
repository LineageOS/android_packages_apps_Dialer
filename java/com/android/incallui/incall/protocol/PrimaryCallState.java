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
import com.android.incallui.call.DialerCall;
import com.android.incallui.videotech.utils.SessionModificationState;
import java.util.Locale;

/** State of the primary call. */
public class PrimaryCallState {
  public final int state;
  public final boolean isVideoCall;
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
  public final boolean isHdAttempting;
  public final boolean isHdAudioCall;
  public final boolean isForwardedNumber;
  public final boolean shouldShowContactPhoto;
  public final long connectTimeMillis;
  public final boolean isVoiceMailNumber;
  public final boolean isRemotelyHeld;
  public final boolean isBusinessNumber;
  public final boolean supportsCallOnHold;

  // TODO: Convert to autovalue. b/34502119
  public static PrimaryCallState createEmptyPrimaryCallState() {
    return new PrimaryCallState(
        DialerCall.State.IDLE,
        false, /* isVideoCall */
        SessionModificationState.NO_REQUEST,
        new DisconnectCause(DisconnectCause.UNKNOWN),
        null, /* connectionLabel */
        null, /* connectionIcon */
        null, /* gatewayNumber */
        null, /* callSubject */
        null, /* callbackNumber */
        false /* isWifi */,
        false /* isConference */,
        false /* isWorkCall */,
        false /* isHdAttempting */,
        false /* isHdAudioCall */,
        false /* isForwardedNumber */,
        false /* shouldShowContactPhoto */,
        0,
        false /* isVoiceMailNumber */,
        false /* isRemotelyHeld */,
        false /* isBusinessNumber */,
        true /* supportsCallOnHold */);
  }

  public PrimaryCallState(
      int state,
      boolean isVideoCall,
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
      boolean isHdAttempting,
      boolean isHdAudioCall,
      boolean isForwardedNumber,
      boolean shouldShowContactPhoto,
      long connectTimeMillis,
      boolean isVoiceMailNumber,
      boolean isRemotelyHeld,
      boolean isBusinessNumber,
      boolean supportsCallOnHold) {
    this.state = state;
    this.isVideoCall = isVideoCall;
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
    this.isHdAttempting = isHdAttempting;
    this.isHdAudioCall = isHdAudioCall;
    this.isForwardedNumber = isForwardedNumber;
    this.shouldShowContactPhoto = shouldShowContactPhoto;
    this.connectTimeMillis = connectTimeMillis;
    this.isVoiceMailNumber = isVoiceMailNumber;
    this.isRemotelyHeld = isRemotelyHeld;
    this.isBusinessNumber = isBusinessNumber;
    this.supportsCallOnHold = supportsCallOnHold;
  }

  @Override
  public String toString() {
    return String.format(
        Locale.US, "PrimaryCallState, state: %d, connectionLabel: %s", state, connectionLabel);
  }
}
