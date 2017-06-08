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
package com.android.voicemail.impl.mail;

import android.content.Context;
import android.net.Network;
import android.net.TrafficStats;
import android.support.annotation.VisibleForTesting;
import com.android.dialer.constants.TrafficStatsTags;
import com.android.voicemail.impl.OmtpEvents;
import com.android.voicemail.impl.imap.ImapHelper;
import com.android.voicemail.impl.mail.store.ImapStore;
import com.android.voicemail.impl.mail.utils.LogUtils;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

/** Make connection and perform operations on mail server by reading and writing lines. */
public class MailTransport {
  private static final String TAG = "MailTransport";

  // TODO protected eventually
  /*protected*/ public static final int SOCKET_CONNECT_TIMEOUT = 10000;
  /*protected*/ public static final int SOCKET_READ_TIMEOUT = 60000;

  private static final HostnameVerifier HOSTNAME_VERIFIER =
      HttpsURLConnection.getDefaultHostnameVerifier();

  private final Context mContext;
  private final ImapHelper mImapHelper;
  private final Network mNetwork;
  private final String mHost;
  private final int mPort;
  private Socket mSocket;
  private BufferedInputStream mIn;
  private BufferedOutputStream mOut;
  private final int mFlags;
  private SocketCreator mSocketCreator;
  private InetSocketAddress mAddress;

  public MailTransport(
      Context context,
      ImapHelper imapHelper,
      Network network,
      String address,
      int port,
      int flags) {
    mContext = context;
    mImapHelper = imapHelper;
    mNetwork = network;
    mHost = address;
    mPort = port;
    mFlags = flags;
  }

  /**
   * Returns a new transport, using the current transport as a model. The new transport is
   * configured identically, but not opened or connected in any way.
   */
  @Override
  public MailTransport clone() {
    return new MailTransport(mContext, mImapHelper, mNetwork, mHost, mPort, mFlags);
  }

  public boolean canTrySslSecurity() {
    return (mFlags & ImapStore.FLAG_SSL) != 0;
  }

  public boolean canTrustAllCertificates() {
    return (mFlags & ImapStore.FLAG_TRUST_ALL) != 0;
  }

  /**
   * Attempts to open a connection using the Uri supplied for connection parameters. Will attempt an
   * SSL connection if indicated.
   */
  public void open() throws MessagingException {
    LogUtils.d(TAG, "*** IMAP open " + mHost + ":" + String.valueOf(mPort));

    List<InetSocketAddress> socketAddresses = new ArrayList<InetSocketAddress>();

    if (mNetwork == null) {
      socketAddresses.add(new InetSocketAddress(mHost, mPort));
    } else {
      try {
        InetAddress[] inetAddresses = mNetwork.getAllByName(mHost);
        if (inetAddresses.length == 0) {
          throw new MessagingException(
              MessagingException.IOERROR,
              "Host name " + mHost + "cannot be resolved on designated network");
        }
        for (int i = 0; i < inetAddresses.length; i++) {
          socketAddresses.add(new InetSocketAddress(inetAddresses[i], mPort));
        }
      } catch (IOException ioe) {
        LogUtils.d(TAG, ioe.toString());
        mImapHelper.handleEvent(OmtpEvents.DATA_CANNOT_RESOLVE_HOST_ON_NETWORK);
        throw new MessagingException(MessagingException.IOERROR, ioe.toString());
      }
    }

    boolean success = false;
    while (socketAddresses.size() > 0) {
      mSocket = createSocket();
      try {
        mAddress = socketAddresses.remove(0);
        mSocket.connect(mAddress, SOCKET_CONNECT_TIMEOUT);

        if (canTrySslSecurity()) {
          /*
          SSLSocket cannot be created with a connection timeout, so instead of doing a
          direct SSL connection, we connect with a normal connection and upgrade it into
          SSL
           */
          reopenTls();
        } else {
          mIn = new BufferedInputStream(mSocket.getInputStream(), 1024);
          mOut = new BufferedOutputStream(mSocket.getOutputStream(), 512);
          mSocket.setSoTimeout(SOCKET_READ_TIMEOUT);
        }
        success = true;
        return;
      } catch (IOException ioe) {
        LogUtils.d(TAG, ioe.toString());
        if (socketAddresses.size() == 0) {
          // Only throw an error when there are no more sockets to try.
          mImapHelper.handleEvent(OmtpEvents.DATA_ALL_SOCKET_CONNECTION_FAILED);
          throw new MessagingException(MessagingException.IOERROR, ioe.toString());
        }
      } finally {
        if (!success) {
          try {
            mSocket.close();
            mSocket = null;
          } catch (IOException ioe) {
            throw new MessagingException(MessagingException.IOERROR, ioe.toString());
          }
        }
      }
    }
  }

  // For testing. We need something that can replace the behavior of "new Socket()"
  @VisibleForTesting
  interface SocketCreator {

    Socket createSocket() throws MessagingException;
  }

  @VisibleForTesting
  void setSocketCreator(SocketCreator creator) {
    mSocketCreator = creator;
  }

