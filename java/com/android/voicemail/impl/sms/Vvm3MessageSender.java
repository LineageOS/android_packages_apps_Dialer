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
package com.android.voicemail.impl.sms;

import android.app.PendingIntent;
import android.content.Context;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;

public class Vvm3MessageSender extends OmtpMessageSender {

  /**
   * Creates a new instance of Vvm3MessageSender.
   *
   * @param applicationPort If set to a value > 0 then a binary sms is sent to this port number.
   *     Otherwise, a standard text SMS is sent.
   */
  public Vvm3MessageSender(
      Context context,
      PhoneAccountHandle phoneAccountHandle,
      short applicationPort,
      String destinationNumber) {
    super(context, phoneAccountHandle, applicationPort, destinationNumber);
  }

  @Override
  public void requestVvmActivation(@Nullable PendingIntent sentIntent) {
    // Activation not supported for VVM3, send a status request instead.
    requestVvmStatus(sentIntent);
  }

  @Override
  public void requestVvmDeactivation(@Nullable PendingIntent sentIntent) {
    // Deactivation not supported for VVM3, do nothing
  }

  @Override
  public void requestVvmStatus(@Nullable PendingIntent sentIntent) {
    // Status message:
    // STATUS
    StringBuilder sb = new StringBuilder().append("STATUS");
    sendSms(sb.toString(), sentIntent);
  }
}
