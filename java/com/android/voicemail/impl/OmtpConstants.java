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

import android.support.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Wrapper class to hold relevant OMTP constants as defined in the OMTP spec.
 *
 * <p>In essence this is a programmatic representation of the relevant portions of OMTP spec.
 */
public class OmtpConstants {
  public static final String SMS_FIELD_SEPARATOR = ";";
  public static final String SMS_KEY_VALUE_SEPARATOR = "=";
  public static final String SMS_PREFIX_SEPARATOR = ":";

  public static final String SYNC_SMS_PREFIX = "SYNC";
  public static final String STATUS_SMS_PREFIX = "STATUS";

  // This is the format designated by the OMTP spec.
  public static final String DATE_TIME_FORMAT = "dd/MM/yyyy HH:mm Z";

  /** OMTP protocol versions. */
  public static final String PROTOCOL_VERSION1_1 = "11";

  public static final String PROTOCOL_VERSION1_2 = "12";
  public static final String PROTOCOL_VERSION1_3 = "13";

  ///////////////////////// Client/Mobile originated SMS //////////////////////

  /** Mobile Originated requests */
  public static final String ACTIVATE_REQUEST = "Activate";

  public static final String DEACTIVATE_REQUEST = "Deactivate";
  public static final String STATUS_REQUEST = "Status";

  /** fields that can be present in a Mobile Originated OMTP SMS */
  public static final String CLIENT_TYPE = "ct";

  public static final String APPLICATION_PORT = "pt";
  public static final String PROTOCOL_VERSION = "pv";

  //////////////////////////////// Sync SMS fields ////////////////////////////

  /**
   * Sync SMS fields.
   *
   * <p>Each string constant is the field's key in the SMS body which is used by the parser to
   * identify the field's value, if present, in the SMS body.
   */

  /** The event that triggered this SYNC SMS. See {@link OmtpConstants#SYNC_TRIGGER_EVENT_VALUES} */
  public static final String SYNC_TRIGGER_EVENT = "ev";

  public static final String MESSAGE_UID = "id";
  public static final String MESSAGE_LENGTH = "l";
  public static final String NUM_MESSAGE_COUNT = "c";
  /** See {@link OmtpConstants#CONTENT_TYPE_VALUES} */
  public static final String CONTENT_TYPE = "t";

  public static final String SENDER = "s";
  public static final String TIME = "dt";

  /**
   * SYNC message trigger events.
   *
   * <p>These are the possible values of {@link OmtpConstants#SYNC_TRIGGER_EVENT}.
   */
  public static final String NEW_MESSAGE = "NM";

  public static final String MAILBOX_UPDATE = "MBU";
  public static final String GREETINGS_UPDATE = "GU";

  public static final String[] SYNC_TRIGGER_EVENT_VALUES = {
    NEW_MESSAGE, MAILBOX_UPDATE, GREETINGS_UPDATE
  };

  /**
   * Content types supported by OMTP VVM.
   *
   * <p>These are the possible values of {@link OmtpConstants#CONTENT_TYPE}.
   */
  public static final String VOICE = "v";

  public static final String VIDEO = "o";
  public static final String FAX = "f";
  /** Voice message deposited by an external application */
  public static final String INFOTAINMENT = "i";
  /** Empty Call Capture - i.e. voicemail with no voice message. */
  public static final String ECC = "e";

  public static final String[] CONTENT_TYPE_VALUES = {VOICE, VIDEO, FAX, INFOTAINMENT, ECC};

  ////////////////////////////// Status SMS fields ////////////////////////////

  /**
   * Status SMS fields.
   *
   * <p>Each string constant is the field's key in the SMS body which is used by the parser to
   * identify the field's value, if present, in the SMS body.
   */
  /** See {@link OmtpConstants#PROVISIONING_STATUS_VALUES} */
  public static final String PROVISIONING_STATUS = "st";
  /** See {@link OmtpConstants#RETURN_CODE_VALUES} */
  public static final String RETURN_CODE = "rc";
  /** URL to send users to for activation VVM */
  public static final String SUBSCRIPTION_URL = "rs";
  /** IMAP4/SMTP server IP address or fully qualified domain name */
  public static final String SERVER_ADDRESS = "srv";
  /** Phone number to access voicemails through Telephony User Interface */
  public static final String TUI_ACCESS_NUMBER = "tui";

  public static final String TUI_PASSWORD_LENGTH = "pw_len";
  /** Number to send client origination SMS */
  public static final String CLIENT_SMS_DESTINATION_NUMBER = "dn";

  public static final String IMAP_PORT = "ipt";
  public static final String IMAP_USER_NAME = "u";
  public static final String IMAP_PASSWORD = "pw";
  public static final String SMTP_PORT = "spt";
  public static final String SMTP_USER_NAME = "smtp_u";
  public static final String SMTP_PASSWORD = "smtp_pw";

  /**
   * User provisioning status values.
   *
   * <p>Referred by {@link OmtpConstants#PROVISIONING_STATUS}.
   */
  public static final String SUBSCRIBER_NEW = "N";

