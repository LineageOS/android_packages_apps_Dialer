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
package com.android.voicemail.impl.sms;

import android.os.Bundle;
import com.android.voicemail.impl.OmtpConstants;
import com.android.voicemail.impl.VisualVoicemailPreferences;
import com.android.voicemail.impl.VvmLog;

/**
 * Structured data representation of OMTP STATUS message.
 *
 * <p>The getters will return null if the field was not set in the message body or it could not be
 * parsed.
 */
public class StatusMessage {
  // NOTE: Following Status SMS fields are not yet parsed, as they do not seem
  // to be useful for initial omtp source implementation.
  // lang, g_len, vs_len, pw_len, pm, gm, vtc, vt

  private final String provisioningStatus;
  private final String statusReturnCode;
  private final String serverAddress;
  private final String imapPort;
  private final String imapUserName;
  private final String imapPassword;
  private final String tuiPasswordLength;

  @Override
  public String toString() {
    return "StatusMessage [mProvisioningStatus="
        + provisioningStatus
        + ", mStatusReturnCode="
        + statusReturnCode
        + ", mServerAddress="
        + serverAddress
        + ", mImapPort="
        + imapPort
        + ", mImapUserName="
        + imapUserName
        + ", mImapPassword="
        + VvmLog.pii(imapPassword)
        + ", mTuiPasswordLength="
        + tuiPasswordLength
        + "]";
  }

  public StatusMessage(Bundle wrappedData) {
    provisioningStatus = unquote(getString(wrappedData, OmtpConstants.PROVISIONING_STATUS));
    statusReturnCode = getString(wrappedData, OmtpConstants.RETURN_CODE);
    serverAddress = getString(wrappedData, OmtpConstants.SERVER_ADDRESS);
    imapPort = getString(wrappedData, OmtpConstants.IMAP_PORT);
    imapUserName = getString(wrappedData, OmtpConstants.IMAP_USER_NAME);
    imapPassword = getString(wrappedData, OmtpConstants.IMAP_PASSWORD);
    tuiPasswordLength = getString(wrappedData, OmtpConstants.TUI_PASSWORD_LENGTH);
  }

  private static String unquote(String string) {
    if (string.length() < 2) {
      return string;
    }
    if (string.startsWith("\"") && string.endsWith("\"")) {
      return string.substring(1, string.length() - 1);
    }
    return string;
  }

  /** @return the subscriber's VVM provisioning status. */
  public String getProvisioningStatus() {
    return provisioningStatus;
  }

  /** @return the return-code of the status SMS. */
  public String getReturnCode() {
    return statusReturnCode;
  }

  /**
   * @return the voicemail server address. Either server IP address or fully qualified domain name.
   */
  public String getServerAddress() {
    return serverAddress;
  }

  /** @return the IMAP server port to talk to. */
  public String getImapPort() {
    return imapPort;
  }

  /** @return the IMAP user name to be used for authentication. */
  public String getImapUserName() {
    return imapUserName;
  }

  /** @return the IMAP password to be used for authentication. */
  public String getImapPassword() {
    return imapPassword;
  }

  public String getTuiPasswordLength() {
    return tuiPasswordLength;
  }

  private static String getString(Bundle bundle, String key) {
    String value = bundle.getString(key);
    if (value == null) {
      return "";
    }
    return value;
  }

  /** Saves a StatusMessage to the {@link VisualVoicemailPreferences}. Not all fields are saved. */
  public VisualVoicemailPreferences.Editor putStatus(VisualVoicemailPreferences.Editor editor) {
    return editor
        .putString(OmtpConstants.IMAP_PORT, getImapPort())
        .putString(OmtpConstants.SERVER_ADDRESS, getServerAddress())
        .putString(OmtpConstants.IMAP_USER_NAME, getImapUserName())
        .putString(OmtpConstants.IMAP_PASSWORD, getImapPassword())
        .putString(OmtpConstants.TUI_PASSWORD_LENGTH, getTuiPasswordLength());
  }
}
