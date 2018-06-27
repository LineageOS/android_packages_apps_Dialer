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
import android.support.annotation.ColorInt;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.telecom.DisconnectCause;
import android.text.TextUtils;
import com.android.dialer.assisteddialing.TransformationInfo;
import com.android.dialer.common.Assert;
import com.android.dialer.preferredsim.suggestion.SuggestionProvider;
import com.android.incallui.call.state.DialerCallState;
import com.android.incallui.videotech.utils.SessionModificationState;
import com.google.auto.value.AutoValue;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

/** State of the primary call. */
@AutoValue
public abstract class PrimaryCallState {

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

  public abstract int state();

  public abstract boolean isVideoCall();

  @SessionModificationState
  public abstract int sessionModificationState();

  public abstract DisconnectCause disconnectCause();

  @Nullable
  public abstract String connectionLabel();

  public abstract @ColorInt int primaryColor();

  @Nullable
  public abstract SuggestionProvider.Reason simSuggestionReason();

  @Nullable
  public abstract Drawable connectionIcon();

  @Nullable
  public abstract String gatewayNumber();

  @Nullable
  public abstract String callSubject();

  @Nullable
  public abstract String callbackNumber();

  public abstract boolean isWifi();

  public abstract boolean isConference();

  public abstract boolean isWorkCall();

  public abstract boolean isHdAttempting();

  public abstract boolean isHdAudioCall();

  public abstract boolean isForwardedNumber();

  public abstract boolean shouldShowContactPhoto();

  public abstract long connectTimeMillis();

  public abstract boolean isVoiceMailNumber();

  public abstract boolean isRemotelyHeld();

  public abstract boolean isBusinessNumber();

  public abstract boolean supportsCallOnHold();

  public abstract @ButtonState int swapToSecondaryButtonState();

  public abstract boolean isAssistedDialed();

  @Nullable
  public abstract String customLabel();

  @Nullable
  public abstract TransformationInfo assistedDialingExtras();

  public static Builder builder() {
    return new AutoValue_PrimaryCallState.Builder()
        .setState(DialerCallState.IDLE)
        .setIsVideoCall(false)
        .setSessionModificationState(SessionModificationState.NO_REQUEST)
        .setDisconnectCause(new DisconnectCause(DisconnectCause.UNKNOWN))
        .setIsWifi(false)
        .setIsConference(false)
        .setIsWorkCall(false)
        .setIsHdAttempting(false)
        .setIsHdAudioCall(false)
        .setIsForwardedNumber(false)
        .setShouldShowContactPhoto(false)
        .setConnectTimeMillis(0)
        .setIsVoiceMailNumber(false)
        .setIsRemotelyHeld(false)
        .setIsBusinessNumber(false)
        .setSupportsCallOnHold(true)
        .setSwapToSecondaryButtonState(ButtonState.NOT_SUPPORT)
        .setIsAssistedDialed(false)
        .setPrimaryColor(0);
  }

  /** Builder class for primary call state info. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setState(int state);

    public abstract Builder setIsVideoCall(boolean isVideoCall);

    public abstract Builder setSessionModificationState(
        @SessionModificationState int sessionModificationState);

    public abstract Builder setDisconnectCause(DisconnectCause disconnectCause);

    public abstract Builder setConnectionLabel(String connectionLabel);

    public abstract Builder setSimSuggestionReason(SuggestionProvider.Reason reason);

    public abstract Builder setConnectionIcon(Drawable connectionIcon);

    public abstract Builder setPrimaryColor(@ColorInt int color);

    public abstract Builder setGatewayNumber(String gatewayNumber);

    public abstract Builder setCallSubject(String callSubject);

    public abstract Builder setCallbackNumber(String callbackNumber);

    public abstract Builder setIsWifi(boolean isWifi);

    public abstract Builder setIsConference(boolean isConference);

    public abstract Builder setIsWorkCall(boolean isWorkCall);

    public abstract Builder setIsHdAttempting(boolean isHdAttempting);

    public abstract Builder setIsHdAudioCall(boolean isHdAudioCall);

    public abstract Builder setIsForwardedNumber(boolean isForwardedNumber);

    public abstract Builder setShouldShowContactPhoto(boolean shouldShowContactPhoto);

    public abstract Builder setConnectTimeMillis(long connectTimeMillis);

    public abstract Builder setIsVoiceMailNumber(boolean isVoiceMailNumber);

    public abstract Builder setIsRemotelyHeld(boolean isRemotelyHeld);

    public abstract Builder setIsBusinessNumber(boolean isBusinessNumber);

    public abstract Builder setSupportsCallOnHold(boolean supportsCallOnHold);

    public abstract Builder setSwapToSecondaryButtonState(
        @ButtonState int swapToSecondaryButtonState);

    public abstract Builder setIsAssistedDialed(boolean isAssistedDialed);

    public abstract Builder setCustomLabel(String customLabel);

    public abstract Builder setAssistedDialingExtras(TransformationInfo assistedDialingExtras);

    abstract PrimaryCallState autoBuild();

    public PrimaryCallState build() {
      PrimaryCallState primaryCallState = autoBuild();
      if (!TextUtils.isEmpty(primaryCallState.customLabel())) {
        Assert.checkArgument(primaryCallState.state() == DialerCallState.CALL_PENDING);
      }
      return primaryCallState;
    }
  }

  public static PrimaryCallState empty() {
    return PrimaryCallState.builder().build();
  }

  @Override
  public String toString() {
    return String.format(
        Locale.US, "PrimaryCallState, state: %d, connectionLabel: %s", state(), connectionLabel());
  }
}