  public static final String SUBSCRIBER_READY = "R";
  public static final String SUBSCRIBER_PROVISIONED = "P";
  public static final String SUBSCRIBER_UNKNOWN = "U";
  public static final String SUBSCRIBER_BLOCKED = "B";

  public static final String[] PROVISIONING_STATUS_VALUES = {
    SUBSCRIBER_NEW, SUBSCRIBER_READY, SUBSCRIBER_PROVISIONED, SUBSCRIBER_UNKNOWN, SUBSCRIBER_BLOCKED
  };

  /**
   * The return code included in a status message.
   *
   * <p>These are the possible values of {@link OmtpConstants#RETURN_CODE}.
   */
  public static final String SUCCESS = "0";

  public static final String SYSTEM_ERROR = "1";
  public static final String SUBSCRIBER_ERROR = "2";
  public static final String MAILBOX_UNKNOWN = "3";
  public static final String VVM_NOT_ACTIVATED = "4";
  public static final String VVM_NOT_PROVISIONED = "5";
  public static final String VVM_CLIENT_UKNOWN = "6";
  public static final String VVM_MAILBOX_NOT_INITIALIZED = "7";

  public static final String[] RETURN_CODE_VALUES = {
    SUCCESS,
    SYSTEM_ERROR,
    SUBSCRIBER_ERROR,
    MAILBOX_UNKNOWN,
    VVM_NOT_ACTIVATED,
    VVM_NOT_PROVISIONED,
    VVM_CLIENT_UKNOWN,
    VVM_MAILBOX_NOT_INITIALIZED,
  };

  /** IMAP command extensions */

  /**
   * OMTP spec v1.3 2.3.1 Change password request syntax
   *
   * <p>This changes the PIN to access the Telephone User Interface, the traditional voicemail
   * system.
   */
  public static final String IMAP_CHANGE_TUI_PWD_FORMAT = "XCHANGE_TUI_PWD PWD=%1$s OLD_PWD=%2$s";

  /**
   * OMTP spec v1.3 2.4.1 Change languate request syntax
   *
   * <p>This changes the language in the Telephone User Interface.
   */
  public static final String IMAP_CHANGE_VM_LANG_FORMAT = "XCHANGE_VM_LANG LANG=%1$s";

  /**
   * OMTP spec v1.3 2.5.1 Close NUT Request syntax
   *
   * <p>This disables the new user tutorial, the message played to new users calling in the
   * Telephone User Interface.
   */
  public static final String IMAP_CLOSE_NUT = "XCLOSE_NUT";

  /** Possible NO responses for CHANGE_TUI_PWD */
  public static final String RESPONSE_CHANGE_PIN_TOO_SHORT = "password too short";

  public static final String RESPONSE_CHANGE_PIN_TOO_LONG = "password too long";
  public static final String RESPONSE_CHANGE_PIN_TOO_WEAK = "password too weak";
  public static final String RESPONSE_CHANGE_PIN_MISMATCH = "old password mismatch";
  public static final String RESPONSE_CHANGE_PIN_INVALID_CHARACTER =
      "password contains invalid characters";

  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
    value = {
      CHANGE_PIN_SUCCESS,
      CHANGE_PIN_TOO_SHORT,
      CHANGE_PIN_TOO_LONG,
      CHANGE_PIN_TOO_WEAK,
      CHANGE_PIN_MISMATCH,
      CHANGE_PIN_INVALID_CHARACTER,
      CHANGE_PIN_SYSTEM_ERROR
    }
  )
  public @interface ChangePinResult {}

  public static final int CHANGE_PIN_SUCCESS = 0;
  public static final int CHANGE_PIN_TOO_SHORT = 1;
  public static final int CHANGE_PIN_TOO_LONG = 2;
  public static final int CHANGE_PIN_TOO_WEAK = 3;
  public static final int CHANGE_PIN_MISMATCH = 4;
  public static final int CHANGE_PIN_INVALID_CHARACTER = 5;
  public static final int CHANGE_PIN_SYSTEM_ERROR = 6;

  public static String getClientType() {
    String manufacturer =
        truncate(
            android.os.Build.MANUFACTURER
                .replace('=', '_')
                .replace(';', '_')
                .replace('.', '_')
                .replace(' ', '_'),
            12);

    String version =
        truncate(
            android.os.Build.VERSION
                .RELEASE
                .replace('=', '_')
                .replace(';', '_')
                .replace('.', '_')
                .replace(' ', '_'),
            8);

    String model =
        truncate(
            android.os.Build.MODEL
                .replace('=', '_')
                .replace(';', '_')
                .replace('.', '_')
                .replace(' ', '_'),
            28 - manufacturer.length() - version.length());

    return String.format("%s.%s.%s", manufacturer, model, version);
  }

  private static final String truncate(String string, int length) {
    return string.substring(0, Math.min(length, string.length()));
  }
}
