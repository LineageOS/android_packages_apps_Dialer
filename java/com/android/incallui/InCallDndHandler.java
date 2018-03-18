/*
 * Copyright (C) 2018 The LineageOS Project
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

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.state.DialerCallState;
import com.android.incallui.InCallPresenter.InCallState;

public class InCallDndHandler implements InCallPresenter.InCallStateListener {

  private static final String KEY_ENABLE_DND = "incall_enable_dnd";

  private SharedPreferences prefs;
  private DialerCall activeCall;
  private NotificationManager notificationManager;
  private int userSelectedDndMode;

  public InCallDndHandler(Context context) {
    prefs = PreferenceManager.getDefaultSharedPreferences(context);
    notificationManager = context.getSystemService(NotificationManager.class);

    // Save the user's Do Not Disturb mode so that it can be restored when the call ends
    userSelectedDndMode = notificationManager.getCurrentInterruptionFilter();
  }

  @Override
  public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
    DialerCall activeCall = callList.getActiveCall();

    if (activeCall != null && this.activeCall == null) {
      Log.d(this, "Transition to active call " + activeCall);
      handleDndState(activeCall);
      this.activeCall = activeCall;
    } else if (activeCall == null && this.activeCall != null) {
      Log.d(this, "Transition from active call " + this.activeCall);
      handleDndState(this.activeCall);
      this.activeCall = null;
    }
  }

  private void handleDndState(DialerCall call) {
    if (!prefs.getBoolean(KEY_ENABLE_DND, false)) {
      return;
    }
    if (DialerCallState.isConnectingOrConnected(call.getState())) {
      Log.d(this, "Enabling Do Not Disturb mode");
      setDoNotDisturbMode(NotificationManager.INTERRUPTION_FILTER_NONE);
    } else {
      Log.d(this, "Restoring previous Do Not Disturb mode");
      setDoNotDisturbMode(userSelectedDndMode);
    }
  }

  private void setDoNotDisturbMode(int newMode) {
    if (notificationManager.isNotificationPolicyAccessGranted()) {
      notificationManager.setInterruptionFilter(newMode);
    } else {
      Log.e(this, "Failed to set Do Not Disturb mode " + newMode + " due to lack of permissions");
    }
  }
}
