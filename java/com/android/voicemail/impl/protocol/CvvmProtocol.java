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
import com.android.voicemail.impl.sms.OmtpCvvmMessageSender;
import com.android.voicemail.impl.sms.OmtpMessageSender;

/**
 * A flavor of OMTP protocol with a different mobile originated (MO) format
 *
 * <p>Used by carriers such as T-Mobile
 */
public class CvvmProtocol extends VisualVoicemailProtocol {

  private static String IMAP_CHANGE_TUI_PWD_FORMAT = "CHANGE_TUI_PWD PWD=%1$s OLD_PWD=%2$s";
  private static String IMAP_CHANGE_VM_LANG_FORMAT = "CHANGE_VM_LANG Lang=%1$s";
  private static String IMAP_CLOSE_NUT = "CLOSE_NUT";

  @Override
  public OmtpMessageSender createMessageSender(
      Context context,
      PhoneAccountHandle phoneAccountHandle,
      short applicationPort,
      String destinationNumber) {
    return new OmtpCvvmMessageSender(
        context, phoneAccountHandle, applicationPort, destinationNumber);
  }

  @Override
  public String getCommand(String command) {
    if (command == OmtpConstants.IMAP_CHANGE_TUI_PWD_FORMAT) {
      return IMAP_CHANGE_TUI_PWD_FORMAT;
    }
    if (command == OmtpConstants.IMAP_CLOSE_NUT) {
      return IMAP_CLOSE_NUT;
    }
    if (command == OmtpConstants.IMAP_CHANGE_VM_LANG_FORMAT) {
      return IMAP_CHANGE_VM_LANG_FORMAT;
    }
    return super.getCommand(command);
  }
}
