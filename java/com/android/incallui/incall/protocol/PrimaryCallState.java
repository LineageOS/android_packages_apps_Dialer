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
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.telecom.DisconnectCause;
import android.text.TextUtils;
import com.android.dialer.assisteddialing.TransformationInfo;
import com.android.dialer.common.Assert;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.DialerCall.State;
import com.android.incallui.videotech.utils.SessionModificationState;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

/** State of the primary call. */
public class PrimaryCallState {

  /**
   * Button state that will be invisible if not supported, visible but invalid if disabled, or
   * visible if enabled.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({ButtonState.NOT_SUPPORT, ButtonState.DISABLED, ButtonState.ENABLED})
  public @interface ButtonState {
    int NOT_SUPPORT = 0;
    int DISABLED = 1;
    int ENABLED = 2;
  }

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
  public final @ButtonState int swapToSecondaryButtonState;
  public final boolean isAssistedDialed;
  @Nullable public final String customLabel;
  @Nullable public final TransformationInfo assistedDialingExtras;

  // TODO: Convert to autovalue. a bug
  public static PrimaryCallState createEmptyPrimaryCallState() {
    return createEmptyPrimaryCallStateWithState(DialerCall.State.IDLE, null);
  }

  public static PrimaryCallState createEmptyPrimaryCallStateWithState(
      int state, String customLabel) {
    return new PrimaryCallState(
        state,
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
        true /* supportsCallOnHold */,
        ButtonState.NOT_SUPPORT /* swapToSecondaryButtonState */,
        false /* isAssistedDialed */,
        customLabel,
        null /* assistedDialingExtras */);
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
      boolean supportsCallOnHold,
      @ButtonState int swapToSecondaryButtonState,
      boolean isAssistedDialed,
      @Nullable String customLabel,
      @Nullable TransformationInfo assistedDialingExtras) {
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
    this.swapToSecondaryButtonState = swapToSecondaryButtonState;
    this.isAssistedDialed = isAssistedDialed;
    if (!TextUtils.isEmpty(customLabel)) {
      Assert.checkArgument(state == State.CALL_PENDING);
    }
    this.customLabel = customLabel;
    this.assistedDialingExtras = assistedDialingExtras;
  }

  @Override
  public String toString() {
    return String.format(
        Locale.US, "PrimaryCallState, state: %d, connectionLabel: %s", state, connectionLabel);
  }
}
