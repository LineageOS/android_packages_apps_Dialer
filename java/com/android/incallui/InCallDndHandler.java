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
import com.android.incallui.InCallPresenter.InCallState;

public class InCallDndHandler implements
        InCallPresenter.InCallStateListener {

    private static final String KEY_ENABLE_DND = "incall_enable_dnd";

    private SharedPreferences mPrefs;
    private DialerCall mActiveCall;
    private NotificationManager mNotificationManager;
    private int mUserSelectedDndMode;

    public InCallDndHandler(Context context) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mNotificationManager = context.getSystemService(NotificationManager.class);

        // Save the user's Do Not Disturb mode so that it can be restored when the call ends
        mUserSelectedDndMode = mNotificationManager.getCurrentInterruptionFilter();
    }

    @Override
    public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
        DialerCall activeCall = callList.getActiveCall();

        if (activeCall != null && mActiveCall == null) {
            Log.d(this, "Transition to active call " + activeCall);
            handleDndState(activeCall);
            mActiveCall = activeCall;
        } else if (activeCall == null && mActiveCall != null) {
            Log.d(this, "Transition from active call " + mActiveCall);
            handleDndState(mActiveCall);
            mActiveCall = null;
        }
    }

    private void handleDndState(DialerCall call) {
        if (DialerCall.State.isConnectingOrConnected(call.getState())) {
            if (mPrefs.getBoolean(KEY_ENABLE_DND, false)) {
                Log.d(this, "Enabling Do Not Disturb mode");
                setDoNotDisturbMode(NotificationManager.INTERRUPTION_FILTER_NONE);
            }
        } else {
            if (mPrefs.getBoolean(KEY_ENABLE_DND, false)) {
                Log.d(this, "Restoring previous Do Not Disturb mode");
                setDoNotDisturbMode(mUserSelectedDndMode);
            }
        }
    }

    private void setDoNotDisturbMode(int newMode) {
        if (mNotificationManager.isNotificationPolicyAccessGranted()) {
            mNotificationManager.setInterruptionFilter(newMode);
        } else {
            Log.e(this, "Failed to set Do Not Disturb mode " + newMode
                + " due to lack of permissions");
        }
    }
}
