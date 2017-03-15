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

import android.util.ArraySet;
import android.util.Base64;
import com.android.voicemail.impl.OmtpEvents;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.mail.AuthenticationFailedException;
import com.android.voicemail.impl.mail.CertificateValidationException;
import com.android.voicemail.impl.mail.MailTransport;
import com.android.voicemail.impl.mail.MessagingException;
import com.android.voicemail.impl.mail.store.ImapStore.ImapException;
import com.android.voicemail.impl.mail.store.imap.DigestMd5Utils;
import com.android.voicemail.impl.mail.store.imap.ImapConstants;
import com.android.voicemail.impl.mail.store.imap.ImapResponse;
import com.android.voicemail.impl.mail.store.imap.ImapResponseParser;
import com.android.voicemail.impl.mail.store.imap.ImapUtility;
import com.android.voicemail.impl.mail.utils.LogUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLException;

/** A cacheable class that stores the details for a single IMAP connection. */
public class ImapConnection {
  private final String TAG = "ImapConnection";

  private String mLoginPhrase;
  private ImapStore mImapStore;
  private MailTransport mTransport;
  private ImapResponseParser mParser;
  private Set<String> mCapabilities = new ArraySet<>();

  static final String IMAP_REDACTED_LOG = "[IMAP command redacted]";

  /**
   * Next tag to use. All connections associated to the same ImapStore instance share the same
   * counter to make tests simpler. (Some of the tests involve multiple connections but only have a
   * single counter to track the tag.)
   */
  private final AtomicInteger mNextCommandTag = new AtomicInteger(0);

  ImapConnection(ImapStore store) {
    setStore(store);
  }

  void setStore(ImapStore store) {
    // TODO: maybe we should throw an exception if the connection is not closed here,
    // if it's not currently closed, then we won't reopen it, so if the credentials have
    // changed, the connection will not be reestablished.
    mImapStore = store;
    mLoginPhrase = null;
  }

  /**
   * Generates and returns the phrase to be used for authentication. This will be a LOGIN with
   * username and password.
   *
   * @return the login command string to sent to the IMAP server
   */
  String getLoginPhrase() {
    if (mLoginPhrase == null) {
      if (mImapStore.getUsername() != null && mImapStore.getPassword() != null) {
        // build the LOGIN string once (instead of over-and-over again.)
        // apply the quoting here around the built-up password
        mLoginPhrase =
            ImapConstants.LOGIN
                + " "
                + mImapStore.getUsername()
                + " "
                + ImapUtility.imapQuoted(mImapStore.getPassword());
      }
    }
    return mLoginPhrase;
  }

  public void open() throws IOException, MessagingException {
    if (mTransport != null && mTransport.isOpen()) {
      return;
    }

    try {
      // copy configuration into a clean transport, if necessary
      if (mTransport == null) {
        mTransport = mImapStore.cloneTransport();
      }

      mTransport.open();

      createParser();

      // The server should greet us with something like
      // * OK IMAP4rev1 Server
      // consume the response before doing anything else.
      ImapResponse response = mParser.readResponse(false);
      if (!response.isOk()) {
        mImapStore.getImapHelper().handleEvent(OmtpEvents.DATA_INVALID_INITIAL_SERVER_RESPONSE);
        throw new MessagingException(
            MessagingException.AUTHENTICATION_FAILED_OR_SERVER_ERROR,
            "Invalid server initial response");
      }

      queryCapability();

      maybeDoStartTls();

      // LOGIN
      doLogin();
    } catch (SSLException e) {
      LogUtils.d(TAG, "SSLException ", e);
      mImapStore.getImapHelper().handleEvent(OmtpEvents.DATA_SSL_EXCEPTION);
      throw new CertificateValidationException(e.getMessage(), e);
    } catch (IOException ioe) {
      LogUtils.d(TAG, "IOException", ioe);
      mImapStore.getImapHelper().handleEvent(OmtpEvents.DATA_IOE_ON_OPEN);
      throw ioe;
    } finally {
      destroyResponses();
    }
  }

  void logout() {
    try {
      sendCommand(ImapConstants.LOGOUT, false);
      if (!mParser.readResponse(true).is(0, ImapConstants.BYE)) {
        VvmLog.e(TAG, "Server did not respond LOGOUT with BYE");
      }
      if (!mParser.readResponse(false).isOk()) {
        VvmLog.e(TAG, "Server did not respond OK after LOGOUT");
      }
    } catch (IOException | MessagingException e) {
      VvmLog.e(TAG, "Error while logging out:" + e);
    }
  }

  /**
   * Closes the connection and releases all resources. This connection can not be used again until
   * {@link #setStore(ImapStore)} is called.
   */
  void close() {
    if (mTransport != null) {
      logout();
      mTransport.close();
      mTransport = null;
    }
    destroyResponses();
    mParser = null;
    mImapStore = null;
  }

