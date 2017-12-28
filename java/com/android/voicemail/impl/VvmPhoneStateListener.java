/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.voicemail.impl;

import android.content.Context;
import android.telecom.PhoneAccountHandle;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import com.android.voicemail.impl.sync.SyncTask;
import com.android.voicemail.impl.sync.VoicemailStatusQueryHelper;
import com.android.voicemail.impl.sync.VvmAccountManager;

/**
 * Check if service is lost and indicate this in the voicemail status. TODO(a bug): Not used
 * for now, restore it.
 */
public class VvmPhoneStateListener extends PhoneStateListener {

  private static final String TAG = "VvmPhoneStateListener";

  private PhoneAccountHandle phoneAccount;
  private Context context;
  private int previousState = -1;

  public VvmPhoneStateListener(Context context, PhoneAccountHandle accountHandle) {
    // TODO(twyen): a bug too much trouble to call super constructor through reflection,
    // just use non-phoneAccountHandle version for now.
    super();
    this.context = context;
    phoneAccount = accountHandle;
  }

  @Override
  public void onServiceStateChanged(ServiceState serviceState) {
    if (phoneAccount == null) {
      VvmLog.e(
          TAG,
          "onServiceStateChanged on phoneAccount "
              + phoneAccount
              + " with invalid phoneAccountHandle, ignoring");
      return;
    }

    int state = serviceState.getState();
    if (state == previousState
        || (state != ServiceState.STATE_IN_SERVICE
            && previousState != ServiceState.STATE_IN_SERVICE)) {
      // Only interested in state changes or transitioning into or out of "in service".
      // Otherwise just quit.
      previousState = state;
      return;
    }

    OmtpVvmCarrierConfigHelper helper = new OmtpVvmCarrierConfigHelper(context, phoneAccount);

    if (state == ServiceState.STATE_IN_SERVICE) {
      VoicemailStatusQueryHelper voicemailStatusQueryHelper =
          new VoicemailStatusQueryHelper(context);
      if (voicemailStatusQueryHelper.isVoicemailSourceConfigured(phoneAccount)) {
        if (!voicemailStatusQueryHelper.isNotificationsChannelActive(phoneAccount)) {
          VvmLog.v(TAG, "Notifications channel is active for " + phoneAccount);
          helper.handleEvent(
              VoicemailStatus.edit(context, phoneAccount), OmtpEvents.NOTIFICATION_IN_SERVICE);
        }
      }

      if (VvmAccountManager.isAccountActivated(context, phoneAccount)) {
        VvmLog.v(TAG, "Signal returned: requesting resync for " + phoneAccount);
        // If the source is already registered, run a full sync in case something was missed
        // while signal was down.
        SyncTask.start(context, phoneAccount);
      } else {
        VvmLog.v(TAG, "Signal returned: reattempting activation for " + phoneAccount);
        // Otherwise initiate an activation because this means that an OMTP source was
        // recognized but either the activation text was not successfully sent or a response
        // was not received.
        helper.startActivation();
      }
    } else {
      VvmLog.v(TAG, "Notifications channel is inactive for " + phoneAccount);

      if (!VvmAccountManager.isAccountActivated(context, phoneAccount)) {
        return;
      }
      helper.handleEvent(
          VoicemailStatus.edit(context, phoneAccount), OmtpEvents.NOTIFICATION_SERVICE_LOST);
    }
    previousState = state;
  }
}
