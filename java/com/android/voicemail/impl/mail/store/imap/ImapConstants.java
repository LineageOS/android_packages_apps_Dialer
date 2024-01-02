/*
 * Copyright (C) 2015 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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
 * limitations under the License.
 */

package com.android.voicemail.impl.mail.store.imap;

import com.android.voicemail.impl.mail.store.ImapStore;

import java.util.Locale;

public final class ImapConstants {
  private ImapConstants() {}

  public static final String FETCH_FIELD_BODY_PEEK_BARE = "BODY.PEEK";
  public static final String FETCH_FIELD_BODY_PEEK = FETCH_FIELD_BODY_PEEK_BARE + "[]";
  public static final String FETCH_FIELD_BODY_PEEK_TRUNCATED =
      String.format(Locale.US, "BODY.PEEK[]<0.%d>", ImapStore.FETCH_BODY_TRUNCATED_SUGGESTED_SIZE);
  public static final String FETCH_FIELD_HEADERS =
      "BODY.PEEK[HEADER.FIELDS (date subject from content-type to cc message-id content-duration)]";

  public static final String ALERT = "ALERT";
  public static final String APPEND = "APPEND";
  public static final String AUTHENTICATE = "AUTHENTICATE";
  public static final String BAD = "BAD";
  public static final String BADCHARSET = "BADCHARSET";
  public static final String BODY = "BODY";
  public static final String BODY_BRACKET_HEADER = "BODY[HEADER";
  public static final String BODYSTRUCTURE = "BODYSTRUCTURE";
  public static final String BYE = "BYE";
  public static final String CAPABILITY = "CAPABILITY";
  public static final String CHECK = "CHECK";
  public static final String CLOSE = "CLOSE";
  public static final String COPY = "COPY";
  public static final String COPYUID = "COPYUID";
  public static final String CREATE = "CREATE";
  public static final String DELETE = "DELETE";
  public static final String EXAMINE = "EXAMINE";
  public static final String EXISTS = "EXISTS";
  public static final String EXPUNGE = "EXPUNGE";
  public static final String FETCH = "FETCH";
  public static final String FLAG_ANSWERED = "\\ANSWERED";
  public static final String FLAG_DELETED = "\\DELETED";
  public static final String FLAG_FLAGGED = "\\FLAGGED";
  public static final String FLAG_NO_SELECT = "\\NOSELECT";
  public static final String FLAG_SEEN = "\\SEEN";
  public static final String FLAGS = "FLAGS";
  public static final String FLAGS_SILENT = "FLAGS.SILENT";
  public static final String ID = "ID";
  public static final String INBOX = "INBOX";
  public static final String INTERNALDATE = "INTERNALDATE";
  public static final String LIST = "LIST";
  public static final String LOGIN = "LOGIN";
  public static final String LOGOUT = "LOGOUT";
  public static final String LSUB = "LSUB";
  public static final String NAMESPACE = "NAMESPACE";
  public static final String NO = "NO";
  public static final String NOOP = "NOOP";
  public static final String OK = "OK";
  public static final String PARSE = "PARSE";
  public static final String PERMANENTFLAGS = "PERMANENTFLAGS";
  public static final String PREAUTH = "PREAUTH";
  public static final String READ_ONLY = "READ-ONLY";
  public static final String READ_WRITE = "READ-WRITE";
  public static final String RENAME = "RENAME";
  public static final String RFC822_SIZE = "RFC822.SIZE";
  public static final String SEARCH = "SEARCH";
  public static final String SELECT = "SELECT";
  public static final String STARTTLS = "STARTTLS";
  public static final String STATUS = "STATUS";
  public static final String STORE = "STORE";
  public static final String SUBSCRIBE = "SUBSCRIBE";
  public static final String TEXT = "TEXT";
  public static final String TRYCREATE = "TRYCREATE";
  public static final String UID = "UID";
  public static final String UID_COPY = "UID COPY";
  public static final String UID_FETCH = "UID FETCH";
  public static final String UID_SEARCH = "UID SEARCH";
  public static final String UID_STORE = "UID STORE";
  public static final String UIDNEXT = "UIDNEXT";
  public static final String UIDPLUS = "UIDPLUS";
  public static final String UIDVALIDITY = "UIDVALIDITY";
  public static final String UNSEEN = "UNSEEN";
  public static final String UNSUBSCRIBE = "UNSUBSCRIBE";
  public static final String XOAUTH2 = "XOAUTH2";
  public static final String APPENDUID = "APPENDUID";
  public static final String NIL = "NIL";

  /** NO responses */
  public static final String NO_COMMAND_NOT_ALLOWED = "command not allowed";

  public static final String NO_RESERVATION_FAILED = "reservation failed";
  public static final String NO_APPLICATION_ERROR = "application error";
  public static final String NO_INVALID_PARAMETER = "invalid parameter";
  public static final String NO_INVALID_COMMAND = "invalid command";
  public static final String NO_UNKNOWN_COMMAND = "unknown command";
  // AUTHENTICATE
  // The subscriber can not be located in the system.
  public static final String NO_UNKNOWN_USER = "unknown user";
  // The Client Type or Protocol Version is unknown.
  public static final String NO_UNKNOWN_CLIENT = "unknown client";
  // The password received from the client does not match the password defined in the subscriber's
  // profile.
  public static final String NO_INVALID_PASSWORD = "invalid password";
  // The subscriber's mailbox has not yet been initialised via the TUI
  public static final String NO_MAILBOX_NOT_INITIALIZED = "mailbox not initialized";
  // The subscriber has not been provisioned for the VVM service.
  public static final String NO_SERVICE_IS_NOT_PROVISIONED = "service is not provisioned";
  // The subscriber is provisioned for the VVM service but the VVM service is currently not active
  public static final String NO_SERVICE_IS_NOT_ACTIVATED = "service is not activated";
  // The Voice Mail Blocked flag in the subscriber's profile is set to YES.
  public static final String NO_USER_IS_BLOCKED = "user is blocked";

  /** extensions */
  public static final String GETQUOTA = "GETQUOTA";

  public static final String GETQUOTAROOT = "GETQUOTAROOT";
  public static final String QUOTAROOT = "QUOTAROOT";
  public static final String QUOTA = "QUOTA";

  /** capabilities */
  public static final String CAPABILITY_AUTH_DIGEST_MD5 = "AUTH=DIGEST-MD5";

  public static final String CAPABILITY_STARTTLS = "STARTTLS";

  /** authentication */
  public static final String AUTH_DIGEST_MD5 = "DIGEST-MD5";
}
