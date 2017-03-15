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

package com.android.voicemail.impl.protocol;

import android.app.PendingIntent;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import com.android.voicemail.impl.ActivationTask;
import com.android.voicemail.impl.DefaultOmtpEventHandler;
import com.android.voicemail.impl.OmtpConstants;
import com.android.voicemail.impl.OmtpEvents;
import com.android.voicemail.impl.OmtpVvmCarrierConfigHelper;
import com.android.voicemail.impl.VoicemailStatus;
import com.android.voicemail.impl.sms.OmtpMessageSender;
import com.android.voicemail.impl.sms.StatusMessage;

public abstract class VisualVoicemailProtocol {

  /** Activation should cause the carrier to respond with a STATUS SMS. */
  public void startActivation(OmtpVvmCarrierConfigHelper config, PendingIntent sentIntent) {
    OmtpMessageSender messageSender = ProtocolHelper.getMessageSender(this, config);
    if (messageSender != null) {
      messageSender.requestVvmActivation(sentIntent);
    }
  }

  public void startDeactivation(OmtpVvmCarrierConfigHelper config) {
    OmtpMessageSender messageSender = ProtocolHelper.getMessageSender(this, config);
    if (messageSender != null) {
      messageSender.requestVvmDeactivation(null);
    }
  }

  public boolean supportsProvisioning() {
    return false;
  }

  public void startProvisioning(
      ActivationTask task,
      PhoneAccountHandle handle,
      OmtpVvmCarrierConfigHelper config,
      VoicemailStatus.Editor editor,
      StatusMessage message,
      Bundle data) {
    // Do nothing
  }

  public void requestStatus(OmtpVvmCarrierConfigHelper config, @Nullable PendingIntent sentIntent) {
    OmtpMessageSender messageSender = ProtocolHelper.getMessageSender(this, config);
    if (messageSender != null) {
      messageSender.requestVvmStatus(sentIntent);
    }
  }

  public abstract OmtpMessageSender createMessageSender(
      Context context,
      PhoneAccountHandle phoneAccountHandle,
      short applicationPort,
      String destinationNumber);

  /**
   * Translate an OMTP IMAP command to the protocol specific one. For example, changing the TUI
   * password on OMTP is XCHANGE_TUI_PWD, but on CVVM and VVM3 it is CHANGE_TUI_PWD.
   *
   * @param command A String command in {@link OmtpConstants}, the exact
   *     instance should be used instead of its' value.
   * @returns Translated command, or {@code null} if not available in this protocol
   */
  public String getCommand(String command) {
    return command;
  }

  public void handleEvent(
      Context context,
      OmtpVvmCarrierConfigHelper config,
      VoicemailStatus.Editor status,
      OmtpEvents event) {
    DefaultOmtpEventHandler.handleEvent(context, config, status, event);
  }

  /**
   * Given an VVM SMS with an unknown {@code event}, let the protocol attempt to translate it into
   * an equivalent STATUS SMS. Returns {@code null} if it cannot be translated.
   */
  @Nullable
  public Bundle translateStatusSmsBundle(
      OmtpVvmCarrierConfigHelper config, String event, Bundle data) {
    return null;
  }
}