  /** Attempts to convert the connection into secure connection. */
  private void maybeDoStartTls() throws IOException, MessagingException {
    // STARTTLS is required in the OMTP standard but not every implementation support it.
    // Make sure the server does have this capability
    if (hasCapability(ImapConstants.CAPABILITY_STARTTLS)) {
      executeSimpleCommand(ImapConstants.STARTTLS);
      mTransport.reopenTls();
      createParser();
      // The cached capabilities should be refreshed after TLS is established.
      queryCapability();
    }
  }

  /** Logs into the IMAP server */
  private void doLogin() throws IOException, MessagingException, AuthenticationFailedException {
    try {
      if (mCapabilities.contains(ImapConstants.CAPABILITY_AUTH_DIGEST_MD5)) {
        doDigestMd5Auth();
      } else {
        executeSimpleCommand(getLoginPhrase(), true);
      }
    } catch (ImapException ie) {
      LogUtils.d(TAG, "ImapException", ie);
      String status = ie.getStatus();
      String statusMessage = ie.getStatusMessage();
      String alertText = ie.getAlertText();

      if (ImapConstants.NO.equals(status)) {
        switch (statusMessage) {
          case ImapConstants.NO_UNKNOWN_USER:
            mImapStore.getImapHelper().handleEvent(OmtpEvents.DATA_AUTH_UNKNOWN_USER);
            break;
          case ImapConstants.NO_UNKNOWN_CLIENT:
            mImapStore.getImapHelper().handleEvent(OmtpEvents.DATA_AUTH_UNKNOWN_DEVICE);
            break;
          case ImapConstants.NO_INVALID_PASSWORD:
            mImapStore.getImapHelper().handleEvent(OmtpEvents.DATA_AUTH_INVALID_PASSWORD);
            break;
          case ImapConstants.NO_MAILBOX_NOT_INITIALIZED:
            mImapStore.getImapHelper().handleEvent(OmtpEvents.DATA_AUTH_MAILBOX_NOT_INITIALIZED);
            break;
          case ImapConstants.NO_SERVICE_IS_NOT_PROVISIONED:
            mImapStore.getImapHelper().handleEvent(OmtpEvents.DATA_AUTH_SERVICE_NOT_PROVISIONED);
            break;
          case ImapConstants.NO_SERVICE_IS_NOT_ACTIVATED:
            mImapStore.getImapHelper().handleEvent(OmtpEvents.DATA_AUTH_SERVICE_NOT_ACTIVATED);
            break;
          case ImapConstants.NO_USER_IS_BLOCKED:
            mImapStore.getImapHelper().handleEvent(OmtpEvents.DATA_AUTH_USER_IS_BLOCKED);
            break;
          case ImapConstants.NO_APPLICATION_ERROR:
            mImapStore.getImapHelper().handleEvent(OmtpEvents.DATA_REJECTED_SERVER_RESPONSE);
            break;
          default:
            mImapStore.getImapHelper().handleEvent(OmtpEvents.DATA_BAD_IMAP_CREDENTIAL);
        }
        throw new AuthenticationFailedException(alertText, ie);
      }

      mImapStore.getImapHelper().handleEvent(OmtpEvents.DATA_REJECTED_SERVER_RESPONSE);
      throw new MessagingException(alertText, ie);
    }
  }

  private void doDigestMd5Auth() throws IOException, MessagingException {

    //  Initiate the authentication.
    //  The server will issue us a challenge, asking to run MD5 on the nonce with our password
    //  and other data, including the cnonce we randomly generated.
    //
    //  C: a AUTHENTICATE DIGEST-MD5
    //  S: (BASE64) realm="elwood.innosoft.com",nonce="OA6MG9tEQGm2hh",qop="auth",
    //             algorithm=md5-sess,charset=utf-8
    List<ImapResponse> responses =
        executeSimpleCommand(ImapConstants.AUTHENTICATE + " " + ImapConstants.AUTH_DIGEST_MD5);
    String decodedChallenge = decodeBase64(responses.get(0).getStringOrEmpty(0).getString());

    Map<String, String> challenge = DigestMd5Utils.parseDigestMessage(decodedChallenge);
    DigestMd5Utils.Data data = new DigestMd5Utils.Data(mImapStore, mTransport, challenge);

    String response = data.createResponse();
    //  Respond to the challenge. If the server accepts it, it will reply a response-auth which
    //  is the MD5 of our password and the cnonce we've provided, to prove the server does know
    //  the password.
    //
    //  C: (BASE64) charset=utf-8,username="chris",realm="elwood.innosoft.com",
    //              nonce="OA6MG9tEQGm2hh",nc=00000001,cnonce="OA6MHXh6VqTrRk",
    //              digest-uri="imap/elwood.innosoft.com",
    //              response=d388dad90d4bbd760a152321f2143af7,qop=auth
    //  S: (BASE64) rspauth=ea40f60335c427b5527b84dbabcdfffd

    responses = executeContinuationResponse(encodeBase64(response), true);

    // Verify response-auth.
    // If failed verifyResponseAuth() will throw a MessagingException, terminating the
    // connection
    String decodedResponseAuth = decodeBase64(responses.get(0).getStringOrEmpty(0).getString());
    data.verifyResponseAuth(decodedResponseAuth);

    //  Send a empty response to indicate we've accepted the response-auth
    //
    //  C: (empty)
    //  S: a OK User logged in
    executeContinuationResponse("", false);
  }