  protected Socket createSocket() throws MessagingException {
    if (mSocketCreator != null) {
      return mSocketCreator.createSocket();
    }

    if (mNetwork == null) {
      LogUtils.v(TAG, "createSocket: network not specified");
      return new Socket();
    }

    try {
      LogUtils.v(TAG, "createSocket: network specified");
      TrafficStats.setThreadStatsTag(TrafficStatsTags.VISUAL_VOICEMAIL_TAG);
      return mNetwork.getSocketFactory().createSocket();
    } catch (IOException ioe) {
      LogUtils.d(TAG, ioe.toString());
      throw new MessagingException(MessagingException.IOERROR, ioe.toString());
    }
  }

  /** Attempts to reopen a normal connection into a TLS connection. */
  public void reopenTls() throws MessagingException {
    try {
      LogUtils.d(TAG, "open: converting to TLS socket");
      mSocket =
          HttpsURLConnection.getDefaultSSLSocketFactory()
              .createSocket(mSocket, mAddress.getHostName(), mAddress.getPort(), true);
      // After the socket connects to an SSL server, confirm that the hostname is as
      // expected
      if (!canTrustAllCertificates()) {
        verifyHostname(mSocket, mHost);
      }
      mSocket.setSoTimeout(SOCKET_READ_TIMEOUT);
      mIn = new BufferedInputStream(mSocket.getInputStream(), 1024);
      mOut = new BufferedOutputStream(mSocket.getOutputStream(), 512);

    } catch (SSLException e) {
      LogUtils.d(TAG, e.toString());
      throw new CertificateValidationException(e.getMessage(), e);
    } catch (IOException ioe) {
      LogUtils.d(TAG, ioe.toString());
      throw new MessagingException(MessagingException.IOERROR, ioe.toString());
    }
  }

  /**
   * Lightweight version of SSLCertificateSocketFactory.verifyHostname, which provides this service
   * but is not in the public API.
   *
   * <p>Verify the hostname of the certificate used by the other end of a connected socket. It is
   * harmless to call this method redundantly if the hostname has already been verified.
   *
   * <p>Wildcard certificates are allowed to verify any matching hostname, so "foo.bar.example.com"
   * is verified if the peer has a certificate for "*.example.com".
   *
   * @param socket An SSL socket which has been connected to a server
   * @param hostname The expected hostname of the remote server
   * @throws IOException if something goes wrong handshaking with the server
   * @throws SSLPeerUnverifiedException if the server cannot prove its identity
   */
  private void verifyHostname(Socket socket, String hostname) throws IOException {
    // The code at the start of OpenSSLSocketImpl.startHandshake()
    // ensures that the call is idempotent, so we can safely call it.
    SSLSocket ssl = (SSLSocket) socket;
    ssl.startHandshake();

    SSLSession session = ssl.getSession();
    if (session == null) {
      mImapHelper.handleEvent(OmtpEvents.DATA_CANNOT_ESTABLISH_SSL_SESSION);
      throw new SSLException("Cannot verify SSL socket without session");
    }
    // TODO: Instead of reporting the name of the server we think we're connecting to,
    // we should be reporting the bad name in the certificate.  Unfortunately this is buried
    // in the verifier code and is not available in the verifier API, and extracting the
    // CN & alts is beyond the scope of this patch.
    if (!HOSTNAME_VERIFIER.verify(hostname, session)) {
      mImapHelper.handleEvent(OmtpEvents.DATA_SSL_INVALID_HOST_NAME);
      throw new SSLPeerUnverifiedException(
          "Certificate hostname not useable for server: " + session.getPeerPrincipal());
    }
  }

  public boolean isOpen() {
    return (mIn != null
        && mOut != null
        && mSocket != null
        && mSocket.isConnected()
        && !mSocket.isClosed());
  }

  /** Close the connection. MUST NOT return any exceptions - must be "best effort" and safe. */
  public void close() {
    try {
      mIn.close();
    } catch (Exception e) {
      // May fail if the connection is already closed.
    }
    try {
      mOut.close();
    } catch (Exception e) {
      // May fail if the connection is already closed.
    }
    try {
      mSocket.close();
    } catch (Exception e) {
      // May fail if the connection is already closed.
    }
    mIn = null;
    mOut = null;
    mSocket = null;
  }

  public String getHost() {
    return mHost;
  }

  public InputStream getInputStream() {
    return mIn;
  }

  public OutputStream getOutputStream() {
    return mOut;
  }

  /** Writes a single line to the server using \r\n termination. */
  public void writeLine(String s, String sensitiveReplacement) throws IOException {
    if (sensitiveReplacement != null) {
      LogUtils.d(TAG, ">>> " + sensitiveReplacement);
    } else {
      LogUtils.d(TAG, ">>> " + s);
    }

    OutputStream out = getOutputStream();
    out.write(s.getBytes());
    out.write('\r');
    out.write('\n');
    out.flush();
  }

  /**
   * Reads a single line from the server, using either \r\n or \n as the delimiter. The delimiter
   * char(s) are not included in the result.
   */
  public String readLine(boolean loggable) throws IOException {
    StringBuffer sb = new StringBuffer();
    InputStream in = getInputStream();
    int d;
    while ((d = in.read()) != -1) {
      if (((char) d) == '\r') {
        continue;
      } else if (((char) d) == '\n') {
        break;
      } else {
        sb.append((char) d);
      }
    }
    if (d == -1) {
      LogUtils.d(TAG, "End of stream reached while trying to read line.");
    }
    String ret = sb.toString();
    if (loggable) {
      LogUtils.d(TAG, "<<< " + ret);
    }
    return ret;
  }
}
