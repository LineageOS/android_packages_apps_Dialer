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
import com.android.voicemail.impl.NeededForTesting;
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

  private final String mProvisioningStatus;
  private final String mStatusReturnCode;
  private final String mSubscriptionUrl;
  private final String mServerAddress;
  private final String mTuiAccessNumber;
  private final String mClientSmsDestinationNumber;
  private final String mImapPort;
  private final String mImapUserName;
  private final String mImapPassword;
  private final String mSmtpPort;
  private final String mSmtpUserName;
  private final String mSmtpPassword;
  private final String mTuiPasswordLength;

  @Override
  public String toString() {
    return "StatusMessage [mProvisioningStatus="
        + mProvisioningStatus
        + ", mStatusReturnCode="
        + mStatusReturnCode
        + ", mSubscriptionUrl="
        + mSubscriptionUrl
        + ", mServerAddress="
        + mServerAddress
        + ", mTuiAccessNumber="
        + mTuiAccessNumber
        + ", mClientSmsDestinationNumber="
        + mClientSmsDestinationNumber
        + ", mImapPort="
        + mImapPort
        + ", mImapUserName="
        + mImapUserName
        + ", mImapPassword="
        + VvmLog.pii(mImapPassword)
        + ", mSmtpPort="
        + mSmtpPort
        + ", mSmtpUserName="
        + mSmtpUserName
        + ", mSmtpPassword="
        + VvmLog.pii(mSmtpPassword)
        + ", mTuiPasswordLength="
        + mTuiPasswordLength
        + "]";
  }

  public StatusMessage(Bundle wrappedData) {
    mProvisioningStatus = unquote(getString(wrappedData, OmtpConstants.PROVISIONING_STATUS));
    mStatusReturnCode = getString(wrappedData, OmtpConstants.RETURN_CODE);
    mSubscriptionUrl = getString(wrappedData, OmtpConstants.SUBSCRIPTION_URL);
    mServerAddress = getString(wrappedData, OmtpConstants.SERVER_ADDRESS);
    mTuiAccessNumber = getString(wrappedData, OmtpConstants.TUI_ACCESS_NUMBER);
    mClientSmsDestinationNumber =
        getString(wrappedData, OmtpConstants.CLIENT_SMS_DESTINATION_NUMBER);
    mImapPort = getString(wrappedData, OmtpConstants.IMAP_PORT);
    mImapUserName = getString(wrappedData, OmtpConstants.IMAP_USER_NAME);
    mImapPassword = getString(wrappedData, OmtpConstants.IMAP_PASSWORD);
    mSmtpPort = getString(wrappedData, OmtpConstants.SMTP_PORT);
    mSmtpUserName = getString(wrappedData, OmtpConstants.SMTP_USER_NAME);
    mSmtpPassword = getString(wrappedData, OmtpConstants.SMTP_PASSWORD);
    mTuiPasswordLength = getString(wrappedData, OmtpConstants.TUI_PASSWORD_LENGTH);
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
    return mProvisioningStatus;
  }

  /** @return the return-code of the status SMS. */
  public String getReturnCode() {
    return mStatusReturnCode;
  }

  /**
   * @return the URL of the voicemail server. This is the URL to send the users to for subscribing
   *     to the visual voicemail service.
   */
  @NeededForTesting
  public String getSubscriptionUrl() {
    return mSubscriptionUrl;
  }

  /**
   * @return the voicemail server address. Either server IP address or fully qualified domain name.
   */
  public String getServerAddress() {
    return mServerAddress;
  }

  /**
   * @return the Telephony User Interface number to call to access voicemails directly from the IVR.
   */
  @NeededForTesting
  public String getTuiAccessNumber() {
    return mTuiAccessNumber;
  }

  /** @return the number to which client originated SMSes should be sent to. */
  @NeededForTesting
  public String getClientSmsDestinationNumber() {
    return mClientSmsDestinationNumber;
  }

  /** @return the IMAP server port to talk to. */
  public String getImapPort() {
    return mImapPort;
  }

  /** @return the IMAP user name to be used for authentication. */
  public String getImapUserName() {
    return mImapUserName;
  }

  /** @return the IMAP password to be used for authentication. */
  public String getImapPassword() {
    return mImapPassword;
  }

  /** @return the SMTP server port to talk to. */
  @NeededForTesting
  public String getSmtpPort() {
    return mSmtpPort;
  }

  /** @return the SMTP user name to be used for SMTP authentication. */
  @NeededForTesting
  public String getSmtpUserName() {
    return mSmtpUserName;
  }

  /** @return the SMTP password to be used for SMTP authentication. */
  @NeededForTesting
  public String getSmtpPassword() {
    return mSmtpPassword;
  }

  public String getTuiPasswordLength() {
    return mTuiPasswordLength;
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
