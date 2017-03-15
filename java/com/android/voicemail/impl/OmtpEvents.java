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

package com.android.voicemail.impl;

import android.support.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Events internal to the OMTP client. These should be translated into {@link
 * android.provider.VoicemailContract.Status} error codes before writing into the voicemail status
 * table.
 */
public enum OmtpEvents {

  // Configuration State
  CONFIG_REQUEST_STATUS_SUCCESS(Type.CONFIGURATION, true),

  CONFIG_PIN_SET(Type.CONFIGURATION, true),
  // The voicemail PIN is replaced with a generated PIN, user should change it.
  CONFIG_DEFAULT_PIN_REPLACED(Type.CONFIGURATION, true),
  CONFIG_ACTIVATING(Type.CONFIGURATION, true),
  // There are already activation records, this is only a book-keeping activation.
  CONFIG_ACTIVATING_SUBSEQUENT(Type.CONFIGURATION, true),
  CONFIG_STATUS_SMS_TIME_OUT(Type.CONFIGURATION),
  CONFIG_SERVICE_NOT_AVAILABLE(Type.CONFIGURATION),

  // Data channel State

  // A new sync has started, old errors in data channel should be cleared.
  DATA_IMAP_OPERATION_STARTED(Type.DATA_CHANNEL, true),
  // Successfully downloaded/uploaded data from the server, which means the data channel is clear.
  DATA_IMAP_OPERATION_COMPLETED(Type.DATA_CHANNEL, true),
  // The port provided in the STATUS SMS is invalid.
  DATA_INVALID_PORT(Type.DATA_CHANNEL),
  // No connection to the internet, and the carrier requires cellular data
  DATA_NO_CONNECTION_CELLULAR_REQUIRED(Type.DATA_CHANNEL),
  // No connection to the internet.
  DATA_NO_CONNECTION(Type.DATA_CHANNEL),
  // Address lookup for the server hostname failed. DNS error?
  DATA_CANNOT_RESOLVE_HOST_ON_NETWORK(Type.DATA_CHANNEL),
  // All destination address that resolves to the server hostname are rejected or timed out
  DATA_ALL_SOCKET_CONNECTION_FAILED(Type.DATA_CHANNEL),
  // Failed to establish SSL with the server, either with a direct SSL connection or by
  // STARTTLS command
  DATA_CANNOT_ESTABLISH_SSL_SESSION(Type.DATA_CHANNEL),
  // Identity of the server cannot be verified.
  DATA_SSL_INVALID_HOST_NAME(Type.DATA_CHANNEL),
  // The server rejected our username/password
  DATA_BAD_IMAP_CREDENTIAL(Type.DATA_CHANNEL),

  DATA_AUTH_UNKNOWN_USER(Type.DATA_CHANNEL),
  DATA_AUTH_UNKNOWN_DEVICE(Type.DATA_CHANNEL),
  DATA_AUTH_INVALID_PASSWORD(Type.DATA_CHANNEL),
  DATA_AUTH_MAILBOX_NOT_INITIALIZED(Type.DATA_CHANNEL),
  DATA_AUTH_SERVICE_NOT_PROVISIONED(Type.DATA_CHANNEL),
  DATA_AUTH_SERVICE_NOT_ACTIVATED(Type.DATA_CHANNEL),
  DATA_AUTH_USER_IS_BLOCKED(Type.DATA_CHANNEL),

  // A command to the server didn't result with an "OK" or continuation request
  DATA_REJECTED_SERVER_RESPONSE(Type.DATA_CHANNEL),
  // The server did not greet us with a "OK", possibly not a IMAP server.
  DATA_INVALID_INITIAL_SERVER_RESPONSE(Type.DATA_CHANNEL),
  // An IOException occurred while trying to open an ImapConnection
  // TODO: reduce scope
  DATA_IOE_ON_OPEN(Type.DATA_CHANNEL),
  // The SELECT command on a mailbox is rejected
  DATA_MAILBOX_OPEN_FAILED(Type.DATA_CHANNEL),
  // An IOException has occurred
  // TODO: reduce scope
  DATA_GENERIC_IMAP_IOE(Type.DATA_CHANNEL),
  // An SslException has occurred while opening an ImapConnection
  // TODO: reduce scope
  DATA_SSL_EXCEPTION(Type.DATA_CHANNEL),

  // Notification Channel

  // Cell signal restored, can received VVM SMSs
  NOTIFICATION_IN_SERVICE(Type.NOTIFICATION_CHANNEL, true),
  // Cell signal lost, cannot received VVM SMSs
  NOTIFICATION_SERVICE_LOST(Type.NOTIFICATION_CHANNEL, false),

  // Other
  OTHER_SOURCE_REMOVED(Type.OTHER, false),

  // VVM3
  VVM3_NEW_USER_SETUP_FAILED,
  // Table 4. client internal error handling
  VVM3_VMG_DNS_FAILURE,
  VVM3_SPG_DNS_FAILURE,
  VVM3_VMG_CONNECTION_FAILED,
  VVM3_SPG_CONNECTION_FAILED,
  VVM3_VMG_TIMEOUT,
  VVM3_STATUS_SMS_TIMEOUT,

  VVM3_SUBSCRIBER_PROVISIONED,
  VVM3_SUBSCRIBER_BLOCKED,
  VVM3_SUBSCRIBER_UNKNOWN;

  public static class Type {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({CONFIGURATION, DATA_CHANNEL, NOTIFICATION_CHANNEL, OTHER})
    public @interface Values {}

    public static final int CONFIGURATION = 1;
    public static final int DATA_CHANNEL = 2;
    public static final int NOTIFICATION_CHANNEL = 3;
    public static final int OTHER = 4;
  }

  private final int mType;
  private final boolean mIsSuccess;

  OmtpEvents(int type, boolean isSuccess) {
    mType = type;
    mIsSuccess = isSuccess;
  }

  OmtpEvents(int type) {
    mType = type;
    mIsSuccess = false;
  }

  OmtpEvents() {
    mType = Type.OTHER;
    mIsSuccess = false;
  }

  @Type.Values
  public int getType() {
    return mType;
  }

  public boolean isSuccess() {
    return mIsSuccess;
  }
}
