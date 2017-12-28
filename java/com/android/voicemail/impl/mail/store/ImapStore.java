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
 * limitations under the License.
 */

package com.android.voicemail.impl.mail.store;

import android.content.Context;
import android.net.Network;
import com.android.voicemail.impl.imap.ImapHelper;
import com.android.voicemail.impl.mail.MailTransport;
import com.android.voicemail.impl.mail.Message;
import com.android.voicemail.impl.mail.MessagingException;
import com.android.voicemail.impl.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStream;
import org.apache.james.mime4j.MimeException;

public class ImapStore {
  /**
   * A global suggestion to Store implementors on how much of the body should be returned on
   * FetchProfile.Item.BODY_SANE requests. We'll use 125k now.
   */
  public static final int FETCH_BODY_SANE_SUGGESTED_SIZE = (125 * 1024);

  private final Context context;
  private final ImapHelper helper;
  private final String username;
  private final String password;
  private final MailTransport transport;
  private ImapConnection connection;

  public static final int FLAG_NONE = 0x00; // No flags
  public static final int FLAG_SSL = 0x01; // Use SSL
  public static final int FLAG_TLS = 0x02; // Use TLS
  public static final int FLAG_AUTHENTICATE = 0x04; // Use name/password for authentication
  public static final int FLAG_TRUST_ALL = 0x08; // Trust all certificates
  public static final int FLAG_OAUTH = 0x10; // Use OAuth for authentication

  /** Contains all the information necessary to log into an imap server */
  public ImapStore(
      Context context,
      ImapHelper helper,
      String username,
      String password,
      int port,
      String serverName,
      int flags,
      Network network) {
    this.context = context;
    this.helper = helper;
    this.username = username;
    this.password = password;
    transport = new MailTransport(context, this.getImapHelper(), network, serverName, port, flags);
  }

  public Context getContext() {
    return context;
  }

  public ImapHelper getImapHelper() {
    return helper;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  /** Returns a clone of the transport associated with this store. */
  MailTransport cloneTransport() {
    return transport.clone();
  }

  /** Returns UIDs of Messages joined with "," as the separator. */
  static String joinMessageUids(Message[] messages) {
    StringBuilder sb = new StringBuilder();
    boolean notFirst = false;
    for (Message m : messages) {
      if (notFirst) {
        sb.append(',');
      }
      sb.append(m.getUid());
      notFirst = true;
    }
    return sb.toString();
  }

  static class ImapMessage extends MimeMessage {
    private ImapFolder folder;

    ImapMessage(String uid, ImapFolder folder) {
      this.uid = uid;
      this.folder = folder;
    }

    public void setSize(int size) {
      this.size = size;
    }

    @Override
    public void parse(InputStream in) throws IOException, MessagingException, MimeException {
      super.parse(in);
    }

    public void setFlagInternal(String flag, boolean set) throws MessagingException {
      super.setFlag(flag, set);
    }

    @Override
    public void setFlag(String flag, boolean set) throws MessagingException {
      super.setFlag(flag, set);
      folder.setFlags(new Message[] {this}, new String[] {flag}, set);
    }
  }

  static class ImapException extends MessagingException {
    private static final long serialVersionUID = 1L;

    private final String status;
    private final String statusMessage;
    private final String alertText;
    private final String responseCode;

    public ImapException(
        String message,
        String status,
        String statusMessage,
        String alertText,
        String responseCode) {
      super(message);
      this.status = status;
      this.statusMessage = statusMessage;
      this.alertText = alertText;
      this.responseCode = responseCode;
    }

    public String getStatus() {
      return status;
    }

    public String getStatusMessage() {
      return statusMessage;
    }

    public String getAlertText() {
      return alertText;
    }

    public String getResponseCode() {
      return responseCode;
    }
  }

  public void closeConnection() {
    if (connection != null) {
      connection.close();
      connection = null;
    }
  }

  public ImapConnection getConnection() {
    if (connection == null) {
      connection = new ImapConnection(this);
    }
    return connection;
  }
}
