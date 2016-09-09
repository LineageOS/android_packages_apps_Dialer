/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package com.android.incallui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.telecom.DisconnectCause;

import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.state.DialerCallState;
import com.android.incallui.InCallPresenter.InCallState;

public class InCallVibrationHandler extends Handler implements
    InCallPresenter.InCallStateListener {

  private static final int MSG_VIBRATE_45_SEC = 1;

  private static final String KEY_VIBRATE_CALL_WAITING = "incall_vibrate_call_waiting";
  private static final String KEY_VIBRATE_OUTGOING = "incall_vibrate_outgoing";
  private static final String KEY_VIBRATE_45SECS = "incall_vibrate_45secs";
  private static final String KEY_VIBRATE_HANGUP = "incall_vibrate_hangup";

  private SharedPreferences prefs;
  private Vibrator vibrator;
  private DialerCall activeCall;

  public InCallVibrationHandler(Context context) {
    prefs = PreferenceManager.getDefaultSharedPreferences(context);
    vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
  }

  @Override
  public void handleMessage(Message msg) {
    switch (msg.what) {
      case MSG_VIBRATE_45_SEC:
        vibrate(70, 0, 0);
        sendEmptyMessageDelayed(MSG_VIBRATE_45_SEC, 60000);
        break;
    }
  }

  @Override
  public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
    DialerCall activeCall = callList.getActiveCall();

    if (activeCall != null && this.activeCall == null) {
      Log.d(this, "Transition to active call " + activeCall);
      if (activeCall.isOutgoing()) {
        handleOutgoingCallVibration(activeCall);
      }
      this.activeCall = activeCall;
    } else if (activeCall != null && callList.getIncomingCall() != null
        && !callList.getIncomingCall().equals(activeCall)) {
      Log.d(this, "New incoming call" + callList.getIncomingCall());
      handleCallWaitingVibration(activeCall);
    } else if (activeCall == null && this.activeCall != null) {
      Log.d(this, "Transition from active call " + this.activeCall);
      handleCallEnd(this.activeCall);
      this.activeCall = null;
    }
  }

  private void handleOutgoingCallVibration(DialerCall call) {
    long durationMillis = System.currentTimeMillis() - call.getConnectTimeMillis();
    Log.d(this, "Start outgoing call: duration = " + durationMillis);

    if (prefs.getBoolean(KEY_VIBRATE_OUTGOING, false) && durationMillis < 200) {
      vibrate(100, 200, 0);
    }
    if (prefs.getBoolean(KEY_VIBRATE_45SECS, false)) {
      start45SecondVibration(durationMillis);
    }
  }

  private void handleCallWaitingVibration(DialerCall call) {
    Log.d(this, "Start call waiting vibration");
    if (prefs.getBoolean(KEY_VIBRATE_CALL_WAITING, false)) {
      vibrate(200, 300, 500);
    }
  }

  private void handleCallEnd(DialerCall call) {
    long durationMillis = System.currentTimeMillis() - call.getConnectTimeMillis();
    DisconnectCause cause = call.getDisconnectCause();
    boolean localDisconnect =
        // Disconnection not yet processed
        call.getState() == DialerCallState.DISCONNECTING ||
        // Disconnection already processed
        (cause != null && cause.getCode() == DisconnectCause.LOCAL);

    Log.d(this, "Ending active call: duration = " + durationMillis
        + ", locally disconnected = " + localDisconnect);

    if (prefs.getBoolean(KEY_VIBRATE_HANGUP, false)
        && !localDisconnect && durationMillis > 500) {
      vibrate(50, 100, 50);
    }
    // Stop 45-second vibration
    removeMessages(MSG_VIBRATE_45_SEC);
  }

  private void start45SecondVibration(long callDurationMillis) {
    callDurationMillis = callDurationMillis % 60000;
    Log.d(this, "vibrate start @" + callDurationMillis);
    removeMessages(MSG_VIBRATE_45_SEC);

    long timer;
    if (callDurationMillis > 45000) {
        // Schedule the alarm at the next minute + 45 secs
        timer = 45000 + 60000 - callDurationMillis;
    } else {
        // Schedule the alarm at the first 45 second mark
        timer = 45000 - callDurationMillis;
    }
    sendEmptyMessageDelayed(MSG_VIBRATE_45_SEC, timer);
  }

  private void vibrate(int v1, int p1, int v2) {
    long[] pattern = new long[] {
      0, v1, p1, v2
    };
    vibrator.vibrate(pattern, -1);
  }
}
