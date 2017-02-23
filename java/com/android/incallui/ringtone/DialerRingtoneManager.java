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
 * limitations under the License
 */

package com.android.incallui.ringtone;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall.State;
import java.util.Objects;

/**
 * Class that determines when ringtones should be played and can play the call waiting tone when
 * necessary.
 */
public class DialerRingtoneManager {

  /*
   * Flag used to determine if the Dialer is responsible for playing ringtones for incoming calls.
   * Once we're ready to enable Dialer Ringing, these flags should be removed.
   */
  private static final boolean IS_DIALER_RINGING_ENABLED = false;
  private final InCallTonePlayer mInCallTonePlayer;
  private final CallList mCallList;
  private Boolean mIsDialerRingingEnabledForTesting;

  /**
   * Creates the DialerRingtoneManager with the given {@link InCallTonePlayer}.
   *
   * @param inCallTonePlayer the tone player used to play in-call tones.
   * @param callList the CallList used to check for {@link State#CALL_WAITING}
   * @throws NullPointerException if inCallTonePlayer or callList are null
   */
  public DialerRingtoneManager(
      @NonNull InCallTonePlayer inCallTonePlayer, @NonNull CallList callList) {
    mInCallTonePlayer = Objects.requireNonNull(inCallTonePlayer);
    mCallList = Objects.requireNonNull(callList);
  }

  /**
   * Determines if a ringtone should be played for the given call state (see {@link State}) and
   * {@link Uri}.
   *
   * @param callState the call state for the call being checked.
   * @param ringtoneUri the ringtone to potentially play.
   * @return {@code true} if the ringtone should be played, {@code false} otherwise.
   */
  public boolean shouldPlayRingtone(int callState, @Nullable Uri ringtoneUri) {
    return isDialerRingingEnabled()
        && translateCallStateForCallWaiting(callState) == State.INCOMING
        && ringtoneUri != null;
  }

  /**
   * Determines if an incoming call should vibrate as well as ring.
   *
   * @param resolver {@link ContentResolver} used to look up the {@link
   *     Settings.System#VIBRATE_WHEN_RINGING} setting.
   * @return {@code true} if the call should vibrate, {@code false} otherwise.
   */
  public boolean shouldVibrate(ContentResolver resolver) {
    return Settings.System.getInt(resolver, Settings.System.VIBRATE_WHEN_RINGING, 0) != 0;
  }

  /**
   * The incoming callState is never set as {@link State#CALL_WAITING} because {@link
   * DialerCall#translateState(int)} doesn't account for that case, check for it here
   */
  private int translateCallStateForCallWaiting(int callState) {
    if (callState != State.INCOMING) {
      return callState;
    }
    return mCallList.getActiveCall() == null ? State.INCOMING : State.CALL_WAITING;
  }

  private boolean isDialerRingingEnabled() {
    boolean enabledFlag =
        mIsDialerRingingEnabledForTesting != null
            ? mIsDialerRingingEnabledForTesting
            : IS_DIALER_RINGING_ENABLED;
    return VERSION.SDK_INT >= VERSION_CODES.N && enabledFlag;
  }

  /**
   * Determines if a call waiting tone should be played for the the given call state (see {@link
   * State}).
   *
   * @param callState the call state for the call being checked.
   * @return {@code true} if the call waiting tone should be played, {@code false} otherwise.
   */
  public boolean shouldPlayCallWaitingTone(int callState) {
    return isDialerRingingEnabled()
        && translateCallStateForCallWaiting(callState) == State.CALL_WAITING
        && !mInCallTonePlayer.isPlayingTone();
  }

  /** Plays the call waiting tone. */
  public void playCallWaitingTone() {
    if (!isDialerRingingEnabled()) {
      return;
    }
    mInCallTonePlayer.play(InCallTonePlayer.TONE_CALL_WAITING);
  }

  /** Stops playing the call waiting tone. */
  public void stopCallWaitingTone() {
    if (!isDialerRingingEnabled()) {
      return;
    }
    mInCallTonePlayer.stop();
  }

  void setDialerRingingEnabledForTesting(boolean status) {
    mIsDialerRingingEnabledForTesting = status;
  }
}
