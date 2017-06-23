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

import android.content.Context;
import android.telecom.PhoneAccountHandle;
import com.android.voicemail.impl.OmtpConstants;
import com.android.voicemail.impl.sms.OmtpMessageSender;
import com.android.voicemail.impl.sms.OmtpStandardMessageSender;

public class OmtpProtocol extends VisualVoicemailProtocol {

  @Override
  public OmtpMessageSender createMessageSender(
      Context context,
      PhoneAccountHandle phoneAccountHandle,
      short applicationPort,
      String destinationNumber) {
    return new OmtpStandardMessageSender(
        context,
        phoneAccountHandle,
        applicationPort,
        destinationNumber,
        OmtpConstants.getClientType(),
        OmtpConstants.PROTOCOL_VERSION1_1,
        null /*clientPrefix*/);
  }
}
