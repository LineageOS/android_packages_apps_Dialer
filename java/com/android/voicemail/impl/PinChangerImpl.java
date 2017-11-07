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

package com.android.voicemail.impl;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.Network;
import android.os.Build.VERSION_CODES;
import android.support.annotation.WorkerThread;
import android.telecom.PhoneAccountHandle;
import com.android.dialer.common.Assert;
import com.android.voicemail.PinChanger;
import com.android.voicemail.impl.imap.ImapHelper;
import com.android.voicemail.impl.imap.ImapHelper.InitializingException;
import com.android.voicemail.impl.mail.MessagingException;
import com.android.voicemail.impl.sync.VvmNetworkRequest;
import com.android.voicemail.impl.sync.VvmNetworkRequest.NetworkWrapper;
import com.android.voicemail.impl.sync.VvmNetworkRequest.RequestFailedException;

@TargetApi(VERSION_CODES.O)
class PinChangerImpl implements PinChanger {

  private final Context context;
  private final PhoneAccountHandle phoneAccountHandle;

  private static final String KEY_SCRAMBLED_PIN = "default_old_pin"; // legacy name, DO NOT CHANGE

  PinChangerImpl(Context context, PhoneAccountHandle phoneAccountHandle) {
    this.context = context;
    this.phoneAccountHandle = phoneAccountHandle;
  }

  @WorkerThread
  @Override
  @ChangePinResult
  public int changePin(String oldPin, String newPin) {
    Assert.isWorkerThread();
    OmtpVvmCarrierConfigHelper config = new OmtpVvmCarrierConfigHelper(context, phoneAccountHandle);
    VoicemailStatus.Editor status = VoicemailStatus.edit(context, phoneAccountHandle);
    try (NetworkWrapper networkWrapper =
        VvmNetworkRequest.getNetwork(config, phoneAccountHandle, status)) {
      Network network = networkWrapper.get();
      try (ImapHelper helper = new ImapHelper(context, phoneAccountHandle, network, status)) {
        return helper.changePin(oldPin, newPin);
      } catch (InitializingException | MessagingException e) {
        VvmLog.e(
            "VoicemailClientImpl.changePin", "ChangePinNetworkRequestCallback: onAvailable: " + e);
        return PinChanger.CHANGE_PIN_SYSTEM_ERROR;
      }

    } catch (RequestFailedException e) {
      return PinChanger.CHANGE_PIN_SYSTEM_ERROR;
    }
  }

  @Override
  public void setScrambledPin(String pin) {
    new VisualVoicemailPreferences(context, phoneAccountHandle)
        .edit()
        .putString(KEY_SCRAMBLED_PIN, pin)
        .apply();
    if (pin == null) {
      new OmtpVvmCarrierConfigHelper(context, phoneAccountHandle)
          .handleEvent(
              VoicemailStatus.edit(context, phoneAccountHandle), OmtpEvents.CONFIG_PIN_SET);
    }
  }

  @Override
  public String getScrambledPin() {
    return new VisualVoicemailPreferences(context, phoneAccountHandle).getString(KEY_SCRAMBLED_PIN);
  }

  @Override
  public PinSpecification getPinSpecification() {
    PinSpecification result = new PinSpecification();
    VisualVoicemailPreferences preferences =
        new VisualVoicemailPreferences(context, phoneAccountHandle);
    // The OMTP pin length format is {min}-{max}
    String[] lengths = preferences.getString(OmtpConstants.TUI_PASSWORD_LENGTH, "").split("-");
    if (lengths.length == 2) {
      try {
        result.minLength = Integer.parseInt(lengths[0]);
        result.maxLength = Integer.parseInt(lengths[1]);
      } catch (NumberFormatException e) {
        // do nothing, return default value;
      }
    }
    return result;
  }
}