  private static String decodeBase64(String string) {
    return new String(Base64.decode(string, Base64.DEFAULT));
  }

  private static String encodeBase64(String string) {
    return Base64.encodeToString(string.getBytes(), Base64.NO_WRAP);
  }

  private void queryCapability() throws IOException, MessagingException {
    List<ImapResponse> responses = executeSimpleCommand(ImapConstants.CAPABILITY);
    mCapabilities.clear();
    Set<String> disabledCapabilities =
        mImapStore.getImapHelper().getConfig().getDisabledCapabilities();
    for (ImapResponse response : responses) {
      if (response.isTagged()) {
        continue;
      }
      for (int i = 0; i < response.size(); i++) {
        String capability = response.getStringOrEmpty(i).getString();
        if (disabledCapabilities != null) {
          if (!disabledCapabilities.contains(capability)) {
            mCapabilities.add(capability);
          }
        } else {
          mCapabilities.add(capability);
        }
      }
    }

    LogUtils.d(TAG, "Capabilities: " + mCapabilities.toString());
  }

  private boolean hasCapability(String capability) {
    return mCapabilities.contains(capability);
  }
  /**
   * Create an {@link ImapResponseParser} from {@code mTransport.getInputStream()} and set it to
   * {@link #mParser}.
   *
   * <p>If we already have an {@link ImapResponseParser}, we {@link #destroyResponses()} and throw
   * it away.
   */
  private void createParser() {
    destroyResponses();
    mParser = new ImapResponseParser(mTransport.getInputStream());
  }

  public void destroyResponses() {
    if (mParser != null) {
      mParser.destroyResponses();
    }
  }

  public ImapResponse readResponse() throws IOException, MessagingException {
    return mParser.readResponse(false);
  }

  public List<ImapResponse> executeSimpleCommand(String command)
      throws IOException, MessagingException {
    return executeSimpleCommand(command, false);
  }

  /**
   * Send a single command to the server. The command will be preceded by an IMAP command tag and
   * followed by \r\n (caller need not supply them). Execute a simple command at the server, a
   * simple command being one that is sent in a single line of text
   *
   * @param command the command to send to the server
   * @param sensitive whether the command should be redacted in logs (used for login)
   * @return a list of ImapResponses
   * @throws IOException
   * @throws MessagingException
   */
  public List<ImapResponse> executeSimpleCommand(String command, boolean sensitive)
      throws IOException, MessagingException {
    // TODO: It may be nice to catch IOExceptions and close the connection here.
    // Currently, we expect callers to do that, but if they fail to we'll be in a broken state.
    sendCommand(command, sensitive);
    return getCommandResponses();
  }

  public String sendCommand(String command, boolean sensitive)
      throws IOException, MessagingException {
    open();

    if (mTransport == null) {
      throw new IOException("Null transport");
    }
    String tag = Integer.toString(mNextCommandTag.incrementAndGet());
    String commandToSend = tag + " " + command;
    mTransport.writeLine(commandToSend, (sensitive ? IMAP_REDACTED_LOG : command));
    return tag;
  }

  List<ImapResponse> executeContinuationResponse(String response, boolean sensitive)
      throws IOException, MessagingException {
    mTransport.writeLine(response, (sensitive ? IMAP_REDACTED_LOG : response));
    return getCommandResponses();
  }

  /**
   * Read and return all of the responses from the most recent command sent to the server
   *
   * @return a list of ImapResponses
   * @throws IOException
   * @throws MessagingException
   */
  List<ImapResponse> getCommandResponses() throws IOException, MessagingException {
    final List<ImapResponse> responses = new ArrayList<ImapResponse>();
    ImapResponse response;
    do {
      response = mParser.readResponse(false);
      responses.add(response);
    } while (!(response.isTagged() || response.isContinuationRequest()));

    if (!(response.isOk() || response.isContinuationRequest())) {
      final String toString = response.toString();
      final String status = response.getStatusOrEmpty().getString();
      final String statusMessage = response.getStatusResponseTextOrEmpty().getString();
      final String alert = response.getAlertTextOrEmpty().getString();
      final String responseCode = response.getResponseCodeOrEmpty().getString();
      destroyResponses();
      throw new ImapException(toString, status, statusMessage, alert, responseCode);
    }
    return responses;
  }
}
